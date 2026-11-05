package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.spec.BenchmarkSpec;

/**
 * Baseline tick cost of the empty (but arena-loaded) world. Captures the fixed overhead every
 * mod adds to a tick even when "nothing" is happening — often the most player-relevant number.
 */
public class IdlePhase extends BenchmarkPhase {

    public IdlePhase() {
        super("idle", "Idle Tick");
    }

    @Override
    protected int settleTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.IDLE_SETTLE_TICKS);
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.IDLE_MEASURE_TICKS);
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        return msptResult();
    }
}
