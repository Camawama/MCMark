package net.camacraft.mcmark;

import net.camacraft.mcmark.command.MCMarkCommands;
import net.camacraft.mcmark.engine.BenchmarkEngine;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge-bus lifecycle events: command registration, benchmark-world detection, auto-start. */
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MCMarkCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        BenchmarkEngine.INSTANCE.attachServer(server);
        boolean benchmarkWorld = server.overworld() != null
                && server.overworld().getSeed() == BenchmarkSpec.WORLD_SEED
                && server.getWorldData().getLevelName().startsWith(BenchmarkSpec.WORLD_NAME_PREFIX);
        BenchmarkEngine.INSTANCE.onServerStarted(server, benchmarkWorld);
        if (benchmarkWorld) {
            MCMark.LOGGER.info("Official MCMark benchmark world detected: '{}'",
                    server.getWorldData().getLevelName());
        }

        // Headless automation (CI, dedicated servers): -Dmcmark.autorun=standard|quick
        // or env MCMARK_AUTORUN. Starts a run shortly after server start, no player needed.
        String autorun = System.getProperty("mcmark.autorun", System.getenv("MCMARK_AUTORUN"));
        if (autorun != null && !autorun.isBlank()) {
            MCMark.LOGGER.info("MCMark autorun requested: {}", autorun);
            BenchmarkEngine.INSTANCE.scheduleHeadlessStart(
                    net.camacraft.mcmark.spec.Suite.byName(autorun));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BenchmarkEngine.INSTANCE.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BenchmarkEngine.INSTANCE.onServerStopping();
    }
}
