package net.camacraft.mcmark;

import net.camacraft.mcmark.metrics.FrameStats;

import java.util.List;
import java.util.Map;

/**
 * Bridge from the (common-side) benchmark engine to client-only functionality.
 * The dedicated server uses {@link #NOOP}; the physical client installs a real implementation
 * during client setup. This avoids both custom networking and client-class loading on servers —
 * the engine runs on the integrated server in the same JVM as the client.
 */
public interface ClientHook {
    ClientHook NOOP = new ClientHook() {};

    default boolean hasClient() {
        return false;
    }

    /** Begins a render-test segment: the client sweeps the camera and records frame times. */
    default void beginRenderSegment(double seconds, float pitch, float startYaw) {}

    default boolean isRenderSegmentDone() {
        return true;
    }

    /** Returns and clears the stats of the last completed segment, or null. */
    default FrameStats takeRenderStats() {
        return null;
    }

    /** Differences between current video settings and the MCMark standard ("key=value (standard v)"). */
    default List<String> videoSettingsDeviations() {
        return List.of();
    }

    /** Temporarily applies standard video settings (never persisted). Returns true if applied. */
    default boolean applyStandardVideoSettings() {
        return false;
    }

    default void restoreVideoSettings() {}

    /** GPU/CPU/driver info and a video settings snapshot for the environment fingerprint. */
    default Map<String, String> captureClientEnvironment() {
        return Map.of();
    }
}
