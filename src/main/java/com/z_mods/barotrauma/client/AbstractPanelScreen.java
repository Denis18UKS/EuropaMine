package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

abstract class AbstractPanelScreen extends Screen {
    protected static final int CANVAS_W = 1200;
    protected static final int CANVAS_H = 675;
    protected static final int BG = 0xFF07100E;
    protected static final int PANEL = 0xFF050A09;
    protected static final int PANEL_HOVER = 0xFF27443B;
    protected static final int BORDER = 0xFF8AD6BE;
    protected static final int MUTED = 0xFF9AA9A3;
    protected static final int TEXT = 0xFFFFF6C8;
    protected static final int BRIGHT = 0xFFFFFFFF;
    protected static final int ACCENT = 0xFF65C4A8;
    protected static final int DANGER = 0xFFDB6868;
    protected float canvasScale = 1.0F;
    protected float canvasX;
    protected float canvasY;

    protected AbstractPanelScreen(Component title) {
        super(title);
    }

    protected void beginCanvas(GuiGraphics graphics) {
        canvasScale = Math.min(width / (float) CANVAS_W, height / (float) CANVAS_H);
        canvasX = (width - CANVAS_W * canvasScale) / 2.0F;
        canvasY = (height - CANVAS_H * canvasScale) / 2.0F;
        graphics.fill(0, 0, width, height, 0xFF020605);
        graphics.pose().pushPose();
        graphics.pose().translate(canvasX, canvasY, 0);
        graphics.pose().scale(canvasScale, canvasScale, 1.0F);
        graphics.fill(0, 0, CANVAS_W, CANVAS_H, BG);
    }

    protected void endCanvas(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    protected double vx(double mouseX) {
        return (mouseX - canvasX) / canvasScale;
    }

    protected double vy(double mouseY) {
        return (mouseY - canvasY) / canvasScale;
    }

    protected static boolean inside(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    protected void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        border(graphics, x, y, w, h, BORDER);
    }

    protected void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    protected void text(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, true);
    }

    protected void centered(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawCenteredString(font, value, x, y, color);
    }

    protected void heading(GuiGraphics graphics, String value, int x, int y) {
        text(graphics, value, x, y, BRIGHT);
    }

    protected void button(GuiGraphics graphics, String value, int x, int y, int w, int h, boolean active, boolean hover) {
        graphics.fill(x, y, x + w, y + h, hover ? PANEL_HOVER : 0xFF26312E);
        border(graphics, x, y, w, h, active ? ACCENT : MUTED);
        centered(graphics, value, x + w / 2, y + (h - 8) / 2, active ? BRIGHT : MUTED);
    }

    protected void checkbox(GuiGraphics graphics, String value, int x, int y, boolean checked, boolean enabled) {
        graphics.fill(x, y, x + 13, y + 13, 0xFF0B1513);
        border(graphics, x, y, 13, 13, enabled ? ACCENT : MUTED);
        if (checked) {
            text(graphics, "✓", x + 2, y + 1, enabled ? ACCENT : MUTED);
        }
        text(graphics, value, x + 19, y + 2, enabled ? TEXT : MUTED);
    }

    protected void slider(GuiGraphics graphics, String label, int x, int y, int w, int value, int maximum, String suffix) {
        text(graphics, label, x, y, TEXT);
        int barY = y + 15;
        graphics.fill(x, barY, x + w, barY + 3, 0xFF24302C);
        int knob = x + Math.round(w * (value / (float) Math.max(1, maximum)));
        graphics.fill(x, barY, knob, barY + 3, ACCENT);
        graphics.fill(knob - 3, barY - 3, knob + 4, barY + 6, 0xFF91C7B4);
        text(graphics, value + suffix, x + w + 8, y + 11, BRIGHT);
    }

    protected void scrollbar(GuiGraphics graphics, int x, int y, int h, int offset, int maximum) {
        graphics.fill(x, y, x + 4, y + h, 0xFF2A3632);
        int thumbH = maximum <= 0 ? h : Math.max(18, h / (maximum + 1));
        int thumbY = maximum <= 0 ? y : y + Math.round((h - thumbH) * (offset / (float) maximum));
        graphics.fill(x, thumbY, x + 4, thumbY + thumbH, 0xFF9EB8AF);
    }

    protected void photo(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF020403);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(texture, x, y, 0, 0, w, h, w, h);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        border(graphics, x, y, w, h, BORDER);
    }

    /** Flushes lower GUI layers before a modal is painted, preventing delayed font batches from bleeding through. */
    protected void beginModal(GuiGraphics graphics, int alpha) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.fill(0, 0, CANVAS_W, CANVAS_H, (alpha & 0xFF) << 24);
    }

    protected void endModal(GuiGraphics graphics) {
        graphics.flush();
        graphics.pose().popPose();
    }

    protected void drawPlayerHead(GuiGraphics graphics, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            ResourceLocation skin = ((AbstractClientPlayer) minecraft.player).getSkinTextureLocation();
            graphics.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
            graphics.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
        } else {
            graphics.fill(x, y, x + size, y + size, 0xFF1B2422);
        }
        border(graphics, x, y, size, size, BORDER);
    }

    protected String fit(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) return value;
        String result = value;
        while (!result.isEmpty() && font.width(result + "…") > maximumWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
