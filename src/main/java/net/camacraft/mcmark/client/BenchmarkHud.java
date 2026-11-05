package net.camacraft.mcmark.client;

import net.camacraft.mcmark.MCMarkConfig;
import net.camacraft.mcmark.engine.BenchmarkEngine;
import net.camacraft.mcmark.engine.ProgressSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/** In-game progress overlay shown while a benchmark run is active. */
public class BenchmarkHud implements IGuiOverlay {
    private static final int PANEL_WIDTH = 180;
    private static long completeShownAt = -1;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!MCMarkConfig.CLIENT.showHud.get()) return;
        ProgressSnapshot p = BenchmarkEngine.INSTANCE.progress();

        if (p.status() == ProgressSnapshot.Status.COMPLETE) {
            if (completeShownAt < 0) completeShownAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - completeShownAt < 30_000) renderComplete(graphics, p);
            return;
        }
        completeShownAt = -1;
        if (!p.active()) return;

        Font font = Minecraft.getInstance().font;
        int x = 6, y = 6;
        int height = 46;
        graphics.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y + height, 0xA0101018);

        graphics.drawString(font, "MCMark — " + p.suite() + (p.standard() ? "" : " (non-standard)"),
                x + 2, y + 2, 0xFF55FFFF);
        graphics.drawString(font, String.format(Locale.ROOT, "%s (%d/%d)",
                p.phaseDisplay(), p.phaseIndex(), p.phaseCount()), x + 2, y + 13, 0xFFFFFFFF);

        int barY = y + 24;
        graphics.fill(x + 2, barY, x + PANEL_WIDTH - 2, barY + 5, 0xFF303040);
        int fill = (int) ((PANEL_WIDTH - 4) * Math.min(1.0, p.overallProgress()));
        graphics.fill(x + 2, barY, x + 2 + fill, barY + 5, 0xFF4FC3F7);

        graphics.drawString(font, String.format(Locale.ROOT, "MSPT %.1f   FPS %d   %.0f%%",
                        p.recentMspt(), ClientFrameMeter.fps(), p.overallProgress() * 100),
                x + 2, barY + 9, 0xFFB0B0C0);
    }

    private void renderComplete(GuiGraphics graphics, ProgressSnapshot p) {
        Font font = Minecraft.getInstance().font;
        int x = 6, y = 6;
        graphics.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y + 24, 0xA0101018);
        graphics.drawString(font, "MCMark complete!", x + 2, y + 2, 0xFF55FF55);
        graphics.drawString(font, String.format(Locale.ROOT, "Score: %.0f %s", p.finalScore(),
                p.standard() ? "✔" : "(non-standard)"), x + 2, y + 13, 0xFFFFFFFF);
    }
}
