package net.camacraft.mcmark.spec;

/**
 * Benchmark suite selection. Only {@link #STANDARD} produces upload-eligible results;
 * {@link #QUICK} is a shortened sanity-check run and is always flagged non-standard.
 */
public enum Suite {
    STANDARD(1.0, 1.0),
    QUICK(0.4, 0.5);

    public final double durationScale;
    public final double countScale;

    Suite(double durationScale, double countScale) {
        this.durationScale = durationScale;
        this.countScale = countScale;
    }

    public int scaleTicks(int standardTicks) {
        return Math.max(60, (int) Math.round(standardTicks * durationScale));
    }

    public int scaleCount(int standardCount) {
        return Math.max(1, (int) Math.round(standardCount * countScale));
    }

    public static Suite byName(String name) {
        for (Suite s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return STANDARD;
    }
}
