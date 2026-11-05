package net.camacraft.mcmark.upload;

import com.google.gson.Gson;
import net.camacraft.mcmark.MCMark;
import net.camacraft.mcmark.MCMarkConfig;
import net.camacraft.mcmark.result.BenchmarkResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Client for the (future) MCMark leaderboard API.
 *
 * <p>The full request path is implemented and versioned now so the mod is ready the moment the
 * website launches — flipping {@code uploadEnabled} in the config (or a config update shipped
 * with a new mod release) is all that's needed. Until then every call short-circuits with a
 * friendly "not live yet" outcome and nothing ever leaves the machine.</p>
 */
public final class UploadClient {
    public static final String API_VERSION = "v1";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private UploadClient() {}

    public record UploadOutcome(boolean success, String message) {}

    public static boolean isLive() {
        return MCMarkConfig.COMMON.uploadEnabled.get();
    }

    public static String notLiveMessage() {
        return "The MCMark online leaderboard isn't live yet. Your result is saved locally and "
                + "will be ready to upload the moment it launches — keep an eye on the mod page!";
    }

    public static CompletableFuture<UploadOutcome> upload(BenchmarkResult result) {
        if (!isLive()) {
            return CompletableFuture.completedFuture(new UploadOutcome(false, notLiveMessage()));
        }
        if (!result.uploadEligible) {
            return CompletableFuture.completedFuture(new UploadOutcome(false,
                    "This result is not upload-eligible (non-standard run: " + result.flags + ")."));
        }
        String base = MCMarkConfig.COMMON.apiBaseUrl.get();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/" + API_VERSION + "/results"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("User-Agent", "MCMark/" + MCMark.version())
                .header("X-MCMark-Schema", String.valueOf(result.schemaVersion))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(result)))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        return new UploadOutcome(true, "Result uploaded successfully! Response: " + resp.body());
                    }
                    return new UploadOutcome(false, "Upload failed (HTTP " + resp.statusCode() + ").");
                })
                .exceptionally(e -> {
                    MCMark.LOGGER.warn("MCMark upload failed", e);
                    return new UploadOutcome(false, "Upload failed: " + e.getMessage());
                });
    }

    public static CompletableFuture<UploadOutcome> ping() {
        if (!isLive()) {
            return CompletableFuture.completedFuture(new UploadOutcome(false, notLiveMessage()));
        }
        String base = MCMarkConfig.COMMON.apiBaseUrl.get();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/" + API_VERSION + "/status"))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "MCMark/" + MCMark.version())
                .GET()
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 200
                        ? new UploadOutcome(true, "MCMark services are online.")
                        : new UploadOutcome(false, "MCMark services returned HTTP " + resp.statusCode() + "."))
                .exceptionally(e -> new UploadOutcome(false, "Could not reach MCMark services: " + e.getMessage()));
    }
}
