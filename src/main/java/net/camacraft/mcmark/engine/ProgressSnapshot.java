package net.camacraft.mcmark.engine;

/** Immutable snapshot of engine progress, safe to read from the render thread for the HUD. */
public record ProgressSnapshot(
        Status status,
        String suite,
        String phaseDisplay,
        int phaseIndex,
        int phaseCount,
        double phaseProgress,
        double overallProgress,
        double recentMspt,
        double finalScore,
        boolean standard
) {
    public enum Status { IDLE, PREPARING, RUNNING, FINISHING, COMPLETE }

    public static final ProgressSnapshot IDLE_SNAPSHOT =
            new ProgressSnapshot(Status.IDLE, "", "", 0, 0, 0, 0, 0, 0, true);

    public boolean active() {
        return status == Status.PREPARING || status == Status.RUNNING || status == Status.FINISHING;
    }
}
