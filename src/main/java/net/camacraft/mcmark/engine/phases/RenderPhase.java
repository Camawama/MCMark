package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.metrics.FrameStats;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.camacraft.mcmark.spec.StandardFlag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Client render benchmark: the player (already in spectator) is teleported to fixed vantage
 * points over the spec-generated terrain; the client sweeps the camera 360° per segment while
 * recording frame times. Standard video settings are applied temporarily (config permitting)
 * so scores are comparable across users.
 */
public class RenderPhase extends BenchmarkPhase {
    private enum SegState { TELEPORT, SETTLE, RUNNING, ADVANCE }

    private final List<FrameStats> segments = new ArrayList<>();
    private SegState segState = SegState.TELEPORT;
    private int segIndex;
    private int waitTicks;
    private int timeoutTicks;
    private boolean skipped;
    private boolean applied;
    private List<ChunkPos> pregenQueue;
    private int pregenIndex;

    public RenderPhase() {
        super("render", "Render (FPS)");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        skipped = !ctx.client.hasClient() || ctx.player() == null;
        if (skipped) return;
        applied = ctx.client.applyStandardVideoSettings();
        if (!applied && !ctx.client.videoSettingsDeviations().isEmpty()) {
            ctx.addFlag(StandardFlag.NON_STANDARD_VIDEO_SETTINGS);
        }
        segIndex = 0;
        segState = SegState.TELEPORT;
        segments.clear();
        // Everything within the client's view distance must already exist before measurement,
        // otherwise chunk generation/streaming during the sweep adds machine-dependent noise.
        pregenQueue = new ArrayList<>();
        int r = BenchmarkSpec.RENDER_PREGEN_RADIUS;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                pregenQueue.add(new ChunkPos(ctx.chunkgenCenterCx() + dx, ctx.chunkgenCenterCz() + dz));
            }
        }
        pregenIndex = 0;
    }

    @Override
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        if (skipped) return true;
        for (int i = 0; i < BenchmarkSpec.RENDER_PREGEN_PER_TICK && pregenIndex < pregenQueue.size(); i++) {
            ChunkPos pos = pregenQueue.get(pregenIndex++);
            ctx.level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
        }
        return pregenIndex >= pregenQueue.size();
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        // Nominal length for progress display; actual completion is segment-driven.
        return skipped ? 1
                : BenchmarkSpec.RENDER_SEGMENTS * (BenchmarkSpec.RENDER_SETTLE_TICKS
                + (int) (BenchmarkSpec.RENDER_SEGMENT_SECONDS * 20));
    }

    @Override
    protected boolean measureComplete(BenchmarkContext ctx, int ticksDone) {
        return skipped || segIndex >= BenchmarkSpec.RENDER_SEGMENTS;
    }

    @Override
    protected void measureTick(BenchmarkContext ctx, int tick) {
        if (skipped || segIndex >= BenchmarkSpec.RENDER_SEGMENTS) return;
        ServerPlayer player = ctx.player();
        if (player == null) {
            // Player left mid-run; salvage what we have.
            ctx.addFlag(StandardFlag.RENDER_INTERRUPTED);
            segIndex = BenchmarkSpec.RENDER_SEGMENTS;
            return;
        }
        switch (segState) {
            case TELEPORT -> {
                teleportToVantage(ctx, player, segIndex);
                waitTicks = 0;
                segState = SegState.SETTLE;
            }
            case SETTLE -> {
                waitTicks++;
                if (waitTicks >= BenchmarkSpec.RENDER_SETTLE_TICKS) {
                    ctx.client.beginRenderSegment(BenchmarkSpec.RENDER_SEGMENT_SECONDS,
                            pitchFor(segIndex), 0f);
                    timeoutTicks = 0;
                    segState = SegState.RUNNING;
                }
            }
            case RUNNING -> {
                if (ctx.client.isRenderSegmentDone()) {
                    FrameStats stats = ctx.client.takeRenderStats();
                    if (stats != null) {
                        segments.add(stats);
                        if (stats.interference) ctx.addFlag(StandardFlag.RENDER_INTERRUPTED);
                    }
                    segState = SegState.ADVANCE;
                } else if (++timeoutTicks > BenchmarkSpec.RENDER_TIMEOUT_TICKS) {
                    ctx.addFlag(StandardFlag.RENDER_TIMEOUT);
                    segState = SegState.ADVANCE;
                }
            }
            case ADVANCE -> {
                segIndex++;
                segState = SegState.TELEPORT;
            }
        }
    }

    private void teleportToVantage(BenchmarkContext ctx, ServerPlayer player, int segment) {
        int x = ctx.chunkgenCenterCx() * 16 + 8;
        int z = ctx.chunkgenCenterCz() * 16 + 8;
        int surface = ctx.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int y = segment == 0 ? surface + 35 : surface + 90;
        player.teleportTo(ctx.level, x + 0.5, y, z + 0.5, 0f, pitchFor(segment));
    }

    private float pitchFor(int segment) {
        return segment == 0 ? 25f : 55f;
    }

    @Override
    protected double measureProgress(BenchmarkContext ctx, int ticksDone) {
        return skipped ? 1.0 : Math.min(1.0, segIndex / (double) BenchmarkSpec.RENDER_SEGMENTS);
    }

    @Override
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        if (applied) ctx.client.restoreVideoSettings();
        applied = false;
        return true;
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        PhaseResult r = new PhaseResult(id(), display());
        if (skipped || segments.isEmpty()) {
            r.scored = false;
            return r;
        }
        double totalSeconds = 0;
        double blendSum = 0;
        double avgSum = 0;
        double lowSum = 0;
        for (FrameStats s : segments) {
            totalSeconds += s.seconds;
            avgSum += s.avgFps;
            lowSum += s.onePercentLowFps;
            blendSum += BenchmarkSpec.RENDER_AVG_WEIGHT * s.avgFps
                    + BenchmarkSpec.RENDER_LOW_WEIGHT * s.onePercentLowFps;
        }
        int n = segments.size();
        r.metricName = "fps_blend";
        r.unit = "fps";
        r.lowerIsBetter = false;
        r.primary = blendSum / n;
        r.stats.put("avg_fps", avgSum / n);
        r.stats.put("one_percent_low_fps", lowSum / n);
        r.stats.put("segments", (double) n);
        r.stats.put("total_s", totalSeconds);
        return r;
    }

    @Override
    public void abort(BenchmarkContext ctx) {
        if (applied) ctx.client.restoreVideoSettings();
        applied = false;
    }
}
