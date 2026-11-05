package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.ArenaBuilder;
import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.engine.Step;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fluid stress: a stone terrace pyramid inside the arena is flooded from the top; the water
 * cascades over three tiers and floods the floor. Placement geometry and timing are fixed,
 * so the flow-update workload is identical every run.
 */
public class FluidPhase extends BenchmarkPhase {
    private final Deque<Step> buildSteps = new ArrayDeque<>();
    private boolean sourcesPlaced;
    private Step clearStep;

    public FluidPhase() {
        super("fluids", "Fluids");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        int cx = BenchmarkSpec.ARENA_CENTER.getX();
        int cz = BenchmarkSpec.ARENA_CENTER.getZ();
        var stone = Blocks.STONE.defaultBlockState();
        buildSteps.clear();
        buildSteps.add(ArenaBuilder.fill(ctx.level, new BlockPos(cx - 12, 196, cz - 12),
                new BlockPos(cx + 12, 197, cz + 12), stone, 900));
        buildSteps.add(ArenaBuilder.fill(ctx.level, new BlockPos(cx - 8, 198, cz - 8),
                new BlockPos(cx + 8, 199, cz + 8), stone, 900));
        buildSteps.add(ArenaBuilder.fill(ctx.level, new BlockPos(cx - 4, 200, cz - 4),
                new BlockPos(cx + 4, 201, cz + 4), stone, 900));
        sourcesPlaced = false;
        clearStep = null;
    }

    @Override
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        Step step = buildSteps.peek();
        if (step != null) {
            if (step.tick()) buildSteps.poll();
            return false;
        }
        if (!sourcesPlaced) {
            int cx = BenchmarkSpec.ARENA_CENTER.getX();
            int cz = BenchmarkSpec.ARENA_CENTER.getZ();
            var water = Blocks.WATER.defaultBlockState();
            for (int x = cx - 4; x <= cx + 4; x++) {
                for (int z = cz - 4; z <= cz + 4; z++) {
                    ctx.level.setBlock(new BlockPos(x, 202, z), water, 3);
                }
            }
            sourcesPlaced = true;
        }
        return true;
    }

    @Override
    protected int settleTicks(BenchmarkContext ctx) {
        return BenchmarkSpec.FLUID_SETTLE_TICKS;
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.FLUID_MEASURE_TICKS);
    }

    @Override
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        if (clearStep == null) {
            int cx = BenchmarkSpec.ARENA_CENTER.getX();
            int cz = BenchmarkSpec.ARENA_CENTER.getZ();
            clearStep = ArenaBuilder.fill(ctx.level,
                    new BlockPos(cx - BenchmarkSpec.ARENA_HALF, 196, cz - BenchmarkSpec.ARENA_HALF),
                    new BlockPos(cx + BenchmarkSpec.ARENA_HALF, 203, cz + BenchmarkSpec.ARENA_HALF),
                    Blocks.AIR.defaultBlockState(), 2000);
        }
        return clearStep.tick();
    }

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        return msptResult();
    }
}
