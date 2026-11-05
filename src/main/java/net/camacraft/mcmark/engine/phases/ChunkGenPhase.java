package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.McmarkSavedData;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.metrics.SampleSeries;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.camacraft.mcmark.spec.StandardFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures synchronous full generation of a square of spec-defined chunks. With the official
 * world seed, every run generates byte-identical terrain. Each world region is single-use
 * (tracked in saved data); reruns in the same world fall back to offset regions and are flagged.
 */
public class ChunkGenPhase extends BenchmarkPhase {
    private final SampleSeries chunkTimes = new SampleSeries();
    private List<ChunkPos> pending = new ArrayList<>();
    private int index;

    public ChunkGenPhase() {
        super("chunk_gen", "Chunk Gen");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        int region = McmarkSavedData.get(ctx.level).consumeChunkgenRegion();
        if (region > 0) ctx.addFlag(StandardFlag.CHUNKGEN_REGION_REUSED);
        int ccx = BenchmarkSpec.CHUNKGEN_CENTER_CX + region * BenchmarkSpec.CHUNKGEN_REGION_STRIDE;
        int ccz = BenchmarkSpec.CHUNKGEN_CENTER_CZ;
        ctx.setChunkgenCenter(ccx, ccz);
        pending = new ArrayList<>();
        int r = BenchmarkSpec.CHUNKGEN_RADIUS;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                pending.add(new ChunkPos(ccx + dx, ccz + dz));
            }
        }
        index = 0;
    }

    @Override
    protected void measureTick(BenchmarkContext ctx, int tick) {
        for (int i = 0; i < BenchmarkSpec.CHUNKGEN_PER_TICK && index < pending.size(); i++) {
            ChunkPos pos = pending.get(index++);
            long start = System.nanoTime();
            ctx.level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            chunkTimes.add(System.nanoTime() - start);
        }
    }

    @Override
    protected boolean measureComplete(BenchmarkContext ctx, int ticksDone) {
        return index >= pending.size();
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return Math.max(1, pending.size() / BenchmarkSpec.CHUNKGEN_PER_TICK);
    }

    @Override
    protected double measureProgress(BenchmarkContext ctx, int ticksDone) {
        return pending.isEmpty() ? 1.0 : index / (double) pending.size();
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        PhaseResult r = new PhaseResult(id(), display());
        double totalSeconds = chunkTimes.sum() / 1.0e9;
        r.metricName = "generation_rate";
        r.unit = "chunks/s";
        r.lowerIsBetter = false;
        r.primary = totalSeconds > 0 ? chunkTimes.count() / totalSeconds : 0;
        r.stats.put("chunks", (double) chunkTimes.count());
        r.stats.put("total_s", totalSeconds);
        r.stats.put("median_chunk_ms", chunkTimes.medianMs());
        r.stats.put("p95_chunk_ms", chunkTimes.percentileMs(95));
        r.stats.put("max_chunk_ms", chunkTimes.maxMs());
        return r;
    }
}
