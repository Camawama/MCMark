package net.camacraft.mcmark.result;

import net.camacraft.mcmark.engine.PhaseResult;

import java.util.List;
import java.util.Map;

/**
 * Turns raw phase metrics into scores.
 *
 * <p>Each phase is scored as {@code 1000 * ratio} against a fixed reference value (the
 * "calibration constants" below, defined once per spec version). The overall score is a
 * weighted geometric mean, so no single phase can dominate and improvements multiply.</p>
 *
 * <p>Absolute scores are only comparable on similar hardware; the intended workflow is comparing
 * runs on the SAME machine (e.g. a modpack vs. the vanilla baseline), which cancels hardware out.
 * The future website normalizes across machines using submitted baseline runs.</p>
 */
public final class ScoreCalculator {
    private ScoreCalculator() {}

    private record Ref(double baseline, double weight) {}

    /** Reference values for spec 1.0.0 (nominal mid-range 2023 desktop, vanilla + MCMark). */
    private static final Map<String, Ref> SERVER_REFS = Map.of(
            "idle", new Ref(2.0, 0.10),          // median ms per tick, lower better
            "chunk_gen", new Ref(40.0, 0.20),    // chunks per second, higher better
            "entity_stress", new Ref(9.0, 0.25), // median ms per tick
            "item_stress", new Ref(8.0, 0.15),   // median ms per tick
            "redstone", new Ref(5.0, 0.15),      // median ms per tick
            "fluids", new Ref(6.0, 0.15)         // median ms per tick
    );

    private static final double RENDER_BASELINE_FPS = 200.0;
    private static final double SERVER_EXPONENT = 0.65;
    private static final double CLIENT_EXPONENT = 0.35;

    public static class Totals {
        public double overall;
        public double server;
        public Double client;
    }

    /** Computes per-phase scores in place and returns aggregate totals. */
    public static Totals compute(List<PhaseResult> results) {
        double weightedLogSum = 0;
        double weightSum = 0;
        Double clientRatio = null;

        for (PhaseResult r : results) {
            if (!r.scored) continue;
            double ratio = ratio(r);
            r.score = 1000.0 * ratio;

            Ref ref = SERVER_REFS.get(r.id);
            if (ref != null) {
                weightedLogSum += ref.weight() * Math.log(ratio);
                weightSum += ref.weight();
            } else if (r.id.equals("render")) {
                clientRatio = ratio;
            }
        }

        Totals totals = new Totals();
        totals.server = weightSum > 0 ? 1000.0 * Math.exp(weightedLogSum / weightSum) : 0;
        if (clientRatio != null) {
            totals.client = 1000.0 * clientRatio;
            totals.overall = 1000.0 * Math.exp(
                    SERVER_EXPONENT * Math.log(totals.server / 1000.0)
                            + CLIENT_EXPONENT * Math.log(totals.client / 1000.0));
        } else {
            totals.client = null;
            totals.overall = totals.server;
        }
        return totals;
    }

    private static double ratio(PhaseResult r) {
        double baseline = r.id.equals("render") ? RENDER_BASELINE_FPS
                : SERVER_REFS.containsKey(r.id) ? SERVER_REFS.get(r.id).baseline() : 1.0;
        double measured = Math.max(r.primary, 1.0e-6);
        double ratio = r.lowerIsBetter ? baseline / measured : measured / baseline;
        return Math.max(0.01, Math.min(100.0, ratio));
    }
}
