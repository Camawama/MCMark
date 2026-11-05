package net.camacraft.mcmark.engine;

import net.camacraft.mcmark.metrics.SampleSeries;

/**
 * Base class for benchmark phases. Each phase moves through SETUP → SETTLE → MEASURE → CLEANUP,
 * driven once per server tick (at tick end, with that tick's measured duration). Tick durations
 * are only recorded during MEASURE, so setup/cleanup work never contaminates results.
 */
public abstract class BenchmarkPhase {
    public enum Stage { SETUP, SETTLE, MEASURE, CLEANUP, DONE }

    private final String id;
    private final String display;
    protected final SampleSeries tickSamples = new SampleSeries();
    private Stage stage = Stage.SETUP;
    private int stageTick = 0;

    protected BenchmarkPhase(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public final String id() {
        return id;
    }

    public final String display() {
        return display;
    }

    public void begin(BenchmarkContext ctx) {}

    /** Driven by the engine at the end of each server tick. Returns true when the phase is done. */
    public final boolean tickEnd(BenchmarkContext ctx, long tickNanos) {
        switch (stage) {
            case SETUP -> {
                if (setupTick(ctx, stageTick)) {
                    stage = Stage.SETTLE;
                    stageTick = 0;
                } else {
                    stageTick++;
                }
            }
            case SETTLE -> {
                stageTick++;
                if (stageTick >= settleTicks(ctx)) {
                    stage = Stage.MEASURE;
                    stageTick = 0;
                    onMeasureStart(ctx);
                }
            }
            case MEASURE -> {
                tickSamples.add(tickNanos);
                measureTick(ctx, stageTick);
                stageTick++;
                if (measureComplete(ctx, stageTick)) {
                    stage = Stage.CLEANUP;
                    stageTick = 0;
                }
            }
            case CLEANUP -> {
                if (cleanupTick(ctx, stageTick)) {
                    stage = Stage.DONE;
                } else {
                    stageTick++;
                }
            }
            case DONE -> {}
        }
        return stage == Stage.DONE;
    }

    /** Rough progress in [0,1] for the HUD. */
    public double progressFraction(BenchmarkContext ctx) {
        return switch (stage) {
            case SETUP -> 0.05;
            case SETTLE -> 0.05 + 0.10 * Math.min(1.0, stageTick / (double) Math.max(1, settleTicks(ctx)));
            case MEASURE -> 0.15 + 0.80 * measureProgress(ctx, stageTick);
            case CLEANUP, DONE -> 1.0;
        };
    }

    protected double measureProgress(BenchmarkContext ctx, int ticksDone) {
        return Math.min(1.0, ticksDone / (double) Math.max(1, measureTicks(ctx)));
    }

    /** Returns true when setup is finished. Called once per tick with an incrementing counter. */
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        return true;
    }

    protected int settleTicks(BenchmarkContext ctx) {
        return 0;
    }

    protected void onMeasureStart(BenchmarkContext ctx) {}

    /** Per-tick workload during measurement (e.g. scheduled item waves). */
    protected void measureTick(BenchmarkContext ctx, int tick) {}

    protected boolean measureComplete(BenchmarkContext ctx, int ticksDone) {
        return ticksDone >= measureTicks(ctx);
    }

    protected abstract int measureTicks(BenchmarkContext ctx);

    /** Returns true when cleanup is finished. Called once per tick with an incrementing counter. */
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        return true;
    }

    /** Builds this phase's result after DONE. */
    public abstract PhaseResult result(BenchmarkContext ctx);

    /** Emergency cleanup on abort; must be safe to call in any stage. */
    public void abort(BenchmarkContext ctx) {}

    /** Convenience: standard MSPT result from the recorded tick samples. */
    protected PhaseResult msptResult() {
        PhaseResult r = new PhaseResult(id(), display());
        r.metricName = "median_mspt";
        r.unit = "ms";
        r.lowerIsBetter = true;
        r.primary = tickSamples.medianMs();
        r.stats.put("mean_ms", tickSamples.meanMs());
        r.stats.put("p95_ms", tickSamples.percentileMs(95));
        r.stats.put("p99_ms", tickSamples.percentileMs(99));
        r.stats.put("max_ms", tickSamples.maxMs());
        r.stats.put("std_dev_ms", tickSamples.stdDevMs());
        r.stats.put("ticks", (double) tickSamples.count());
        return r;
    }
}
