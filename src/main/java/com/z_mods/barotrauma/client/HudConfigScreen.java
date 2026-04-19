package com.z_mods.barotrauma.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudConfigScreen extends Screen {
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudConfigScreen() {
        super(Component.literal("Настройка HUD"));
    }

    @Override
    protected void init() {
        int buttonY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("-"), button -> {
            HudSettings.setScale(HudSettings.getScale() - 0.1F);
        }).bounds(8, buttonY, 24, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            HudSettings.setScale(HudSettings.getScale() + 0.1F);
        }).bounds(36, buttonY, 24, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Сброс"), button -> {
            HudSettings.reset();
        }).bounds(68, buttonY, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), button -> {
            HudSettings.save();
            this.onClose();
        }).bounds(146, buttonY, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        PlayerProfileOverlay.drawProfilePanel(guiGraphics, Minecraft.getInstance(), this.width, this.height, true);
        guiGraphics.drawString(this.font, "Перетащи панель мышью. +/- меняет размер.", 8, 8, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, "Масштаб: " + String.format("%.1f", HudSettings.getScale()), 8, 22, 0xFFFFFFFF, true);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverPanel(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - PlayerProfileOverlay.getPanelX(this.width);
            dragOffsetY = mouseY - PlayerProfileOverlay.getPanelY(this.height);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            int panelX = (int) Math.round(mouseX - dragOffsetX);
            int panelY = (int) Math.round(mouseY - dragOffsetY);
            int scaledWidth = PlayerProfileOverlay.getScaledPanelWidth();
            int scaledHeight = PlayerProfileOverlay.getScaledPanelHeight();

            panelX = Math.max(0, Math.min(this.width - scaledWidth, panelX));
            panelY = Math.max(0, Math.min(this.height - scaledHeight, panelY));

            HudSettings.setOffsets(this.width - panelX - scaledWidth, this.height - panelY - scaledHeight);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        HudSettings.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isOverPanel(double mouseX, double mouseY) {
        int x = PlayerProfileOverlay.getPanelX(this.width);
        int y = PlayerProfileOverlay.getPanelY(this.height);
        return mouseX >= x && mouseX < x + PlayerProfileOverlay.getScaledPanelWidth()
                && mouseY >= y && mouseY < y + PlayerProfileOverlay.getScaledPanelHeight();
    }
}
