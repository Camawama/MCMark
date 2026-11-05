package net.camacraft.mcmark.client;

/** Cheap rolling FPS estimate for the HUD (frame count over the last second). */
public final class ClientFrameMeter {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final long[] stamps = new long[512];
    private static int head;
    private static int count;

    private ClientFrameMeter() {}

    public static void onFrame() {
        long now = System.nanoTime();
        stamps[head] = now;
        head = (head + 1) % stamps.length;
        if (count < stamps.length) count++;
    }

    public static int fps() {
        long now = System.nanoTime();
        int frames = 0;
        for (int i = 0; i < count; i++) {
            int idx = (head - 1 - i + stamps.length * 2) % stamps.length;
            if (now - stamps[idx] <= WINDOW_NANOS) frames++;
            else break;
        }
        return frames;
    }
}
