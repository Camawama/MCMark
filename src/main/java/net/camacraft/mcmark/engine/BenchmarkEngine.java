package net.camacraft.mcmark.engine;

import net.camacraft.mcmark.MCMark;
import net.camacraft.mcmark.MCMarkConfig;
import net.camacraft.mcmark.detect.PerfModDetector;
import net.camacraft.mcmark.engine.phases.ChunkGenPhase;
import net.camacraft.mcmark.engine.phases.EntityStressPhase;
import net.camacraft.mcmark.engine.phases.FluidPhase;
import net.camacraft.mcmark.engine.phases.IdlePhase;
import net.camacraft.mcmark.engine.phases.ItemStressPhase;
import net.camacraft.mcmark.engine.phases.RedstonePhase;
import net.camacraft.mcmark.engine.phases.RenderPhase;
import net.camacraft.mcmark.engine.phases.WarmupPhase;
import net.camacraft.mcmark.env.EnvironmentCapture;
import net.camacraft.mcmark.result.BenchmarkResult;
import net.camacraft.mcmark.result.ResultStore;
import net.camacraft.mcmark.result.ScoreCalculator;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.camacraft.mcmark.spec.StandardFlag;
import net.camacraft.mcmark.spec.Suite;
import net.camacraft.mcmark.spec.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The benchmark run state machine. Lives on the server side, driven by server tick events.
 * PREPARING builds the arena over several ticks, RUNNING executes the phase list, FINISHING
 * tears everything down and restores world/player state, then the result is scored and saved.
 */
public final class BenchmarkEngine {
    public static final BenchmarkEngine INSTANCE = new BenchmarkEngine();

    private enum State { IDLE, PREPARING, RUNNING, FINISHING }

    private State state = State.IDLE;
    private MinecraftServer server;
    private ServerLevel level;
    private BenchmarkContext ctx;
    private Suite suite = Suite.STANDARD;
    private TargetType targetType = TargetType.MODPACK;
    private String targetName = "Unnamed";
    private Set<StandardFlag> flags = EnumSet.noneOf(StandardFlag.class);
    private List<String> videoDeviations = List.of();

    private final List<BenchmarkPhase> phases = new ArrayList<>();
    private final List<PhaseResult> results = new ArrayList<>();
    private int phaseIndex;
    private final Deque<Step> prepareQueue = new ArrayDeque<>();
    private final Deque<Step> teardownQueue = new ArrayDeque<>();

    private long tickStartNanos;
    private final ArrayDeque<Long> recentTicks = new ArrayDeque<>();
    private volatile ProgressSnapshot progress = ProgressSnapshot.IDLE_SNAPSHOT;

    // Snapshots for restoring the world/player after a run
    private GameRuleSnapshot ruleSnapshot;
    private PlayerSnapshot playerSnapshot;

    // Auto-start machinery for official benchmark worlds / headless automation
    private boolean benchmarkWorldArmed;
    private int autoStartCountdown = -1;
    private UUID autoStartPlayer;
    private Suite headlessSuite;

