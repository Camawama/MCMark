package net.camacraft.mcmark.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Raw outcome of one benchmark phase; scored later by the ScoreCalculator. */
public class PhaseResult {
    public final String id;
    public final String display;
    public boolean scored = true;
    public String metricName = "median_mspt";
    public String unit = "ms";
    public boolean lowerIsBetter = true;
    public double primary;
    public double score;
    public final Map<String, Double> stats = new LinkedHashMap<>();

    public PhaseResult(String id, String display) {
        this.id = id;
        this.display = display;
    }
}
