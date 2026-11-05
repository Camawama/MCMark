package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.ArenaBuilder;
import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity/AI stress: a fixed population of husks (hostile pathfinding, sunlight-immune),
 * villagers (brain AI, panic behavior) and cows sealed in the arena. Individual AI decisions
 * are stochastic, but the population and space are fixed and the long measurement window plus
 * median MSPT make run-to-run variance small.
 */
public class EntityStressPhase extends BenchmarkPhase {
    private final List<EntityType<? extends Mob>> spawnPlan = new ArrayList<>();
    private int spawned;

    public EntityStressPhase() {
        super("entity_stress", "Entity Stress");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        spawnPlan.clear();
        for (int i = 0; i < ctx.count(BenchmarkSpec.ENTITY_HUSKS); i++) spawnPlan.add(EntityType.HUSK);
        for (int i = 0; i < ctx.count(BenchmarkSpec.ENTITY_VILLAGERS); i++) spawnPlan.add(EntityType.VILLAGER);
        for (int i = 0; i < ctx.count(BenchmarkSpec.ENTITY_COWS); i++) spawnPlan.add(EntityType.COW);
        spawned = 0;
    }

    @Override
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        for (int i = 0; i < BenchmarkSpec.ENTITY_SPAWNS_PER_TICK && spawned < spawnPlan.size(); i++, spawned++) {
            ArenaBuilder.spawnMob(ctx.level, spawnPlan.get(spawned), ctx.random);
        }
        return spawned >= spawnPlan.size();
    }

    @Override
    protected int settleTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.ENTITY_SETTLE_TICKS);
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.ENTITY_MEASURE_TICKS);
    }

    @Override
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        ArenaBuilder.discardNonPlayers(ctx.level);
        return true;
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        PhaseResult r = msptResult();
        r.stats.put("entities", (double) spawnPlan.size());
        return r;
    }

    @Override
    public void abort(BenchmarkContext ctx) {
        ArenaBuilder.discardNonPlayers(ctx.level);
    }
}
