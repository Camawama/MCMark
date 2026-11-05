package net.camacraft.mcmark.detect;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognizes known performance-related mods in the loaded mod list so results can be
 * categorized (e.g. leaderboards "with optimization mods" vs "without").
 *
 * <p>This registry is intentionally in code and versioned with the mod: it is part of the
 * classification behavior of a given MCMark release, so two users on the same MCMark version
 * always classify identically.</p>
 */
public final class PerfModDetector {
    private PerfModDetector() {}

    /** Mod ids that are part of the platform itself and never counted as content. */
    public static final Set<String> SYSTEM_MOD_IDS = Set.of("minecraft", "forge", "mcmark");

    private static final Map<String, PerfModCategory> KNOWN = Map.ofEntries(
            // Rendering optimizers
            Map.entry("rubidium", PerfModCategory.RENDERING),
            Map.entry("embeddium", PerfModCategory.RENDERING),
            Map.entry("magnesium", PerfModCategory.RENDERING),
            Map.entry("sodium", PerfModCategory.RENDERING),
            Map.entry("xenon", PerfModCategory.RENDERING),
            Map.entry("entityculling", PerfModCategory.RENDERING),
            Map.entry("entity_culling", PerfModCategory.RENDERING),
            Map.entry("cullleaves", PerfModCategory.RENDERING),
            Map.entry("cull_less_leaves", PerfModCategory.RENDERING),
            Map.entry("immediatelyfast", PerfModCategory.RENDERING),
            Map.entry("exordium", PerfModCategory.RENDERING),
            Map.entry("betterfpsdist", PerfModCategory.RENDERING),
            Map.entry("farsight_view", PerfModCategory.RENDERING),
            Map.entry("farsight", PerfModCategory.RENDERING),
            Map.entry("oculus", PerfModCategory.RENDERING),
            Map.entry("iris", PerfModCategory.RENDERING),
            Map.entry("flywheel", PerfModCategory.RENDERING),
            Map.entry("dynamic_fps", PerfModCategory.RENDERING),
            // Tick / logic optimizers
            Map.entry("lithium", PerfModCategory.SERVER_TICK),
            Map.entry("canary", PerfModCategory.SERVER_TICK),
            Map.entry("radium", PerfModCategory.SERVER_TICK),
            Map.entry("roadrunner", PerfModCategory.SERVER_TICK),
            Map.entry("performant", PerfModCategory.SERVER_TICK),
            Map.entry("aiimprovements", PerfModCategory.SERVER_TICK),
            Map.entry("clumps", PerfModCategory.SERVER_TICK),
            Map.entry("fastworkbench", PerfModCategory.SERVER_TICK),
            Map.entry("fastfurnace", PerfModCategory.SERVER_TICK),
            // Memory
            Map.entry("ferritecore", PerfModCategory.MEMORY),
            Map.entry("memoryleakfix", PerfModCategory.MEMORY),
            Map.entry("saturn", PerfModCategory.MEMORY),
            // Chunk gen / loading
            Map.entry("c2me", PerfModCategory.CHUNK_GENERATION),
            Map.entry("chunky", PerfModCategory.CHUNK_GENERATION),
            Map.entry("fastload", PerfModCategory.CHUNK_GENERATION),
            Map.entry("betterbiomeblend", PerfModCategory.CHUNK_GENERATION),
            // Lighting
            Map.entry("starlight", PerfModCategory.LIGHTING),
            Map.entry("phosphor", PerfModCategory.LIGHTING),
            // Network
            Map.entry("krypton", PerfModCategory.NETWORK),
            Map.entry("connectivity", PerfModCategory.NETWORK),
            // Load time
            Map.entry("lazydfu", PerfModCategory.LOAD_TIME),
            Map.entry("smoothboot", PerfModCategory.LOAD_TIME),
            Map.entry("modernfix", PerfModCategory.LOAD_TIME),
            Map.entry("vintagefix", PerfModCategory.LOAD_TIME),
            // Profilers / diagnostics
            Map.entry("spark", PerfModCategory.PROFILER),
            Map.entry("observable", PerfModCategory.PROFILER),
            Map.entry("laggoggles", PerfModCategory.PROFILER),
            Map.entry("carpet", PerfModCategory.PROFILER)
    );

    public record DetectedMod(String id, String name, String version, PerfModCategory category) {}

    public static PerfModCategory categoryOf(String modId) {
        return KNOWN.get(modId);
    }

    public static List<DetectedMod> detect() {
        List<DetectedMod> found = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            PerfModCategory cat = KNOWN.get(info.getModId());
            if (cat != null) {
                found.add(new DetectedMod(info.getModId(), info.getDisplayName(),
                        info.getVersion().toString(), cat));
            }
        }
        return found;
    }

    public static Map<PerfModCategory, List<DetectedMod>> byCategory(List<DetectedMod> mods) {
        Map<PerfModCategory, List<DetectedMod>> map = new EnumMap<>(PerfModCategory.class);
        for (DetectedMod m : mods) {
            map.computeIfAbsent(m.category(), k -> new ArrayList<>()).add(m);
        }
        return map;
    }

    public static boolean hasProfiler(List<DetectedMod> mods) {
        return mods.stream().anyMatch(m -> m.category() == PerfModCategory.PROFILER);
    }

    /** True if any non-profiler optimization mod is present. */
    public static boolean hasOptimizers(List<DetectedMod> mods) {
        return mods.stream().anyMatch(m -> m.category() != PerfModCategory.PROFILER);
    }

    public static int nonSystemModCount() {
        return (int) ModList.get().getMods().stream()
                .filter(m -> !SYSTEM_MOD_IDS.contains(m.getModId()))
                .count();
    }
}
