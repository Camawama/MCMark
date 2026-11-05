package net.camacraft.mcmark.engine;

import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds and tears down the benchmark structures, and spawns deterministic workloads. */
public final class ArenaBuilder {
    private ArenaBuilder() {}

    private static final int CX = BenchmarkSpec.ARENA_CENTER.getX();
    private static final int CZ = BenchmarkSpec.ARENA_CENTER.getZ();
    private static final int SHELL = BenchmarkSpec.ARENA_SHELL;
    private static final int FLOOR_Y = BenchmarkSpec.ARENA_FLOOR_Y;
    private static final int ROOF_Y = BenchmarkSpec.ARENA_ROOF_Y;

    // ------------------------------------------------------------------
    // Tick-spread world editing
    // ------------------------------------------------------------------

    /** A fill that places up to {@code perTick} blocks per tick, x-major order, update flag 2. */
    public static Step fill(ServerLevel level, BlockPos min, BlockPos max, BlockState state, int perTick) {
        final int dx = max.getX() - min.getX() + 1;
        final int dy = max.getY() - min.getY() + 1;
        final int dz = max.getZ() - min.getZ() + 1;
        final int volume = dx * dy * dz;
        return new Step() {
            int cursor = 0;

            @Override
            public boolean tick() {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                int end = Math.min(volume, cursor + perTick);
                while (cursor < end) {
                    int i = cursor++;
                    pos.set(min.getX() + i % dx,
                            min.getY() + (i / dx) % dy,
                            min.getZ() + i / (dx * dy));
                    level.setBlock(pos, state, 2);
                }
                return cursor >= volume;
            }
        };
    }

    /** Forces and synchronously loads the working chunk region, a few chunks per tick. */
    public static Step forceAndLoadChunks(ServerLevel level) {
        final int minCx = BenchmarkSpec.FORCED_MIN_CX, maxCx = BenchmarkSpec.FORCED_MAX_CX;
        final int minCz = BenchmarkSpec.FORCED_MIN_CZ, maxCz = BenchmarkSpec.FORCED_MAX_CZ;
        final int width = maxCx - minCx + 1;
        final int total = width * (maxCz - minCz + 1);
        return new Step() {
            int cursor = 0;

            @Override
            public boolean tick() {
                int end = Math.min(total, cursor + 6);
                while (cursor < end) {
                    int i = cursor++;
                    int cx = minCx + i % width;
                    int cz = minCz + i / width;
                    level.getChunk(cx, cz, ChunkStatus.FULL, true);
                    level.setChunkForced(cx, cz, true);
                }
                return cursor >= total;
            }
        };
    }

    public static void unforceChunks(ServerLevel level) {
        for (int cx = BenchmarkSpec.FORCED_MIN_CX; cx <= BenchmarkSpec.FORCED_MAX_CX; cx++) {
            for (int cz = BenchmarkSpec.FORCED_MIN_CZ; cz <= BenchmarkSpec.FORCED_MAX_CZ; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }

    /** Sealed, lit stress arena: stone shell with a sea-lantern grid in the roof. */
    public static List<Step> buildArenaSteps(ServerLevel level) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        List<Step> steps = new ArrayList<>();
        steps.add(fill(level, new BlockPos(CX - SHELL, FLOOR_Y, CZ - SHELL),
                new BlockPos(CX + SHELL, FLOOR_Y, CZ + SHELL), stone, 700));
        steps.add(fill(level, new BlockPos(CX - SHELL, ROOF_Y, CZ - SHELL),
                new BlockPos(CX + SHELL, ROOF_Y, CZ + SHELL), stone, 700));
        steps.add(fill(level, new BlockPos(CX - SHELL, FLOOR_Y + 1, CZ - SHELL),
                new BlockPos(CX - SHELL, ROOF_Y - 1, CZ + SHELL), stone, 700));
        steps.add(fill(level, new BlockPos(CX + SHELL, FLOOR_Y + 1, CZ - SHELL),
                new BlockPos(CX + SHELL, ROOF_Y - 1, CZ + SHELL), stone, 700));
        steps.add(fill(level, new BlockPos(CX - SHELL, FLOOR_Y + 1, CZ - SHELL),
                new BlockPos(CX + SHELL, ROOF_Y - 1, CZ - SHELL), stone, 700));
        steps.add(fill(level, new BlockPos(CX - SHELL, FLOOR_Y + 1, CZ + SHELL),
                new BlockPos(CX + SHELL, ROOF_Y - 1, CZ + SHELL), stone, 700));
        steps.add(Step.once(() -> {
            BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
            for (int x = CX - SHELL + 3; x <= CX + SHELL - 3; x += 6) {
                for (int z = CZ - SHELL + 3; z <= CZ + SHELL - 3; z += 6) {
                    level.setBlock(new BlockPos(x, ROOF_Y, z), lantern, 2);
                }
            }
        }));
        return steps;
    }

    /** Removes everything MCMark placed: arena shell + contents and the redstone platform. */
    public static List<Step> clearAllSteps(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once(() -> discardNonPlayers(level)));
        steps.add(fill(level, new BlockPos(CX - SHELL, FLOOR_Y, CZ - SHELL),
                new BlockPos(CX + SHELL, ROOF_Y, CZ + SHELL), air, 1500));
        BlockPos rs = BenchmarkSpec.REDSTONE_ORIGIN;
        steps.add(fill(level, rs,
                new BlockPos(rs.getX() + BenchmarkSpec.REDSTONE_PLATFORM_X - 1, rs.getY() + 3,
                        rs.getZ() + BenchmarkSpec.REDSTONE_PLATFORM_Z - 1), air, 1500));
        steps.add(Step.once(() -> discardNonPlayers(level)));
        return steps;
    }

    // ------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------

    /** Bounds covering the arena and redstone platform with margin, for entity cleanup. */
    public static AABB workingBounds() {
        return new AABB(CX - SHELL - 8, FLOOR_Y - 6, CZ - SHELL - 8,
                CX + 130, ROOF_Y + 8, CZ + SHELL + 8);
    }

    public static void discardNonPlayers(ServerLevel level) {
        for (Entity e : level.getEntities((Entity) null, workingBounds(), e -> !(e instanceof Player))) {
            e.discard();
        }
    }

    public static void discardItems(ServerLevel level) {
        for (Entity e : level.getEntities((Entity) null, workingBounds(), e -> e instanceof ItemEntity)) {
            e.discard();
        }
    }

    /** Deterministic uniform position on the arena floor, inset from the walls. */
    public static double[] randomFloorPos(Random rng) {
        int inner = BenchmarkSpec.ARENA_HALF - 4;
        double x = CX - inner + rng.nextDouble() * (inner * 2);
        double z = CZ - inner + rng.nextDouble() * (inner * 2);
        return new double[]{x, z};
    }

    public static void spawnMob(ServerLevel level, EntityType<? extends Mob> type, Random rng) {
        Mob mob = type.create(level);
        if (mob == null) return;
        double[] p = randomFloorPos(rng);
        mob.moveTo(p[0], FLOOR_Y + 1.05, p[1], rng.nextFloat() * 360f, 0f);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    public static void dropItem(ServerLevel level, ItemStack stack, Random rng) {
        double[] p = randomFloorPos(rng);
        ItemEntity item = new ItemEntity(level, p[0], FLOOR_Y + 3.0, p[1], stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }
}
