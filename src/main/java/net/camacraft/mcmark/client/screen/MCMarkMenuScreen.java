package net.camacraft.mcmark.client.screen;

import net.camacraft.mcmark.MCMark;
import net.camacraft.mcmark.client.WorldCreator;
import net.camacraft.mcmark.spec.BenchmarkSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Main MCMark menu, opened from the title screen. */
public class MCMarkMenuScreen extends Screen {
    private final Screen parent;

    public MCMarkMenuScreen(Screen parent) {
        super(Component.translatable("mcmark.screen.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 24;

        addRenderableWidget(Button.builder(Component.translatable("mcmark.screen.menu.create_world"),
                        b -> WorldCreator.createBenchmarkWorld(this))
                .bounds(x, y, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("mcmark.screen.menu.create_world.tooltip")))
                .build());

        addRenderableWidget(Button.builder(Component.translatable("mcmark.screen.menu.results"),
                        b -> this.minecraft.setScreen(new ResultsScreen(this)))
                .bounds(x, y + 24, 200, 20)
                .build());

        Button leaderboard = Button.builder(Component.translatable("mcmark.screen.menu.leaderboard"), b -> {})
                .bounds(x, y + 48, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("mcmark.screen.menu.leaderboard.tooltip")))
                .build();
        leaderboard.active = false;
        addRenderableWidget(leaderboard);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, y + 84, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcmark.screen.menu.subtitle", MCMark.version(), BenchmarkSpec.SPEC_VERSION),
                this.width / 2, this.height / 4 - 6, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
