package net.camacraft.mcmark.client;

import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One-click creation of an official benchmark world: the spec seed, a normal-generation
 * overworld, creative/cheats, and benchmark gamerules. The world name prefix triggers
 * auto-start once it loads.
 */
public final class WorldCreator {
    private WorldCreator() {}

    public static void createBenchmarkWorld(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        String name = BenchmarkSpec.WORLD_NAME_PREFIX + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());

        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, null);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);

        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false,
                Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(BenchmarkSpec.WORLD_SEED, true, false);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
                WorldCreator::normalDimensions);
    }

    private static WorldDimensions normalDimensions(RegistryAccess registryAccess) {
        return registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
    }
}
