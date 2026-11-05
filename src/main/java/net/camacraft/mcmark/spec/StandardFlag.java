package net.camacraft.mcmark.spec;

/**
 * Conditions detected during a run that affect how comparable the result is.
 * Flags with {@code breaksStandard == true} make a result ineligible for the public leaderboard;
 * informational flags are recorded so the website can filter/categorize, but do not invalidate.
 */
public enum StandardFlag {
    QUICK_SUITE(true, "Quick suite — shortened workloads"),
    NON_STANDARD_SEED(true, "World does not use the official MCMark seed"),
    CHUNKGEN_REGION_REUSED(true, "Standard chunk-generation region already consumed in this world"),
    NO_CLIENT_PHASES(true, "No client attached — render phase skipped"),
    NON_STANDARD_VIDEO_SETTINGS(true, "Video settings deviate from the MCMark standard"),
    RENDER_INTERRUPTED(true, "A screen was opened during the render phase"),
    RENDER_TIMEOUT(true, "Client failed to complete a render segment in time"),
    DEDICATED_SERVER(true, "Run on a dedicated server"),
    MULTIPLE_PLAYERS(true, "More than one player was online during the run"),
    /** Informational: profilers (spark, Observable, ...) add overhead but people run them 24/7. */
    PROFILER_ACTIVE(false, "A profiler mod was active during the run");

    public final boolean breaksStandard;
    public final String description;

    StandardFlag(boolean breaksStandard, String description) {
        this.breaksStandard = breaksStandard;
        this.description = description;
    }
}
