package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.network.HotbarPackets;
import com.z_mods.barotrauma.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** In-world configurable panel screen for extra hotbar count and placement. */
public final class HotbarLayoutScreen extends Screen {
    private int selected = -1;
    private int dragging = -1;
    private double dragX;
    private double dragY;
    private String notice = "";
    private long noticeUntil;

    public HotbarLayoutScreen() {
        super(Component.literal("Настройка дополнительных слотов"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int bottom = height - 32;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            HotbarLayoutSettings.setDraftCount(HotbarLayoutSettings.draftCount() - 1);
            selected = Math.min(selected, HotbarLayoutSettings.draftCount() - 1);
        }).bounds(cx - 210, bottom, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b ->
                HotbarLayoutSettings.setDraftCount(HotbarLayoutSettings.draftCount() + 1))
                .bounds(cx - 176, bottom, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сбросить позиции"), b -> HotbarLayoutSettings.resetPositions())
                .bounds(cx - 138, bottom, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> {
            HotbarLayoutSettings.save();
            notice("Черновик сохранён. На HUD ещё не применён.");
        }).bounds(cx - 20, bottom, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Применить"), b -> {
            HotbarLayoutSettings.save();
            ModNetworking.CHANNEL.sendToServer(new HotbarPackets.ServerboundApplyHotbar(
                    HotbarLayoutSettings.draftCount()));
            notice("Сохранено и применено.");
        }).bounds(cx + 76, bottom, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(cx + 172, bottom, 72, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        int previewY = height / 2 - 20;
        g.drawCenteredString(font, "НАСТРОЙКА ХОТБАРА", cx, 24, 0xFFF1E4B1);
        g.drawCenteredString(font, "Дополнительных реальных ячеек: " + HotbarLayoutSettings.draftCount()
                + "  (итого " + (9 + HotbarLayoutSettings.draftCount()) + ")", cx, 42, 0xFFB9D8D0);
        g.drawCenteredString(font, "Перетаскивайте дополнительные слоты мышью. Сохранить = черновик, Применить = включить.",
                cx, 58, 0xFF9BAAA6);

        int vanillaLeft = cx - 91;
        for (int i = 0; i < 9; i++) drawPreviewSlot(g, vanillaLeft + i * 20, previewY, false, i + 1);
        for (int i = 0; i < HotbarLayoutSettings.draftCount(); i++) {
            int x = vanillaLeft + 9 * 20 + HotbarLayoutSettings.x(i);
            int y = previewY + HotbarLayoutSettings.y(i);
            drawPreviewSlot(g, x, y, selected == i, 10 + i);
        }
        g.drawCenteredString(font, "Предпросмотр", cx, previewY - 28, 0xFFEDE2B4);
        if (!notice.isEmpty() && System.currentTimeMillis() < noticeUntil) {
            g.drawCenteredString(font, notice, cx, height - 54, 0xFF75E6C3);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawPreviewSlot(GuiGraphics g, int x, int y, boolean selected, int number) {
        g.fill(x, y, x + 20, y + 20, selected ? 0xCC2D7569 : 0xCC131A19);
        g.fill(x + 1, y + 1, x + 19, y + 2, selected ? 0xFF89FFE1 : 0xFF678077);
        g.fill(x + 1, y + 18, x + 19, y + 19, 0xFF31413C);
        String label = number == 10 ? "0" : Integer.toString(number);
        g.drawCenteredString(font, label, x + 10, y + 6, 0xFFF4E7B5);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int cx = width / 2;
        int previewY = height / 2 - 20;
        int vanillaLeft = cx - 91;
        for (int i = HotbarLayoutSettings.draftCount() - 1; i >= 0; i--) {
            int x = vanillaLeft + 9 * 20 + HotbarLayoutSettings.x(i);
            int y = previewY + HotbarLayoutSettings.y(i);
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                selected = dragging = i;
                dragX = mouseX - x;
                dragY = mouseY - y;
                return true;
            }
        }
        selected = -1;
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && dragging >= 0) {
            int cx = width / 2;
            int previewY = height / 2 - 20;
            int baseX = cx - 91 + 9 * 20;
            int x = (int)Math.round(mouseX - dragX - baseX);
            int y = (int)Math.round(mouseY - dragY - previewY);
            x = Math.max(-baseX + 4, Math.min(width - baseX - 24, x));
            y = Math.max(-previewY + 80, Math.min(height - previewY - 80, y));
            HotbarLayoutSettings.move(dragging, x, y);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void notice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 3000L;
    }

    @Override public boolean isPauseScreen() { return false; }
}
