package net.camacraft.mcmark.client;

import net.camacraft.mcmark.MCMark;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies MCMark's standard video settings temporarily for the render phase and restores the
 * user's settings afterwards. Settings are only changed in memory — MCMark never calls
 * {@code options.save()}, so nothing is written over the user's options file.
 */
public final class VideoSettingsManager {
    private static Snapshot snapshot;

    private VideoSettingsManager() {}

    private record Snapshot(int renderDistance, int simulationDistance, boolean vsync, int fpsLimit,
                            GraphicsStatus graphics, CloudStatus clouds, boolean ambientOcclusion,
                            boolean entityShadows, int biomeBlend) {}

    public static List<String> deviations() {
        Options o = Minecraft.getInstance().options;
        List<String> out = new ArrayList<>();
        check(out, "renderDistance", o.renderDistance().get(), BenchmarkSpec.STD_RENDER_DISTANCE);
        check(out, "simulationDistance", o.simulationDistance().get(), BenchmarkSpec.STD_SIMULATION_DISTANCE);
        check(out, "vsync", o.enableVsync().get(), BenchmarkSpec.STD_VSYNC);
        check(out, "fpsLimit", o.framerateLimit().get(), BenchmarkSpec.STD_FPS_LIMIT);
        check(out, "graphics", o.graphicsMode().get(), GraphicsStatus.FANCY);
        check(out, "clouds", o.cloudStatus().get(), CloudStatus.FANCY);
        check(out, "ambientOcclusion", o.ambientOcclusion().get(), true);
        check(out, "entityShadows", o.entityShadows().get(), true);
        check(out, "biomeBlend", o.biomeBlendRadius().get(), BenchmarkSpec.STD_BIOME_BLEND);
        return out;
    }

    private static <T> void check(List<String> out, String name, T actual, T standard) {
        if (!actual.equals(standard)) {
            out.add(name + "=" + actual + " (standard " + standard + ")");
        }
    }

    public static void apply() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        if (snapshot == null) {
            snapshot = new Snapshot(o.renderDistance().get(), o.simulationDistance().get(),
                    o.enableVsync().get(), o.framerateLimit().get(), o.graphicsMode().get(),
                    o.cloudStatus().get(), o.ambientOcclusion().get(), o.entityShadows().get(),
                    o.biomeBlendRadius().get());
        }
        o.renderDistance().set(BenchmarkSpec.STD_RENDER_DISTANCE);
        o.simulationDistance().set(BenchmarkSpec.STD_SIMULATION_DISTANCE);
        o.enableVsync().set(BenchmarkSpec.STD_VSYNC);
        o.framerateLimit().set(BenchmarkSpec.STD_FPS_LIMIT);
        o.graphicsMode().set(GraphicsStatus.FANCY);
        o.cloudStatus().set(CloudStatus.FANCY);
        o.ambientOcclusion().set(true);
        o.entityShadows().set(true);
        o.biomeBlendRadius().set(BenchmarkSpec.STD_BIOME_BLEND);
        mc.levelRenderer.allChanged();
        MCMark.LOGGER.info("Applied standard MCMark video settings (in memory only)");
    }

    public static void restore() {
        if (snapshot == null) return;
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        o.renderDistance().set(snapshot.renderDistance());
        o.simulationDistance().set(snapshot.simulationDistance());
        o.enableVsync().set(snapshot.vsync());
        o.framerateLimit().set(snapshot.fpsLimit());
        o.graphicsMode().set(snapshot.graphics());
        o.cloudStatus().set(snapshot.clouds());
        o.ambientOcclusion().set(snapshot.ambientOcclusion());
        o.entityShadows().set(snapshot.entityShadows());
        o.biomeBlendRadius().set(snapshot.biomeBlend());
        snapshot = null;
        mc.levelRenderer.allChanged();
        MCMark.LOGGER.info("Restored user video settings");
    }

    public static Map<String, String> settingsSnapshot() {
        Options o = Minecraft.getInstance().options;
        Map<String, String> map = new LinkedHashMap<>();
        map.put("setting.renderDistance", String.valueOf(o.renderDistance().get()));
        map.put("setting.simulationDistance", String.valueOf(o.simulationDistance().get()));
        map.put("setting.vsync", String.valueOf(o.enableVsync().get()));
        map.put("setting.fpsLimit", String.valueOf(o.framerateLimit().get()));
        map.put("setting.graphics", String.valueOf(o.graphicsMode().get()));
        map.put("setting.clouds", String.valueOf(o.cloudStatus().get()));
        map.put("setting.biomeBlend", String.valueOf(o.biomeBlendRadius().get()));
        map.put("setting.guiScale", String.valueOf(o.guiScale().get()));
        map.put("setting.fullscreen", String.valueOf(o.fullscreen().get()));
        return map;
    }
}
