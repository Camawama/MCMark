package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.ArenaBuilder;
import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.world.entity.EntityType;

/**
 * Unscored JIT/cache warmup: runs a small entity workload so hot paths (entity ticking,
 * pathfinding, collision) are compiled before anything is measured.
 */
public class WarmupPhase extends BenchmarkPhase {
    private int spawned;
    private int toSpawn;

    public WarmupPhase() {
        super("warmup", "Warmup");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        toSpawn = ctx.count(BenchmarkSpec.WARMUP_HUSKS) + ctx.count(BenchmarkSpec.WARMUP_COWS);
    }

    @Override
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        int husks = ctx.count(BenchmarkSpec.WARMUP_HUSKS);
        for (int i = 0; i < BenchmarkSpec.ENTITY_SPAWNS_PER_TICK && spawned < toSpawn; i++, spawned++) {
            ArenaBuilder.spawnMob(ctx.level, spawned < husks ? EntityType.HUSK : EntityType.COW, ctx.random);
        }
        return spawned >= toSpawn;
    }

    @Override
    protected int settleTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.WARMUP_TICKS);
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return 1;
    }

    @Override
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        ArenaBuilder.discardNonPlayers(ctx.level);
        return true;
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        PhaseResult r = new PhaseResult(id(), display());
        r.scored = false;
        return r;
    }

    @Override
    public void abort(BenchmarkContext ctx) {
        ArenaBuilder.discardNonPlayers(ctx.level);
    }
}
