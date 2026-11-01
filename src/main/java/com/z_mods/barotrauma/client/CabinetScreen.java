package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.menu.CabinetMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CabinetScreen extends AbstractContainerScreen<CabinetMenu> {
    public CabinetScreen(CabinetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 212;
        this.imageHeight = menu.isLarge() ? 258 : 243;
        this.titleLabelX = 12;
        this.titleLabelY = 8;
        this.inventoryLabelX = 25;
        this.inventoryLabelY = menu.isLarge() ? 148 : 133;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF20A100E);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF111B18);
        g.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF070B0A);
        g.fill(x + 7, y + 7, x + imageWidth - 7, y + 10, 0xFF547064);
        g.fill(x + 7, y + imageHeight - 10, x + imageWidth - 7, y + imageHeight - 7, 0xFF253831);

        int columns = menu.isLarge() ? 7 : 5;
        int rows = menu.isLarge() ? 5 : 4;
        int mainX = x + (212 - columns * 18) / 2;
        int mainY = y + 24;
        for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
            slotFrame(g, mainX + col * 18, mainY + row * 18, false);
        }
        int specialY = y + (menu.isLarge() ? 120 : 101);
        int specialX = x + (212 - menu.specialSlots() * 18) / 2;
        for (int i = 0; i < menu.specialSlots(); i++) slotFrame(g, specialX + i * 18, specialY, true);

        int playerY = y + (menu.isLarge() ? 160 : 145);
        int playerX = x + 25;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slotFrame(g, playerX + col * 18, playerY + row * 18, false);
        for (int col = 0; col < 9; col++) slotFrame(g, playerX + col * 18, playerY + 58, false);

        g.drawCenteredString(font, "СПЕЦ. ЯЧЕЙКИ: ПОЯС / ЯЩИК / БОЛЬШОЙ ЯЩИК", x + imageWidth / 2,
                specialY - 11, 0xFFB6A96F);
    }

    private void slotFrame(GuiGraphics g, int x, int y, boolean special) {
        g.fill(x - 1, y - 1, x + 17, y + 17, special ? 0xFF6B5730 : 0xFF27342F);
        g.fill(x, y, x + 16, y + 16, special ? 0xFF211B10 : 0xFF0D1210);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFEADDA8, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF9CB0A7, false);
    }
}
