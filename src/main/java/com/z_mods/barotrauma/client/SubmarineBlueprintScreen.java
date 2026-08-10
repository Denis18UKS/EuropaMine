package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.BlueprintPackets;
import com.z_mods.barotrauma.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pixel editor: one lit cell becomes one block in the vertical submarine plane. */
public final class SubmarineBlueprintScreen extends Screen {
    public static final int GRID_W = 48;
    public static final int GRID_H = 24;
    private final boolean[] cells = new boolean[GRID_W * GRID_H];
    private int cellSize = 10;
    private int gridX;
    private int gridY;
    private boolean painting;
    private boolean paintValue;
    private String notice = "ЛКМ рисует, ПКМ стирает. 1 пиксель = 1 блок.";

    public SubmarineBlueprintScreen() {
        super(Component.literal("2D-конструктор подлодки"));
    }

    @Override
    protected void init() {
        cellSize = Math.max(5, Math.min(12, Math.min((width - 80) / GRID_W, (height - 120) / GRID_H)));
        gridX = (width - GRID_W * cellSize) / 2;
        gridY = Math.max(48, (height - GRID_H * cellSize) / 2 - 8);
        int y = Math.min(height - 28, gridY + GRID_H * cellSize + 14);
        addRenderableWidget(Button.builder(Component.literal("Шаблон"), b -> template()).bounds(gridX, y, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Очистить"), b -> java.util.Arrays.fill(cells, false)).bounds(gridX + 78, y, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Построить"), b -> build()).bounds(gridX + GRID_W * cellSize - 164, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose()).bounds(gridX + GRID_W * cellSize - 68, y, 68, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, "2D-КОНСТРУКТОР ПОДЛОДКИ", width / 2, 18, 0xFFF0E2AE);
        g.drawCenteredString(font, notice, width / 2, 32, 0xFFAFC8C0);
        for (int row = 0; row < GRID_H; row++) {
            for (int col = 0; col < GRID_W; col++) {
                int x = gridX + col * cellSize;
                int y = gridY + row * cellSize;
                int color = cells[row * GRID_W + col] ? 0xFF2D9B83 : 0xFF111817;
                g.fill(x, y, x + cellSize - 1, y + cellSize - 1, color);
                if (cellSize >= 8) g.renderOutline(x, y, cellSize, cellSize, 0xFF27433C);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 || button == 1) {
            int index = cellAt(mouseX, mouseY);
            if (index >= 0) {
                painting = true;
                paintValue = button == 0;
                cells[index] = paintValue;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (painting) {
            int index = cellAt(mouseX, mouseY);
            if (index >= 0) cells[index] = paintValue;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int cellAt(double mouseX, double mouseY) {
        int col = (int)((mouseX - gridX) / cellSize);
        int row = (int)((mouseY - gridY) / cellSize);
        return col >= 0 && col < GRID_W && row >= 0 && row < GRID_H ? row * GRID_W + col : -1;
    }

    private void template() {
        java.util.Arrays.fill(cells, false);
        int mid = GRID_H / 2;
        for (int x = 5; x < GRID_W - 5; x++) {
            int half = Math.max(2, 6 - Math.abs(x - GRID_W / 2) / 6);
            for (int y = mid - half; y <= mid + half; y++) cells[y * GRID_W + x] = true;
        }
        for (int x = GRID_W / 2 - 3; x <= GRID_W / 2 + 3; x++) cells[(mid - 7) * GRID_W + x] = true;
        notice = "Тестовый шаблон загружен. Его можно дорисовать.";
    }

    private void build() {
        byte[] data = new byte[cells.length];
        int count = 0;
        for (int i = 0; i < cells.length; i++) if (cells[i]) { data[i] = 1; count++; }
        if (count == 0) { notice = "Нарисуйте хотя бы один блок."; return; }
        ModNetworking.CHANNEL.sendToServer(new BlueprintPackets.ServerboundBuildBlueprint(GRID_W, GRID_H, data));
        notice = "Чертёж отправлен на строительство: " + count + " блоков.";
    }

    @Override public boolean isPauseScreen() { return false; }
}
