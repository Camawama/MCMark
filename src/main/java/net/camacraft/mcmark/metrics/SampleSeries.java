package net.camacraft.mcmark.metrics;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;

/** A growable series of nanosecond samples with basic statistics. Not thread-safe. */
public final class SampleSeries {
    private final LongArrayList samples = new LongArrayList();

    public void add(long nanos) {
        samples.add(nanos);
    }

    public int count() {
        return samples.size();
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public long sum() {
        long total = 0;
        for (int i = 0; i < samples.size(); i++) total += samples.getLong(i);
        return total;
    }

    public double meanMs() {
        return isEmpty() ? 0 : (sum() / (double) samples.size()) / 1.0e6;
    }

    public double percentileMs(double p) {
        if (isEmpty()) return 0;
        long[] sorted = samples.toLongArray();
        Arrays.sort(sorted);
        int idx = (int) Math.ceil((p / 100.0) * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx] / 1.0e6;
    }

    public double medianMs() {
        return percentileMs(50);
    }

    public double minMs() {
        if (isEmpty()) return 0;
        long min = Long.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) min = Math.min(min, samples.getLong(i));
        return min / 1.0e6;
    }

    public double maxMs() {
        if (isEmpty()) return 0;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < samples.size(); i++) max = Math.max(max, samples.getLong(i));
        return max / 1.0e6;
    }

    public double stdDevMs() {
        if (samples.size() < 2) return 0;
        double mean = sum() / (double) samples.size();
        double var = 0;
        for (int i = 0; i < samples.size(); i++) {
            double d = samples.getLong(i) - mean;
            var += d * d;
        }
        return Math.sqrt(var / (samples.size() - 1)) / 1.0e6;
    }

    /**
     * Mean of the worst {@code percent}% of samples, in ms. For frame times this is the basis of
     * "1% low FPS"; for tick times it captures stutter severity.
     */
    public double worstMeanMs(double percent) {
        if (isEmpty()) return 0;
        long[] sorted = samples.toLongArray();
        Arrays.sort(sorted);
        int n = Math.max(1, (int) Math.floor(sorted.length * (percent / 100.0)));
        long total = 0;
        for (int i = sorted.length - n; i < sorted.length; i++) total += sorted[i];
        return (total / (double) n) / 1.0e6;
    }
}
