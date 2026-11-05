package net.camacraft.mcmark.spec;

/** What a benchmark run is measuring. Recorded in the result for leaderboard categorization. */
public enum TargetType {
    /** A single mod being evaluated (typically vanilla + that mod + MCMark). */
    MOD,
    /** A full modpack. */
    MODPACK,
    /** Vanilla + MCMark only — the reference point for relative comparisons. */
    BASELINE;

    public static TargetType byName(String name) {
        for (TargetType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return MODPACK;
    }

    /** Best-effort guess used for auto-started runs; users can override via command args. */
    public static TargetType guess(int nonSystemModCount) {
        if (nonSystemModCount == 0) return BASELINE;
        return nonSystemModCount <= 15 ? MOD : MODPACK;
    }
}
