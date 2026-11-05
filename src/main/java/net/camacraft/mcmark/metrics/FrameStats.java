package net.camacraft.mcmark.metrics;

/** Result of one client render-test segment, computed client-side from raw frame times. */
public final class FrameStats {
    public final int frames;
    public final double seconds;
    public final double avgFps;
    public final double onePercentLowFps;
    public final double p99FrameTimeMs;
    public final boolean interference;

    public FrameStats(int frames, double seconds, double avgFps, double onePercentLowFps,
                      double p99FrameTimeMs, boolean interference) {
        this.frames = frames;
        this.seconds = seconds;
        this.avgFps = avgFps;
        this.onePercentLowFps = onePercentLowFps;
        this.p99FrameTimeMs = p99FrameTimeMs;
        this.interference = interference;
    }

    public static FrameStats fromFrameTimes(SampleSeries frameTimes, double seconds, boolean interference) {
        int frames = frameTimes.count();
        double avgFps = seconds > 0 ? frames / seconds : 0;
        double worstMeanMs = frameTimes.worstMeanMs(1.0);
        double onePercentLow = worstMeanMs > 0 ? 1000.0 / worstMeanMs : 0;
        return new FrameStats(frames, seconds, avgFps, onePercentLow, frameTimes.percentileMs(99), interference);
    }
}
