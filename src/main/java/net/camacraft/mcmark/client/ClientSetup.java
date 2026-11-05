package net.camacraft.mcmark.client;

import net.camacraft.mcmark.MCMark;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client setup: installs the client hook and registers the HUD overlay. */
@Mod.EventBusSubscriber(modid = MCMark.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        MCMark.setClientHook(new MCMarkClientHooks());
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("benchmark_hud", new BenchmarkHud());
    }
}
