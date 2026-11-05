package net.camacraft.mcmark.spec;

import net.minecraft.core.BlockPos;

/**
 * The MCMark benchmark specification.
 *
 * <p>Everything in this class is a deliberate constant. The fairness guarantee of MCMark rests on
 * every standard-suite run executing an identical workload: same seed, same coordinates, same
 * entity counts, same tick-counted measurement windows. None of these values are exposed to
 * configuration on purpose — a result is only comparable to another result if both were produced
 * by the same spec version, which is embedded in every result file.</p>
 *
 * <p>Any change to any value in this class MUST be accompanied by a bump of {@link #SPEC_VERSION},
 * and scores across spec versions must never be compared directly.</p>
 */
public final class BenchmarkSpec {
    private BenchmarkSpec() {}

    /** Version of the benchmark workload definition. Bump on ANY workload change. */
    public static final String SPEC_VERSION = "1.0.0";
    /** Version of the result JSON schema. Bump on payload shape changes. */
    public static final int SCHEMA_VERSION = 1;

    /** The official benchmark world seed: 0x4D434D41524B == ASCII "MCMARK". */
    public static final long WORLD_SEED = 0x4D434D41524BL;
    /** Worlds whose name starts with this prefix and use {@link #WORLD_SEED} auto-start a run. */
    public static final String WORLD_NAME_PREFIX = "MCMark";

    // ------------------------------------------------------------------
    // Arena (entity / item / redstone / fluid workloads)
    // ------------------------------------------------------------------

    /** Center of the sealed stress-test arena. Far from spawn so normal worlds are untouched. */
    public static final BlockPos ARENA_CENTER = new BlockPos(10_000, 196, 10_000);
    /** Half-width of the arena interior. Interior spans center ± this. */
    public static final int ARENA_HALF = 25;
    /** Shell half-width (walls sit here). */
    public static final int ARENA_SHELL = 26;
    public static final int ARENA_FLOOR_Y = 195;
    public static final int ARENA_ROOF_Y = 204;

    /** Origin (min corner) of the open redstone platform, east of the arena. */
    public static final BlockPos REDSTONE_ORIGIN = new BlockPos(10_064, 195, 9_976);
    public static final int REDSTONE_PLATFORM_X = 56;
    public static final int REDSTONE_PLATFORM_Z = 49;

    /** Forced-chunk region that covers the arena and redstone platform (inclusive chunk coords). */
    public static final int FORCED_MIN_CX = 620, FORCED_MAX_CX = 633;
    public static final int FORCED_MIN_CZ = 620, FORCED_MAX_CZ = 629;

    // ------------------------------------------------------------------
    // Chunk generation workload
    // ------------------------------------------------------------------

    /** Center chunk of the first (standard) generation region. */
    public static final int CHUNKGEN_CENTER_CX = 200;
    public static final int CHUNKGEN_CENTER_CZ = 200;
    /** Radius in chunks; a (2r+1)^2 square is generated (15x15 = 225 chunks). */
    public static final int CHUNKGEN_RADIUS = 7;
    /** Chunks generated synchronously per server tick during the phase. */
    public static final int CHUNKGEN_PER_TICK = 2;
    /** Region reuse spacing (chunks) when the standard region was already consumed. */
    public static final int CHUNKGEN_REGION_STRIDE = 64;

    // ------------------------------------------------------------------
    // Durations (server ticks) and workload sizes — STANDARD suite values.
    // The QUICK suite scales these via Suite#durationScale / Suite#countScale.
    // ------------------------------------------------------------------

    public static final int WARMUP_TICKS = 300;
    public static final int WARMUP_HUSKS = 40;
    public static final int WARMUP_COWS = 20;

    public static final int IDLE_SETTLE_TICKS = 100;
    public static final int IDLE_MEASURE_TICKS = 300;

    public static final int ENTITY_HUSKS = 100;
    public static final int ENTITY_VILLAGERS = 50;
    public static final int ENTITY_COWS = 60;
    public static final int ENTITY_SPAWNS_PER_TICK = 30;
    public static final int ENTITY_SETTLE_TICKS = 100;
    public static final int ENTITY_MEASURE_TICKS = 600;

    public static final int ITEM_INITIAL = 2_000;
    public static final int ITEM_WAVE_SIZE = 250;
    public static final int ITEM_WAVE_INTERVAL_TICKS = 40;
    public static final int ITEM_SPAWNS_PER_TICK = 250;
    public static final int ITEM_SETTLE_TICKS = 40;
    public static final int ITEM_MEASURE_TICKS = 600;

    public static final int REDSTONE_CLOCK_COLUMNS = 12;
    public static final int REDSTONE_CLOCK_ROWS = 12;
    public static final int REDSTONE_SETTLE_TICKS = 60;
    public static final int REDSTONE_MEASURE_TICKS = 600;

    public static final int FLUID_SETTLE_TICKS = 0;
    public static final int FLUID_MEASURE_TICKS = 400;

    /**
     * Chunk radius pre-generated (unmeasured) around the render vantage before the FPS test,
     * so the client's full view distance is available and no chunk generation happens during
     * measurement. Must exceed {@link #STD_RENDER_DISTANCE}.
     */
    public static final int RENDER_PREGEN_RADIUS = 13;
    public static final int RENDER_PREGEN_PER_TICK = 4;

    public static final int RENDER_SEGMENTS = 2;
    public static final double RENDER_SEGMENT_SECONDS = 15.0;
    public static final int RENDER_SETTLE_TICKS = 150;
    /** Watchdog: abort a render segment if the client hasn't reported back after this many ticks. */
    public static final int RENDER_TIMEOUT_TICKS = 900;

    /** Ticks of stabilization before an auto-started run begins in a benchmark world. */
    public static final int AUTOSTART_DELAY_TICKS = 200;

    // ------------------------------------------------------------------
    // Standard client video settings (required for a standard client score)
    // ------------------------------------------------------------------

    public static final int STD_RENDER_DISTANCE = 12;
    public static final int STD_SIMULATION_DISTANCE = 12;
    public static final boolean STD_VSYNC = false;
    /** 260 is the vanilla slider value meaning "unlimited". */
    public static final int STD_FPS_LIMIT = 260;
    public static final int STD_BIOME_BLEND = 2;

    /** Blend used for the client score: avgFps * 0.6 + onePercentLowFps * 0.4. */
    public static final double RENDER_AVG_WEIGHT = 0.6;
    public static final double RENDER_LOW_WEIGHT = 0.4;
}
