package net.camacraft.mcmark.client;

import net.camacraft.mcmark.MCMark;
import net.camacraft.mcmark.MCMarkConfig;
import net.camacraft.mcmark.client.screen.MCMarkMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events: title-screen button and per-frame hooks. */
@Mod.EventBusSubscriber(modid = MCMark.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;
        if (!MCMarkConfig.CLIENT.titleScreenButton.get()) return;
        Button button = Button.builder(Component.translatable("mcmark.button.title"),
                        b -> Minecraft.getInstance().setScreen(new MCMarkMenuScreen(screen)))
                .bounds(screen.width - 64, 4, 60, 20)
                .tooltip(Tooltip.create(Component.translatable("mcmark.button.title.tooltip")))
                .build();
        event.addListener(button);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ClientFrameMeter.onFrame();
        RenderTestController.onFrame(Minecraft.getInstance());
    }
}
