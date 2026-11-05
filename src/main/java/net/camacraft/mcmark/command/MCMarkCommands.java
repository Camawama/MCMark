package net.camacraft.mcmark.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.camacraft.mcmark.MCMark;
import net.camacraft.mcmark.MCMarkConfig;
import net.camacraft.mcmark.detect.PerfModDetector;
import net.camacraft.mcmark.engine.BenchmarkEngine;
import net.camacraft.mcmark.env.EnvironmentCapture;
import net.camacraft.mcmark.result.BenchmarkResult;
import net.camacraft.mcmark.result.ResultStore;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.camacraft.mcmark.spec.Suite;
import net.camacraft.mcmark.spec.TargetType;
import net.camacraft.mcmark.upload.UploadClient;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** The /mcmark command tree. */
public final class MCMarkCommands {
    private MCMarkCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcmark")
                .then(Commands.literal("run")
                        .requires(src -> src.hasPermission(2))
                        .executes(c -> run(c, Suite.STANDARD, null))
                        .then(suiteBranch(Suite.STANDARD))
                        .then(suiteBranch(Suite.QUICK)))
                .then(Commands.literal("abort")
                        .requires(src -> src.hasPermission(2))
                        .executes(MCMarkCommands::abort))
                .then(Commands.literal("status").executes(MCMarkCommands::status))
                .then(Commands.literal("results").executes(MCMarkCommands::results))
                .then(Commands.literal("label")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(MCMarkCommands::label)))
                .then(Commands.literal("upload")
                        .requires(src -> src.hasPermission(2))
                        .executes(MCMarkCommands::upload))
                .then(Commands.literal("mods").executes(MCMarkCommands::mods))
                .then(Commands.literal("env").executes(MCMarkCommands::env)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> suiteBranch(Suite suite) {
        return Commands.literal(suite.name().toLowerCase(Locale.ROOT))
                .executes(c -> run(c, suite, null))
                .then(Commands.literal("mod").executes(c -> run(c, suite, TargetType.MOD)))
                .then(Commands.literal("modpack").executes(c -> run(c, suite, TargetType.MODPACK)))
                .then(Commands.literal("baseline").executes(c -> run(c, suite, TargetType.BASELINE)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Suite suite, TargetType explicitType) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        TargetType type = explicitType != null ? explicitType
                : TargetType.guess(PerfModDetector.nonSystemModCount());
        String error = BenchmarkEngine.INSTANCE.start(src.getServer(), player, suite, type,
                MCMarkConfig.COMMON.defaultTargetName.get());
        if (error != null) {
            src.sendFailure(Component.literal(error));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("MCMark " + suite + " run started (target: "
                + type + ")."), true);
        return 1;
    }

    private static int abort(CommandContext<CommandSourceStack> ctx) {
        String error = BenchmarkEngine.INSTANCE.abort("requested by " + ctx.getSource().getTextName());
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("MCMark run aborted."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "MCMark " + MCMark.version() + " (spec " + BenchmarkSpec.SPEC_VERSION + ") — "
                        + BenchmarkEngine.INSTANCE.statusLine()), false);
        return 1;
    }

    private static int results(CommandContext<CommandSourceStack> ctx) {
        List<ResultStore.StoredResult> all = ResultStore.list();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No results yet. Run /mcmark run to benchmark this instance."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Recent MCMark results:")
                .withStyle(ChatFormatting.GOLD), false);
        all.stream().limit(5).forEach(stored -> {
            BenchmarkResult r = stored.result();
            String line = String.format(Locale.ROOT, "  %s — %s '%s' — score %.0f%s",
                    r.createdAt.substring(0, Math.min(10, r.createdAt.length())),
                    r.target.type, r.target.name, r.scores.overall,
                    r.standard ? " ✔" : " (non-standard)");
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        });
        return 1;
    }

    private static int label(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        try {
            ResultStore.StoredResult updated = ResultStore.relabelLatest(name);
            if (updated == null) {
                ctx.getSource().sendFailure(Component.literal("No results to label."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Latest result labeled '" + name + "'."), false);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Failed to update result: " + e.getMessage()));
            return 0;
        }
    }

    private static int upload(CommandContext<CommandSourceStack> ctx) {
        List<ResultStore.StoredResult> all = ResultStore.list();
        if (all.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No results to upload."));
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        UploadClient.upload(all.get(0).result()).thenAccept(outcome ->
                src.getServer().execute(() -> src.sendSuccess(() ->
                        Component.literal(outcome.message()).withStyle(
                                outcome.success() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false)));
        return 1;
    }

    private static int mods(CommandContext<CommandSourceStack> ctx) {
        var detected = PerfModDetector.detect();
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "Mods loaded: %d (excluding Minecraft/Forge/MCMark). Performance mods detected: %d",
                        PerfModDetector.nonSystemModCount(), detected.size()))
                .withStyle(ChatFormatting.GOLD), false);
        if (detected.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "  None — runs from this instance are classified PURE.").withStyle(ChatFormatting.GREEN), false);
        } else {
            PerfModDetector.byCategory(detected).forEach((cat, mods) -> {
                String names = String.join(", ", mods.stream().map(PerfModDetector.DetectedMod::id).toList());
                src.sendSuccess(() -> Component.literal("  " + cat.display + ": " + names)
                        .withStyle(ChatFormatting.GRAY), false);
            });
            src.sendSuccess(() -> Component.literal(
                            "  Runs are classified " + (PerfModDetector.hasOptimizers(detected) ? "OPTIMIZED" : "PURE")
                                    + " — leaderboards keep these categories separate.")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int env(CommandContext<CommandSourceStack> ctx) {
        BenchmarkResult.Environment env = EnvironmentCapture.capture(MCMark.client());
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("MCMark environment fingerprint:")
                .withStyle(ChatFormatting.GOLD), false);
        for (String line : new String[]{
                "OS: " + env.os + " (" + env.arch + ")",
                "Java: " + env.javaVersion + " (" + env.javaVendor + "), heap " + env.maxHeapMb + " MB",
                "CPU: " + env.cpuName + " — " + env.cpuThreads + " threads",
                "GPU: " + env.gpuRenderer,
                "Minecraft " + env.minecraftVersion + " / Forge " + env.forgeVersion}) {
            src.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
