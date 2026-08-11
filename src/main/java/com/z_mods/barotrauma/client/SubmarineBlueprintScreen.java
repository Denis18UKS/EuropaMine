package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.BlueprintPackets;
import com.z_mods.barotrauma.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Pixel editor: one lit cell becomes one block in the vertical submarine plane. */
public final class SubmarineBlueprintScreen extends Screen {
    public static final int GRID_W = 48;
    public static final int GRID_H = 24;
    private static final int SAVE_MAGIC = 0x45554250; // EUBP
    private static final int SAVE_VERSION = 1;
    private static final Path SAVE_PATH = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve("barotrauma_submarine_blueprint.bin");

    private final boolean[] cells = new boolean[GRID_W * GRID_H];
    private int cellSize = 10;
    private int gridX;
    private int gridY;
    private boolean painting;
    private boolean paintValue;
    private boolean initialProjectLoaded;
    private String notice = "ЛКМ рисует, ПКМ стирает. 1 пиксель = 1 блок.";

    public SubmarineBlueprintScreen() {
        super(Component.literal("2D-конструктор подлодки"));
    }

    @Override
    protected void init() {
        // Reserve enough room for two independent rows of controls. Previously all buttons were
        // forced into one row and overlapped as soon as the grid had to shrink to a 5px cell size.
        cellSize = Math.max(5, Math.min(12, Math.min((width - 40) / GRID_W, (height - 168) / GRID_H)));
        gridX = (width - GRID_W * cellSize) / 2;
        gridY = Math.max(44, (height - GRID_H * cellSize - 72) / 2);

        int gridBottom = gridY + GRID_H * cellSize;
        int firstRowY = Math.min(height - 52, gridBottom + 8);
        int secondRowY = Math.min(height - 28, firstRowY + 24);

        int firstButtonW = Math.min(70, Math.max(54, (width - 34) / 4));
        int firstGap = 6;
        int firstTotal = firstButtonW * 4 + firstGap * 3;
        int firstX = (width - firstTotal) / 2;
        addRenderableWidget(Button.builder(Component.literal("Шаблон"), b -> template())
                .bounds(firstX, firstRowY, firstButtonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Очистить"), b -> clearBlueprint())
                .bounds(firstX + (firstButtonW + firstGap), firstRowY, firstButtonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> saveProject())
                .bounds(firstX + (firstButtonW + firstGap) * 2, firstRowY, firstButtonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Загрузить"), b -> loadProject(true))
                .bounds(firstX + (firstButtonW + firstGap) * 3, firstRowY, firstButtonW, 20).build());

        int buildW = 112;
        int closeW = 82;
        int secondGap = 8;
        int secondTotal = buildW + closeW + secondGap;
        int secondX = (width - secondTotal) / 2;
        addRenderableWidget(Button.builder(Component.literal("ПОСТРОИТЬ"), b -> build())
                .bounds(secondX, secondRowY, buildW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(secondX + buildW + secondGap, secondRowY, closeW, 20).build());

        if (!initialProjectLoaded) {
            initialProjectLoaded = true;
            if (Files.exists(SAVE_PATH)) loadProject(false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, "2D-КОНСТРУКТОР ПОДЛОДКИ", width / 2, 16, 0xFFF0E2AE);
        g.drawCenteredString(font, notice, width / 2, 29, 0xFFAFC8C0);
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
        if (mouseX < gridX || mouseY < gridY
                || mouseX >= gridX + GRID_W * cellSize || mouseY >= gridY + GRID_H * cellSize) return -1;
        int col = (int)((mouseX - gridX) / cellSize);
        int row = (int)((mouseY - gridY) / cellSize);
        return col >= 0 && col < GRID_W && row >= 0 && row < GRID_H ? row * GRID_W + col : -1;
    }

    private void template() {
        Arrays.fill(cells, false);
        int mid = GRID_H / 2;
        for (int x = 5; x < GRID_W - 5; x++) {
            int half = Math.max(2, 6 - Math.abs(x - GRID_W / 2) / 6);
            for (int y = mid - half; y <= mid + half; y++) cells[y * GRID_W + x] = true;
        }
        for (int x = GRID_W / 2 - 3; x <= GRID_W / 2 + 3; x++) cells[(mid - 7) * GRID_W + x] = true;
        notice = "Тестовый шаблон загружен. Его можно дорисовать.";
    }

    private void clearBlueprint() {
        Arrays.fill(cells, false);
        notice = "Полотно очищено. Сохранённый проект не удалён.";
    }

    private void saveProject() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(SAVE_PATH))) {
                out.writeInt(SAVE_MAGIC);
                out.writeInt(SAVE_VERSION);
                out.writeInt(GRID_W);
                out.writeInt(GRID_H);
                for (boolean cell : cells) out.writeBoolean(cell);
            }
            notice = "Проект сохранён: " + countCells() + " блоков.";
        } catch (IOException ex) {
            notice = "Не удалось сохранить проект: " + ex.getClass().getSimpleName();
        }
    }

    private void loadProject(boolean explicit) {
        if (!Files.exists(SAVE_PATH)) {
            if (explicit) notice = "Сохранённый проект пока отсутствует.";
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(SAVE_PATH))) {
            if (in.readInt() != SAVE_MAGIC) throw new IOException("bad magic");
            int version = in.readInt();
            int width = in.readInt();
            int height = in.readInt();
            if (version != SAVE_VERSION || width != GRID_W || height != GRID_H) {
                throw new IOException("unsupported blueprint format");
            }
            for (int i = 0; i < cells.length; i++) cells[i] = in.readBoolean();
            notice = (explicit ? "Проект загружен: " : "Автозагружен сохранённый проект: ")
                    + countCells() + " блоков.";
        } catch (IOException ex) {
            notice = "Не удалось загрузить проект: повреждён или несовместим.";
        }
    }

    private int countCells() {
        int count = 0;
        for (boolean cell : cells) if (cell) count++;
        return count;
    }

    private void build() {
        byte[] data = new byte[cells.length];
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i]) {
                data[i] = 1;
                count++;
            }
        }
        if (count == 0) {
            notice = "Нарисуйте хотя бы один блок.";
            return;
        }
        saveProject();
        ModNetworking.CHANNEL.sendToServer(new BlueprintPackets.ServerboundBuildBlueprint(GRID_W, GRID_H, data));
        notice = "Проект сохранён и отправлен на строительство: " + count + " блоков.";
    }

    @Override public boolean isPauseScreen() { return false; }
}
