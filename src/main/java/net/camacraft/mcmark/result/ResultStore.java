package net.camacraft.mcmark.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.camacraft.mcmark.MCMark;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Persists benchmark results as JSON under {@code <game dir>/mcmark/results/}. */
public final class ResultStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson CANONICAL_GSON = new Gson();
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private ResultStore() {}

    public static Path resultsDir() {
        return FMLPaths.GAMEDIR.get().resolve("mcmark").resolve("results");
    }

    /**
     * Computes the tamper-evidence hash over the canonical (non-pretty, empty-hash) form and
     * stores it in the result. This deters casual file edits; authoritative validation happens
     * server-side once the MCMark website is live.
     */
    public static void seal(BenchmarkResult result) {
        result.integrity.contentHash = "";
        result.integrity.contentHash = sha256(CANONICAL_GSON.toJson(result));
    }

    public static boolean verifySeal(BenchmarkResult result) {
        String stored = result.integrity.contentHash;
        result.integrity.contentHash = "";
        String computed = sha256(CANONICAL_GSON.toJson(result));
        result.integrity.contentHash = stored;
        return computed.equals(stored);
    }

    public static Path save(BenchmarkResult result) throws IOException {
        seal(result);
        Path dir = resultsDir();
        Files.createDirectories(dir);
        String shortId = result.resultId.length() >= 8 ? result.resultId.substring(0, 8) : result.resultId;
        Path file = dir.resolve(FILE_STAMP.format(Instant.parse(result.createdAt)) + "-" + shortId + ".json");
        String json = GSON.toJson(result);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("latest.json"), json, StandardCharsets.UTF_8);
        return file;
    }

    public record StoredResult(Path file, BenchmarkResult result) {}

    /** All stored results, newest first. Unreadable files are skipped with a log warning. */
    public static List<StoredResult> list() {
        List<StoredResult> out = new ArrayList<>();
        Path dir = resultsDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("latest.json"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .forEach(p -> {
                        BenchmarkResult r = load(p);
                        if (r != null) out.add(new StoredResult(p, r));
                    });
        } catch (IOException e) {
            MCMark.LOGGER.warn("Failed to list MCMark results", e);
        }
        return out;
    }

    public static BenchmarkResult load(Path file) {
        try {
            return CANONICAL_GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), BenchmarkResult.class);
        } catch (Exception e) {
            MCMark.LOGGER.warn("Failed to read MCMark result {}", file, e);
            return null;
        }
    }

    /** Renames the target label of the most recent result and re-seals it. */
    public static StoredResult relabelLatest(String newName) throws IOException {
        List<StoredResult> all = list();
        if (all.isEmpty()) return null;
        StoredResult latest = all.get(0);
        latest.result().target.name = newName;
        seal(latest.result());
        String json = GSON.toJson(latest.result());
        Files.writeString(latest.file(), json, StandardCharsets.UTF_8);
        Files.writeString(resultsDir().resolve("latest.json"), json, StandardCharsets.UTF_8);
        return latest;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
