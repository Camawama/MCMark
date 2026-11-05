package net.camacraft.mcmark.env;

import net.camacraft.mcmark.ClientHook;
import net.camacraft.mcmark.detect.PerfModDetector;
import net.camacraft.mcmark.result.BenchmarkResult;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.versions.forge.ForgeVersion;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Captures the environment fingerprint recorded in every result: hardware, JVM, versions,
 * and the complete mod list. This is what makes results auditable — a leaderboard entry can
 * always be traced back to exactly what was running.
 */
public final class EnvironmentCapture {
    private EnvironmentCapture() {}

    public static BenchmarkResult.Environment capture(ClientHook client) {
        BenchmarkResult.Environment env = new BenchmarkResult.Environment();
        env.os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        env.arch = System.getProperty("os.arch");
        env.javaVersion = System.getProperty("java.version");
        env.javaVendor = System.getProperty("java.vendor");
        env.jvmArgs = new ArrayList<>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        env.cpuThreads = Runtime.getRuntime().availableProcessors();
        env.maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        env.minecraftVersion = SharedConstants.getCurrentVersion().getName();
        env.forgeVersion = ForgeVersion.getVersion();
        env.dist = FMLEnvironment.dist.toString();

        var clientInfo = client.captureClientEnvironment();
        env.cpuName = clientInfo.getOrDefault("cpu", "unknown");
        env.gpuRenderer = clientInfo.getOrDefault("gpuRenderer", "unknown");
        env.gpuVendor = clientInfo.getOrDefault("gpuVendor", "unknown");
        env.openGlVersion = clientInfo.getOrDefault("openGlVersion", "unknown");
        clientInfo.forEach((k, v) -> {
            if (k.startsWith("setting.")) env.clientSettings.put(k.substring("setting.".length()), v);
        });
        return env;
    }

    public static List<BenchmarkResult.ModEntry> captureMods() {
        List<BenchmarkResult.ModEntry> mods = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            BenchmarkResult.ModEntry entry = new BenchmarkResult.ModEntry();
            entry.id = info.getModId();
            entry.name = info.getDisplayName();
            entry.version = info.getVersion().toString();
            var cat = PerfModDetector.categoryOf(info.getModId());
            if (cat != null) entry.perfCategory = cat.name();
            mods.add(entry);
        }
        mods.sort(Comparator.comparing(m -> m.id));
        return mods;
    }
}