    private BenchmarkEngine() {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public ProgressSnapshot progress() {
        return progress;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public String statusLine() {
        ProgressSnapshot p = progress;
        return switch (p.status()) {
            case IDLE -> autoStartCountdown > 0
                    ? "Auto-start pending (" + (autoStartCountdown / 20) + "s). /mcmark abort to cancel."
                    : "Idle. Start a run with /mcmark run.";
            case COMPLETE -> String.format(Locale.ROOT, "Last run complete — score %.0f.", p.finalScore());
            default -> String.format(Locale.ROOT, "%s: %s (%d/%d) %.0f%% — overall %.0f%%",
                    p.status(), p.phaseDisplay(), p.phaseIndex(), p.phaseCount(),
                    p.phaseProgress() * 100, p.overallProgress() * 100);
        };
    }

    /** Starts a run. Returns an error message, or null on success. */
    @Nullable
    public String start(MinecraftServer server, @Nullable ServerPlayer player, Suite suite,
                        TargetType targetType, String targetName) {
        if (state != State.IDLE) return "A benchmark is already running. Use /mcmark abort first.";
        ServerLevel overworld = server.overworld();
        if (overworld == null) return "No overworld available.";

        this.server = server;
        this.level = overworld;
        this.suite = suite;
        this.targetType = targetType;
        this.targetName = targetName;
        this.autoStartCountdown = -1;

        flags = EnumSet.noneOf(StandardFlag.class);
        if (suite == Suite.QUICK) flags.add(StandardFlag.QUICK_SUITE);
        if (level.getSeed() != BenchmarkSpec.WORLD_SEED) flags.add(StandardFlag.NON_STANDARD_SEED);
        if (server.isDedicatedServer()) flags.add(StandardFlag.DEDICATED_SERVER);
        if (server.getPlayerList().getPlayerCount() > 1) flags.add(StandardFlag.MULTIPLE_PLAYERS);
        if (PerfModDetector.hasProfiler(PerfModDetector.detect())) flags.add(StandardFlag.PROFILER_ACTIVE);

        boolean clientAttached = MCMark.client().hasClient() && player != null;
        if (!clientAttached) {
            flags.add(StandardFlag.NO_CLIENT_PHASES);
            videoDeviations = List.of();
        } else {
            videoDeviations = MCMark.client().videoSettingsDeviations();
        }

        ctx = new BenchmarkContext(server, level, suite, flags, MCMark.client(),
                player == null ? null : player.getUUID());

        ruleSnapshot = GameRuleSnapshot.capture(server, level);
        playerSnapshot = player == null ? null : PlayerSnapshot.capture(player);

        results.clear();
        phases.clear();
        phases.add(new WarmupPhase());
        phases.add(new IdlePhase());
        phases.add(new ChunkGenPhase());
        phases.add(new EntityStressPhase());
        phases.add(new ItemStressPhase());
        phases.add(new RedstonePhase());
        phases.add(new FluidPhase());
        if (clientAttached) phases.add(new RenderPhase());
        phaseIndex = 0;

        prepareQueue.clear();
        prepareQueue.add(Step.once(() -> {
            ruleSnapshot.applyStandard(ctx);
            ArenaBuilder.discardNonPlayers(level);
        }));
        prepareQueue.add(ArenaBuilder.forceAndLoadChunks(level));
        prepareQueue.addAll(ArenaBuilder.buildArenaSteps(level));
        prepareQueue.add(Step.once(() -> {
            ServerPlayer p = ctx.player();
            if (p != null) {
                p.setGameMode(GameType.SPECTATOR);
                p.teleportTo(level, BenchmarkSpec.ARENA_CENTER.getX() + 0.5, 201.5,
                        BenchmarkSpec.ARENA_CENTER.getZ() + 0.5, 0f, 30f);
            }
        }));

        state = State.PREPARING;
        recentTicks.clear();
        updateProgress(null);
        message(Component.literal("MCMark " + MCMark.version() + " (spec " + BenchmarkSpec.SPEC_VERSION + ") — starting "
                        + suite + " run. Expect ~" + (suite == Suite.STANDARD ? "5" : "2")
                        + " minutes; avoid opening menus during the render phase.")
                .withStyle(ChatFormatting.AQUA));
        MCMark.LOGGER.info("MCMark run started: suite={}, target={} '{}', flags={}", suite, targetType, targetName, flags);
        return null;
    }

    /** Aborts the active run (or cancels a pending auto-start). Returns an error or null. */
    @Nullable
    public String abort(String reason) {
        if (autoStartCountdown > 0) {
            autoStartCountdown = -1;
            benchmarkWorldArmed = false;
            return null;
        }
        if (state == State.IDLE) return "No benchmark is running.";
        MCMark.LOGGER.info("MCMark run aborted: {}", reason);
        if (state == State.RUNNING && phaseIndex < phases.size()) {
            try {
                phases.get(phaseIndex).abort(ctx);
            } catch (Exception e) {
                MCMark.LOGGER.warn("Phase abort cleanup failed", e);
            }
        }
        buildTeardown();
        drainTeardownNow();
        message(Component.literal("MCMark run aborted (" + reason + "). World state restored.")
                .withStyle(ChatFormatting.YELLOW));
        resetToIdle();
        return null;
    }

    public void onServerStarted(MinecraftServer server, boolean isBenchmarkWorld) {
        this.benchmarkWorldArmed = isBenchmarkWorld;
    }

    public void onPlayerLoggedIn(ServerPlayer player) {
        if (benchmarkWorldArmed && state == State.IDLE && autoStartCountdown < 0
                && MCMarkConfig.COMMON.autoStartInBenchmarkWorlds.get()
                && player.server.getPlayerList().getPlayerCount() == 1) {
            autoStartPlayer = player.getUUID();
            autoStartCountdown = BenchmarkSpec.AUTOSTART_DELAY_TICKS;
            player.sendSystemMessage(Component.literal(
                            "Official MCMark benchmark world detected — standard run starts in "
                                    + (BenchmarkSpec.AUTOSTART_DELAY_TICKS / 20) + "s. Run /mcmark abort to cancel.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** Schedules a run with no player attached (CI / dedicated automation). */
    public void scheduleHeadlessStart(Suite suite) {
        if (state != State.IDLE || autoStartCountdown > 0) return;
        headlessSuite = suite;
        autoStartPlayer = null;
        autoStartCountdown = BenchmarkSpec.AUTOSTART_DELAY_TICKS;
    }

    public void onServerStopping() {
        if (state != State.IDLE) {
            MCMark.LOGGER.warn("Server stopping mid-benchmark; restoring world state.");
            try {
                if (state == State.RUNNING && phaseIndex < phases.size()) phases.get(phaseIndex).abort(ctx);
                buildTeardown();
                drainTeardownNow();
            } catch (Exception e) {
                MCMark.LOGGER.error("Teardown during shutdown failed", e);
            }
        }
        resetToIdle();
        benchmarkWorldArmed = false;
        autoStartCountdown = -1;
        server = null;
        level = null;
        ctx = null;
    }

    // ------------------------------------------------------------------
    // Tick driver
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartNanos = System.nanoTime();
            return;
        }
        long tickNanos = System.nanoTime() - tickStartNanos;
        tickAutoStart();

        if (state == State.IDLE) return;
        recordRecentTick(tickNanos);

        try {
            switch (state) {
                case PREPARING -> {
                    Step step = prepareQueue.peek();
                    if (step == null || step.tick()) prepareQueue.poll();
                    if (prepareQueue.isEmpty()) {
                        state = State.RUNNING;
                        phaseIndex = 0;
                        beginPhase();
                    }
                }
                case RUNNING -> {
                    BenchmarkPhase current = phases.get(phaseIndex);
                    if (current.tickEnd(ctx, tickNanos)) {
                        results.add(current.result(ctx));
                        phaseIndex++;
                        if (phaseIndex < phases.size()) {
                            beginPhase();
                        } else {
                            state = State.FINISHING;
                            buildTeardown();
                        }
                    }
                }
                case FINISHING -> {
                    Step step = teardownQueue.peek();
                    if (step == null || step.tick()) teardownQueue.poll();
                    if (teardownQueue.isEmpty()) finalizeRun();
                }
                default -> {}
            }
            updateProgress(state == State.RUNNING ? phases.get(Math.min(phaseIndex, phases.size() - 1)) : null);
        } catch (Exception e) {
            MCMark.LOGGER.error("MCMark engine error — aborting run", e);
            try {
                buildTeardown();
                drainTeardownNow();
            } catch (Exception e2) {
                MCMark.LOGGER.error("Teardown after engine error failed", e2);
            }
            message(Component.literal("MCMark run failed with an internal error (see log). World restored.")
                    .withStyle(ChatFormatting.RED));
            resetToIdle();
        }
    }

    private void tickAutoStart() {
        if (autoStartCountdown <= 0 || state != State.IDLE || server == null) {
            if (autoStartCountdown > 0 && server == null) autoStartCountdown = -1;
            return;
        }
        autoStartCountdown--;
        if (autoStartCountdown == 0) {
            autoStartCountdown = -1;
            ServerPlayer player = autoStartPlayer == null ? null
                    : server.getPlayerList().getPlayer(autoStartPlayer);
            Suite chosenSuite = headlessSuite != null ? headlessSuite : Suite.STANDARD;
            headlessSuite = null;
            TargetType guessed = TargetType.guess(PerfModDetector.nonSystemModCount());
            String error = start(server, player, chosenSuite, guessed,
                    MCMarkConfig.COMMON.defaultTargetName.get());
            if (error != null) MCMark.LOGGER.warn("Auto-start failed: {}", error);
        }
    }

    /** Called by ServerEvents so the engine knows which server instance ticks belong to. */
    public void attachServer(MinecraftServer server) {
        this.server = server;
    }

    private void beginPhase() {
        ctx.reseed(phaseIndex);
        BenchmarkPhase phase = phases.get(phaseIndex);
        MCMark.LOGGER.info("MCMark phase {}/{}: {}", phaseIndex + 1, phases.size(), phase.id());
        phase.begin(ctx);
    }

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    private void buildTeardown() {
        teardownQueue.clear();
        teardownQueue.addAll(ArenaBuilder.clearAllSteps(level));
        teardownQueue.add(Step.once(() -> ArenaBuilder.unforceChunks(level)));
        teardownQueue.add(Step.once(() -> {
            ruleSnapshot.restore(ctx);
            if (playerSnapshot != null) {
                ServerPlayer p = ctx.player();
                if (p != null) playerSnapshot.restore(server, p);
            }
        }));
    }

    private void drainTeardownNow() {
        int safety = 1_000_000;
        while (!teardownQueue.isEmpty() && safety-- > 0) {
            Step step = teardownQueue.peek();
            if (step == null || step.tick()) teardownQueue.poll();
        }
        teardownQueue.clear();
    }

    private void finalizeRun() {
        List<PhaseResult> scored = results.stream().filter(r -> r.scored).toList();
        ScoreCalculator.Totals totals = ScoreCalculator.compute(new ArrayList<>(scored));

        BenchmarkResult result = new BenchmarkResult();
        result.schemaVersion = BenchmarkSpec.SCHEMA_VERSION;
        result.specVersion = BenchmarkSpec.SPEC_VERSION;
        result.modVersion = MCMark.version();
        result.resultId = UUID.randomUUID().toString();
        result.createdAt = Instant.now().toString();
        result.suite = suite.name();
        result.target.type = targetType.name();
        result.target.name = targetName;
        result.scores.overall = round1(totals.overall);
        result.scores.server = round1(totals.server);
        result.scores.client = totals.client == null ? null : round1(totals.client);

        for (PhaseResult r : scored) {
            BenchmarkResult.PhaseScore ps = new BenchmarkResult.PhaseScore();
            ps.id = r.id;
            ps.display = r.display;
            ps.metric = r.metricName;
            ps.unit = r.unit;
            ps.lowerIsBetter = r.lowerIsBetter;
            ps.value = round3(r.primary);
            ps.score = round1(r.score);
            r.stats.forEach((k, v) -> ps.stats.put(k, round3(v)));
            result.phases.add(ps);
        }

        List<StandardFlag> sortedFlags = new ArrayList<>(flags);
        sortedFlags.sort(Comparator.comparing(Enum::name));
        for (StandardFlag f : sortedFlags) {
            result.flags.add(f.name());
            result.flagDescriptions.add(f.name() + ": " + f.description);
        }
        result.standard = flags.stream().noneMatch(f -> f.breaksStandard);
        result.uploadEligible = result.standard && suite == Suite.STANDARD;
        result.videoDeviations = new ArrayList<>(videoDeviations);

        result.environment = EnvironmentCapture.capture(MCMark.client());
        result.mods = EnvironmentCapture.captureMods();

        var detected = PerfModDetector.detect();
        result.perfMods.optimizersPresent = PerfModDetector.hasOptimizers(detected);
        result.perfMods.profilersPresent = PerfModDetector.hasProfiler(detected);
        result.perfMods.classification = result.perfMods.optimizersPresent ? "OPTIMIZED" : "PURE";
        PerfModDetector.byCategory(detected).forEach((cat, mods) ->
                result.perfMods.byCategory.put(cat.name(),
                        mods.stream().map(PerfModDetector.DetectedMod::id).toList()));

        Path saved = null;
        try {
            saved = ResultStore.save(result);
        } catch (IOException e) {
            MCMark.LOGGER.error("Failed to save MCMark result", e);
        }

        report(result, saved);
        progress = new ProgressSnapshot(ProgressSnapshot.Status.COMPLETE, suite.name(), "Complete",
                phases.size(), phases.size(), 1.0, 1.0, 0, result.scores.overall, result.standard);
        state = State.IDLE;
        MCMark.LOGGER.info("MCMark run complete: overall={} server={} client={} standard={}",
                result.scores.overall, result.scores.server, result.scores.client, result.standard);
    }

    private void report(BenchmarkResult result, @Nullable Path savedFile) {
        message(Component.literal("========== MCMark Result ==========").withStyle(ChatFormatting.GOLD));
        MutableComponent scoreLine = Component.literal(String.format(Locale.ROOT, "Overall Score: %.0f", result.scores.overall))
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        message(scoreLine);
        message(Component.literal(String.format(Locale.ROOT, "  Server: %.0f%s", result.scores.server,
                        result.scores.client != null ? String.format(Locale.ROOT, "   Client: %.0f", result.scores.client) : "   Client: —"))
                .withStyle(ChatFormatting.GRAY));
        for (BenchmarkResult.PhaseScore ps : result.phases) {
            message(Component.literal(String.format(Locale.ROOT, "  %-14s %8.2f %-8s → %.0f",
                    ps.display, ps.value, ps.unit, ps.score)).withStyle(ChatFormatting.GRAY));
        }
        if (result.standard) {
            message(Component.literal("Standard run ✔ — eligible for the MCMark leaderboard.")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            message(Component.literal("Non-standard run — flags: " + String.join(", ", result.flags))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (savedFile != null) {
            message(Component.literal("Saved to " + ResultStore.resultsDir().getFileName() + "/"
                    + savedFile.getFileName()).withStyle(ChatFormatting.DARK_GRAY));
        }
        message(Component.literal("Label this result: /mcmark label <name> · Upload: /mcmark upload")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    private void resetToIdle() {
        state = State.IDLE;
        progress = ProgressSnapshot.IDLE_SNAPSHOT;
        prepareQueue.clear();
        teardownQueue.clear();
        results.clear();
        phases.clear();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void recordRecentTick(long nanos) {
        recentTicks.addLast(nanos);
        while (recentTicks.size() > 40) recentTicks.removeFirst();
    }

    private double recentMedianMspt() {
        if (recentTicks.isEmpty()) return 0;
        long[] copy = recentTicks.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(copy);
        return copy[copy.length / 2] / 1.0e6;
    }

    private void updateProgress(@Nullable BenchmarkPhase current) {
        int count = Math.max(1, phases.size());
        double phaseProgress;
        String phaseDisplay;
        int shownIndex;
        switch (state) {
            case PREPARING -> {
                phaseDisplay = "Preparing arena";
                phaseProgress = 0;
                shownIndex = 0;
            }
            case FINISHING -> {
                phaseDisplay = "Restoring world";
                phaseProgress = 1;
                shownIndex = count;
            }
            case RUNNING -> {
                phaseDisplay = current != null ? current.display() : "";
                phaseProgress = current != null ? current.progressFraction(ctx) : 0;
                shownIndex = phaseIndex + 1;
            }
            default -> {
                return;
            }
        }
        double overall = state == State.RUNNING ? (phaseIndex + phaseProgress) / count
                : state == State.PREPARING ? 0 : 1;
        progress = new ProgressSnapshot(
                switch (state) {
                    case PREPARING -> ProgressSnapshot.Status.PREPARING;
                    case FINISHING -> ProgressSnapshot.Status.FINISHING;
                    default -> ProgressSnapshot.Status.RUNNING;
                },
                suite.name(), phaseDisplay, shownIndex, count, phaseProgress, overall,
                recentMedianMspt(), 0, flags.stream().noneMatch(f -> f.breaksStandard));
    }

    private void message(Component component) {
        ServerPlayer p = ctx != null ? ctx.player() : null;
        if (p != null) {
            p.sendSystemMessage(component);
        } else {
            MCMark.LOGGER.info(component.getString());
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ------------------------------------------------------------------
    // Snapshots
    // ------------------------------------------------------------------

    private record GameRuleSnapshot(boolean daylight, boolean weather, boolean mobSpawning,
                                    boolean traderSpawning, boolean patrolSpawning, boolean insomnia,
                                    boolean fireTick, boolean mobGriefing, int randomTickSpeed,
                                    long dayTime, Difficulty difficulty) {

        static GameRuleSnapshot capture(MinecraftServer server, ServerLevel level) {
            GameRules gr = level.getGameRules();
            return new GameRuleSnapshot(
                    gr.getRule(GameRules.RULE_DAYLIGHT).get(),
                    gr.getRule(GameRules.RULE_WEATHER_CYCLE).get(),
                    gr.getRule(GameRules.RULE_DOMOBSPAWNING).get(),
                    gr.getRule(GameRules.RULE_DO_TRADER_SPAWNING).get(),
                    gr.getRule(GameRules.RULE_DO_PATROL_SPAWNING).get(),
                    gr.getRule(GameRules.RULE_DOINSOMNIA).get(),
                    gr.getRule(GameRules.RULE_DOFIRETICK).get(),
                    gr.getRule(GameRules.RULE_MOBGRIEFING).get(),
                    gr.getRule(GameRules.RULE_RANDOMTICKING).get(),
                    level.getDayTime(),
                    server.getWorldData().getDifficulty());
        }

        void applyStandard(BenchmarkContext ctx) {
            ctx.runCommand("gamerule doDaylightCycle false");
            ctx.runCommand("gamerule doWeatherCycle false");
            ctx.runCommand("gamerule doMobSpawning false");
            ctx.runCommand("gamerule doTraderSpawning false");
            ctx.runCommand("gamerule doPatrolSpawning false");
            ctx.runCommand("gamerule doInsomnia false");
            ctx.runCommand("gamerule doFireTick true");
            ctx.runCommand("gamerule mobGriefing true");
            ctx.runCommand("gamerule randomTickSpeed 3");
            ctx.level.setDayTime(6000);
            ctx.level.setWeatherParameters(1_000_000, 0, false, false);
            ctx.server.setDifficulty(Difficulty.NORMAL, true);
        }

        void restore(BenchmarkContext ctx) {
            ctx.runCommand("gamerule doDaylightCycle " + daylight);
            ctx.runCommand("gamerule doWeatherCycle " + weather);
            ctx.runCommand("gamerule doMobSpawning " + mobSpawning);
            ctx.runCommand("gamerule doTraderSpawning " + traderSpawning);
            ctx.runCommand("gamerule doPatrolSpawning " + patrolSpawning);
            ctx.runCommand("gamerule doInsomnia " + insomnia);
            ctx.runCommand("gamerule doFireTick " + fireTick);
            ctx.runCommand("gamerule mobGriefing " + mobGriefing);
            ctx.runCommand("gamerule randomTickSpeed " + randomTickSpeed);
            ctx.level.setDayTime(dayTime);
            ctx.server.setDifficulty(difficulty, true);
        }
    }

    private record PlayerSnapshot(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot,
                                  GameType gameType) {

        static PlayerSnapshot capture(ServerPlayer player) {
            return new PlayerSnapshot(player.level().dimension(), player.position(),
                    player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
        }

        void restore(MinecraftServer server, ServerPlayer player) {
            ServerLevel target = server.getLevel(dimension);
            if (target == null) target = server.overworld();
            player.teleportTo(target, pos.x, pos.y, pos.z, yRot, xRot);
            player.setGameMode(gameType);
        }
    }
}
