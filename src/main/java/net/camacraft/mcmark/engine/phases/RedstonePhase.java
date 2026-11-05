package net.camacraft.mcmark.engine.phases;

import net.camacraft.mcmark.engine.ArenaBuilder;
import net.camacraft.mcmark.engine.BenchmarkContext;
import net.camacraft.mcmark.engine.BenchmarkPhase;
import net.camacraft.mcmark.engine.PhaseResult;
import net.camacraft.mcmark.engine.Step;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Redstone/block-update stress: a grid of observer-pair clocks (2gt period) each driving two
 * redstone lamps, producing a fixed, fully deterministic rate of block updates, state changes
 * and light updates on the open platform east of the arena.
 */
public class RedstonePhase extends BenchmarkPhase {
    private final List<BlockPos> kickPositions = new ArrayList<>();
    private Step platformStep;
    private boolean built;
    private boolean kickedOff;
    private int clockCount;

    public RedstonePhase() {
        super("redstone", "Redstone");
    }

    @Override
    public void begin(BenchmarkContext ctx) {
        BlockPos o = BenchmarkSpec.REDSTONE_ORIGIN;
        platformStep = ArenaBuilder.fill(ctx.level, o,
                new BlockPos(o.getX() + BenchmarkSpec.REDSTONE_PLATFORM_X - 1, o.getY(),
                        o.getZ() + BenchmarkSpec.REDSTONE_PLATFORM_Z - 1),
                Blocks.STONE.defaultBlockState(), 700);
        built = false;
        kickedOff = false;
        kickPositions.clear();
        clockCount = 0;
    }

    @Override
    protected boolean setupTick(BenchmarkContext ctx, int tick) {
        if (!platformStep.tick()) return false;
        if (!built) {
            buildClocks(ctx);
            built = true;
            return false;
        }
        if (!kickedOff) {
            // Re-place one observer of each pair so every clock is guaranteed to start pulsing.
            BlockState observerWest = Blocks.OBSERVER.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST);
            for (BlockPos pos : kickPositions) {
                ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                ctx.level.setBlock(pos, observerWest, 3);
            }
            kickedOff = true;
        }
        return true;
    }

    private void buildClocks(BenchmarkContext ctx) {
        BlockPos o = BenchmarkSpec.REDSTONE_ORIGIN;
        int y = o.getY() + 1;
        BlockState lamp = Blocks.REDSTONE_LAMP.defaultBlockState();
        BlockState observerEast = Blocks.OBSERVER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        BlockState observerWest = Blocks.OBSERVER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST);
        int cols = ctx.count(BenchmarkSpec.REDSTONE_CLOCK_COLUMNS);
        int rows = BenchmarkSpec.REDSTONE_CLOCK_ROWS;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                int x = o.getX() + 4 + i * 4;
                int z = o.getZ() + 4 + j * 3;
                ctx.level.setBlock(new BlockPos(x - 1, y, z), lamp, 3);
                ctx.level.setBlock(new BlockPos(x, y, z), observerEast, 3);
                ctx.level.setBlock(new BlockPos(x + 1, y, z), observerWest, 3);
                ctx.level.setBlock(new BlockPos(x + 2, y, z), lamp, 3);
                kickPositions.add(new BlockPos(x + 1, y, z));
                clockCount++;
            }
        }
    }

    @Override
    protected int settleTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.REDSTONE_SETTLE_TICKS);
    }

    @Override
    protected int measureTicks(BenchmarkContext ctx) {
        return ctx.ticks(BenchmarkSpec.REDSTONE_MEASURE_TICKS);
    }

    @Override
    protected boolean cleanupTick(BenchmarkContext ctx, int tick) {
        return clearStep(ctx).tick();
    }

    private Step clearStep(BenchmarkContext ctx) {
        if (clearStep == null) {
            BlockPos o = BenchmarkSpec.REDSTONE_ORIGIN;
            clearStep = ArenaBuilder.fill(ctx.level, new BlockPos(o.getX(), o.getY(), o.getZ()),
                    new BlockPos(o.getX() + BenchmarkSpec.REDSTONE_PLATFORM_X - 1, o.getY() + 2,
                            o.getZ() + BenchmarkSpec.REDSTONE_PLATFORM_Z - 1),
                    Blocks.AIR.defaultBlockState(), 1200);
        }
        return clearStep;
    }

    private Step clearStep;

    @Override
    public PhaseResult result(BenchmarkContext ctx) {
        PhaseResult r = msptResult();
        r.stats.put("clocks", (double) clockCount);
        return r;
    }

    @Override
    public void abort(BenchmarkContext ctx) {
        // Blocks are inside the standard teardown regions; nothing extra needed.
    }
}
