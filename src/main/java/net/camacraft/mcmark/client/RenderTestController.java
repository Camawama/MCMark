package net.camacraft.mcmark.client;

import net.camacraft.mcmark.metrics.FrameStats;
import net.camacraft.mcmark.metrics.SampleSeries;
import net.minecraft.client.Minecraft;

/**
 * Runs render-test segments on the client: sweeps the camera 360° over the segment duration
 * while recording per-frame times. State is guarded because the (integrated) server thread
 * starts segments and polls completion while the render thread records frames.
 */
public final class RenderTestController {
    private static final Object LOCK = new Object();

    private static boolean active;
    private static boolean done;
    private static double durationSeconds;
    private static float pitch;
    private static float baseYaw;
    private static long startNanos;
    private static long lastFrameNanos;
    private static boolean interference;
    private static SampleSeries frameTimes = new SampleSeries();
    private static FrameStats finished;

    private RenderTestController() {}

    /** Called from the server thread via the client hook. */
    public static void beginSegment(double seconds, float segmentPitch, float startYaw) {
        synchronized (LOCK) {
            durationSeconds = seconds;
            pitch = segmentPitch;
            baseYaw = startYaw;
            frameTimes = new SampleSeries();
            interference = false;
            startNanos = 0;
            lastFrameNanos = 0;
            finished = null;
            done = false;
            active = true;
        }
    }

    public static boolean isSegmentDone() {
        synchronized (LOCK) {
            return done;
        }
    }

    public static FrameStats takeStats() {
        synchronized (LOCK) {
            FrameStats stats = finished;
            finished = null;
            return stats;
        }
    }

    public static boolean isActive() {
        synchronized (LOCK) {
            return active;
        }
    }

    /** Called once per frame from the render tick (render thread). */
    public static void onFrame(Minecraft mc) {
        synchronized (LOCK) {
            if (!active) return;
            long now = System.nanoTime();
            if (startNanos == 0) {
                startNanos = now;
                lastFrameNanos = now;
            } else {
                frameTimes.add(now - lastFrameNanos);
                lastFrameNanos = now;
            }
            if (mc.screen != null || mc.isPaused()) interference = true;

            double elapsed = (now - startNanos) / 1.0e9;
            if (mc.player != null) {
                float yaw = (float) (baseYaw + 360.0 * Math.min(1.0, elapsed / durationSeconds));
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
            }
            if (elapsed >= durationSeconds) {
                finished = FrameStats.fromFrameTimes(frameTimes, elapsed, interference);
                active = false;
                done = true;
            }
        }
    }

    public static void reset() {
        synchronized (LOCK) {
            active = false;
            done = false;
            finished = null;
        }
    }
}
