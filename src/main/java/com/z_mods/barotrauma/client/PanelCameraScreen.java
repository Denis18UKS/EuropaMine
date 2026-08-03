package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.PanelPackets;
import com.z_mods.barotrauma.panel.PanelSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PanelCameraScreen extends AbstractPanelScreen {
    private PanelSettings settings;
    private final boolean editable;
    private int selected;
    private boolean editingName;
    private boolean enlarged;
    private boolean confirmDeleteAll;

    public PanelCameraScreen(PanelSettings settings, boolean editable) {
        super(Component.translatable("screen.barotrauma.panel_camera"));
        this.settings = settings;
        this.editable = editable;
        this.selected = settings.submarine;
    }

    public void applyServerSettings(PanelSettings value) {
        settings = value.copy();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beginCanvas(graphics);
        double mx = vx(mouseX), my = vy(mouseY);
        heading(graphics, "ФОТОКАМЕРА ПАНЕЛИ НАСТРОЕК", 35, 28);
        text(graphics, "Выберите ячейку, задайте название и сделайте квадратный снимок текущего вида.",
                35, 48, TEXT);
        if (!editable) text(graphics, "Режим просмотра — для изменений нужны права оператора.", 35, 65, DANGER);
        drawGallery(graphics, mx, my);
        drawEditor(graphics, mx, my);
        button(graphics, "Закрыть", 1030, 625, 135, 28, true, inside(mx, my, 1030, 625, 1165, 653));
        if (confirmDeleteAll) {
            beginModal(graphics, 225);
            drawDeleteConfirmation(graphics, mx, my);
            endModal(graphics);
        } else if (enlarged) {
            beginModal(graphics, 245);
            drawEnlarged(graphics, mx, my);
            endModal(graphics);
        }
        endCanvas(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawGallery(GuiGraphics g, double mx, double my) {
        panel(g, 25, 85, 730, 525);
        heading(g, "ГАЛЕРЕЯ ПОДЛОДОК", 42, 102);
        for (int slot = 0; slot < PanelSettings.PHOTO_SLOTS; slot++) {
            int col = slot % 4;
            int row = slot / 4;
            int x = 42 + col * 174;
            int y = 126 + row * 151;
            if (selected == slot) g.fill(x - 5, y - 5, x + 163, y + 137, 0x88478E78);
            photo(g, ClientPanelPhotos.texture(slot), x, y, 153, 102);
            graphicsStatus(g, slot, x, y);
            centered(g, fit(settings.photoNames[slot], 150), x + 76, y + 111, selected == slot ? ACCENT : TEXT);
            centered(g, "Ячейка " + (slot + 1), x + 76, y + 123, MUTED);
        }
    }

    private void graphicsStatus(GuiGraphics g, int slot, int x, int y) {
        if (!ClientPanelPhotos.has(slot)) {
            g.fill(x + 1, y + 1, x + 152, y + 101, 0x88000000);
            centered(g, "Нет фотографии", x + 76, y + 47, MUTED);
        }
    }

    private void drawEditor(GuiGraphics g, double mx, double my) {
        panel(g, 770, 85, 405, 525);
        heading(g, "ВЫБРАННАЯ ЯЧЕЙКА " + (selected + 1), 790, 102);
        photo(g, ClientPanelPhotos.texture(selected), 790, 128, 365, 274);
        button(g, "Увеличить", 1015, 412, 140, 24, true, inside(mx, my, 1015, 412, 1155, 436));
        text(g, "Название", 790, 453, TEXT);
        g.fill(790, 468, 365 + 790, 493, 0xFF07100E);
        border(g, 790, 468, 365, 25, editingName ? ACCENT : BORDER);
        text(g, fit(settings.photoNames[selected], 345) + (editingName ? "_" : ""), 799, 476, BRIGHT);
        button(g, "Сделать фото", 790, 514, 175, 30, editable, inside(mx, my, 790, 514, 965, 544));
        button(g, "Удалить фото", 980, 514, 175, 30, editable && ClientPanelPhotos.has(selected),
                inside(mx, my, 980, 514, 1155, 544));
        button(g, "Удалить все фотографии", 790, 559, 365, 30, editable,
                inside(mx, my, 790, 559, 1155, 589));
    }

    private void drawDeleteConfirmation(GuiGraphics g, double mx, double my) {
        panel(g, 415, 235, 370, 160);
        centered(g, "Удалить все 12 фотографий?", 600, 262, BRIGHT);
        centered(g, "Отменить это действие будет нельзя.", 600, 286, DANGER);
        button(g, "Удалить все", 445, 335, 140, 30, true, inside(mx, my, 445, 335, 585, 365));
        button(g, "Отмена", 615, 335, 140, 30, true, inside(mx, my, 615, 335, 755, 365));
    }

    private void drawEnlarged(GuiGraphics g, double mx, double my) {
        panel(g, 190, 35, 820, 605);
        centered(g, settings.photoNames[selected], 600, 52, BRIGHT);
        photo(g, ClientPanelPhotos.texture(selected), 220, 78, 760, 510);
        button(g, "Закрыть", 820, 598, 150, 28, true, inside(mx, my, 820, 598, 970, 626));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        double x = vx(mouseX), y = vy(mouseY);
        if (enlarged) {
            if (inside(x, y, 820, 598, 970, 626) || !inside(x, y, 190, 35, 1010, 640)) enlarged = false;
            return true;
        }
        if (confirmDeleteAll) {
            if (inside(x, y, 445, 335, 585, 365)) {
                ModNetworking.CHANNEL.sendToServer(new PanelPackets.ServerboundPhotoDelete(0, true));
                confirmDeleteAll = false;
            } else if (inside(x, y, 615, 335, 755, 365)) confirmDeleteAll = false;
            return true;
        }
        for (int slot = 0; slot < PanelSettings.PHOTO_SLOTS; slot++) {
            int gx = 42 + (slot % 4) * 174;
            int gy = 126 + (slot / 4) * 151;
            if (inside(x, y, gx, gy, gx + 153, gy + 137)) {
                selected = slot;
                editingName = false;
                return true;
            }
        }
        if (inside(x, y, 1015, 412, 1155, 436)) { enlarged = true; return true; }
        if (inside(x, y, 790, 468, 1155, 493)) { editingName = editable; return true; }
        if (inside(x, y, 790, 514, 965, 544)) {
            if (editable) {
                settings.submarine = selected;
                sendSettings();
                ClientPhotoCapture.schedule(selected);
            }
            return true;
        }
        if (inside(x, y, 980, 514, 1155, 544)) {
            if (editable) ModNetworking.CHANNEL.sendToServer(new PanelPackets.ServerboundPhotoDelete(selected, false));
            return true;
        }
        if (inside(x, y, 790, 559, 1155, 589)) {
            if (editable) confirmDeleteAll = true;
            return true;
        }
        if (inside(x, y, 1030, 625, 1165, 653)) { onClose(); return true; }
        editingName = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!editingName || Character.isISOControl(codePoint)) return super.charTyped(codePoint, modifiers);
        String value = settings.photoNames[selected];
        if (value.length() < 40) {
            settings.photoNames[selected] = value + codePoint;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingName) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String value = settings.photoNames[selected];
                if (!value.isEmpty()) settings.photoNames[selected] = value.substring(0, value.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                editingName = false;
                sendSettings();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendSettings() {
        settings.sanitize();
        ClientPanelState.apply(settings);
        if (editable) ModNetworking.CHANNEL.sendToServer(new PanelPackets.ServerboundSettings(settings.toTag()));
    }

    @Override
    public void onClose() {
        if (editingName) sendSettings();
        super.onClose();
    }
}
