package net.camacraft.mcmark.client.screen;

import net.camacraft.mcmark.result.BenchmarkResult;
import net.camacraft.mcmark.result.ResultStore;
import net.camacraft.mcmark.upload.UploadClient;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/** Local results browser: list of stored runs with details and (future) upload. */
public class ResultsScreen extends Screen {
    private final Screen parent;
    private ResultList list;
    private String status = "";

    public ResultsScreen(Screen parent) {
        super(Component.translatable("mcmark.screen.results.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new ResultList(this.minecraft, this.width, this.height, 32, this.height - 92);
        addRenderableWidget(list);

        int y = this.height - 52;
        addRenderableWidget(Button.builder(Component.translatable("mcmark.screen.results.upload"), b -> upload())
                .bounds(this.width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mcmark.screen.results.folder"), b -> openFolder())
                .bounds(this.width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 + 54, y, 100, 20).build());
    }

    private void upload() {
        ResultList.ResultEntry selected = list.getSelected();
        if (selected == null && !list.children().isEmpty()) selected = list.children().get(0);
        if (selected == null) {
            status = Component.translatable("mcmark.screen.results.none").getString();
            return;
        }
        status = "...";
        UploadClient.upload(selected.stored.result()).thenAccept(outcome ->
                this.minecraft.execute(() -> status = outcome.message()));
    }

    private void openFolder() {
        try {
            Files.createDirectories(ResultStore.resultsDir());
            Util.getPlatform().openFile(ResultStore.resultsDir().toFile());
        } catch (IOException ignored) {
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        ResultList.ResultEntry selected = list.getSelected();
        int y = this.height - 86;
        if (selected != null) {
            BenchmarkResult r = selected.stored.result();
            String scores = String.format(Locale.ROOT, "Overall %.0f · Server %.0f · Client %s",
                    r.scores.overall, r.scores.server,
                    r.scores.client == null ? "—" : String.format(Locale.ROOT, "%.0f", r.scores.client));
            String meta = r.suite + " · " + r.target.type + " '" + r.target.name + "' · "
                    + (r.standard ? "standard ✔" : "flags: " + String.join(", ", r.flags));
            graphics.drawCenteredString(this.font, scores, this.width / 2, y, 0x55FF55);
            graphics.drawCenteredString(this.font, meta, this.width / 2, y + 12, 0xA0A0A0);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("mcmark.screen.results.hint"), this.width / 2, y, 0xA0A0A0);
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height - 26, 0xFFFF55);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    class ResultList extends ObjectSelectionList<ResultList.ResultEntry> {
        ResultList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 24);
            List<ResultStore.StoredResult> stored = ResultStore.list();
            for (ResultStore.StoredResult s : stored) {
                addEntry(new ResultEntry(s));
            }
            if (!children().isEmpty()) setSelected(children().get(0));
        }

        @Override
        public int getRowWidth() {
            return Math.min(340, this.width - 30);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + getRowWidth() / 2 + 6;
        }

        class ResultEntry extends ObjectSelectionList.Entry<ResultEntry> {
            final ResultStore.StoredResult stored;

            ResultEntry(ResultStore.StoredResult stored) {
                this.stored = stored;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                BenchmarkResult r = stored.result();
                String date = r.createdAt.length() >= 16 ? r.createdAt.substring(0, 16).replace('T', ' ') : r.createdAt;
                String line1 = String.format(Locale.ROOT, "%.0f — %s", r.scores.overall, r.target.name);
                String line2 = date + " · " + r.target.type + (r.standard ? " · ✔" : " · non-standard");
                graphics.drawString(ResultsScreen.this.font, line1, left + 4, top + 2, 0xFFFFFF);
                graphics.drawString(ResultsScreen.this.font, line2, left + 4, top + 13, 0x808080);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                ResultList.this.setSelected(this);
                return true;
            }

            @Override
            public Component getNarration() {
                BenchmarkResult r = stored.result();
                return Component.literal(r.target.name + " " + r.scores.overall);
            }
        }
    }
}
