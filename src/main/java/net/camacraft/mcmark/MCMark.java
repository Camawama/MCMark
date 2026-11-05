package net.camacraft.mcmark;

import com.mojang.logging.LogUtils;
import net.camacraft.mcmark.engine.BenchmarkEngine;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * MCMark — the community benchmark for Minecraft mods and modpacks.
 *
 * <p>Runs a deterministic, versioned benchmark suite and produces a comparable performance
 * score, with results stored locally as JSON, ready for the future MCMark leaderboard.</p>
 */
@Mod(MCMark.MODID)
public class MCMark {
    public static final String MODID = "mcmark";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Client-side capabilities, no-op on dedicated servers. Set by the client during setup. */
    private static ClientHook clientHook = ClientHook.NOOP;

    public MCMark(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, MCMarkConfig.COMMON_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, MCMarkConfig.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.register(BenchmarkEngine.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);

        LOGGER.info("MCMark {} initialized (spec {})", version(),
                net.camacraft.mcmark.spec.BenchmarkSpec.SPEC_VERSION);
    }

    public static ClientHook client() {
        return clientHook;
    }

    public static void setClientHook(ClientHook hook) {
        clientHook = hook;
    }

    public static String version() {
        return ModList.get().getModContainerById(MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("dev");
    }
}
