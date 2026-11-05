package net.camacraft.mcmark.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The serialized form of a completed benchmark run. This class IS the upload payload schema
 * (see docs/SPEC.md); field names are part of the public contract with the future MCMark website.
 * Shape changes require bumping {@code schemaVersion}.
 */
public class BenchmarkResult {
    public int schemaVersion;
    public String specVersion;
    public String modVersion;
    public String resultId;
    public String createdAt;

    public String suite;
    public Target target = new Target();

    public Scores scores = new Scores();
    public List<PhaseScore> phases = new ArrayList<>();

    public boolean standard;
    public boolean uploadEligible;
    public List<String> flags = new ArrayList<>();
    public List<String> flagDescriptions = new ArrayList<>();
    public List<String> videoDeviations = new ArrayList<>();

    public Environment environment = new Environment();
    public List<ModEntry> mods = new ArrayList<>();
    public PerfSummary perfMods = new PerfSummary();

    public Integrity integrity = new Integrity();

    public static class Target {
        public String type;
        public String name;
    }

    public static class Scores {
        public double overall;
        public double server;
        /** Null when no client phases ran (dedicated server / headless run). */
        public Double client;
    }

    public static class PhaseScore {
        public String id;
        public String display;
        public String metric;
        public String unit;
        public boolean lowerIsBetter;
        public double value;
        public double score;
        public Map<String, Double> stats = new LinkedHashMap<>();
    }

    public static class Environment {
        public String os;
        public String arch;
        public String javaVersion;
        public String javaVendor;
        public List<String> jvmArgs = new ArrayList<>();
        public int cpuThreads;
        public long maxHeapMb;
        public String minecraftVersion;
        public String forgeVersion;
        public String dist;
        public String cpuName;
        public String gpuRenderer;
        public String gpuVendor;
        public String openGlVersion;
        public Map<String, String> clientSettings = new LinkedHashMap<>();
    }

    public static class ModEntry {
        public String id;
        public String name;
        public String version;
        /** Set only for recognized performance mods. */
        public String perfCategory;
    }

    public static class PerfSummary {
        public boolean optimizersPresent;
        public boolean profilersPresent;
        /** "PURE" (no optimizers) or "OPTIMIZED" — the leaderboard category axis. */
        public String classification;
        public Map<String, List<String>> byCategory = new LinkedHashMap<>();
    }

    public static class Integrity {
        public String algorithm = "SHA-256";
        public String contentHash = "";
    }
}
