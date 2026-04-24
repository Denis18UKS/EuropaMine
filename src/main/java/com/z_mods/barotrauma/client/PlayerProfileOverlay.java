package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.integration.CuriosSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
public final class PlayerProfileOverlay {
    private static final int PANEL_WIDTH = 48;
    private static final int PANEL_HEIGHT = 48;
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");

    private PlayerProfileOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        drawCuriosHotbarSlot(guiGraphics, minecraft, screenWidth, screenHeight);
        drawProfilePanel(guiGraphics, minecraft, screenWidth, screenHeight, false);
    }

    private static void drawCuriosHotbarSlot(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        int vanillaHotbarLeft = screenWidth / 2 - 91;
        int x = vanillaHotbarLeft + 182;
        int y = screenHeight - 22;

        guiGraphics.blit(WIDGETS, x, y, 0, 0, 22, 22);

        ItemStack stack = CuriosSlots.getFirstStack(minecraft.player, CuriosSlots.EXTRA_HOTBAR_SLOT);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 3, y + 3);
            guiGraphics.renderItemDecorations(minecraft.font, stack, x + 3, y + 3);
        }
    }

    public static void drawProfilePanel(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight, boolean editMode) {
        int x = getPanelX(screenWidth);
        int y = getPanelY(screenHeight);
        float scale = HudSettings.getScale();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);

        drawPlayerHead(guiGraphics, minecraft, 4, 4);

        if (editMode) {
            guiGraphics.drawString(minecraft.font, "HUD", 4, 4, 0xFFB7D5CD, false);
        }

        guiGraphics.pose().popPose();
    }

    public static int getPanelX(int screenWidth) {
        return Math.max(0, screenWidth - getScaledPanelWidth() - HudSettings.getRightOffset());
    }

    public static int getPanelY(int screenHeight) {
        return Math.max(0, screenHeight - getScaledPanelHeight() - HudSettings.getBottomOffset());
    }

    public static int getScaledPanelWidth() {
        return Math.round(PANEL_WIDTH * HudSettings.getScale());
    }

    public static int getScaledPanelHeight() {
        return Math.round(PANEL_HEIGHT * HudSettings.getScale());
    }

    private static void drawPlayerHead(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y) {
        if (minecraft.player != null) {
            ResourceLocation skin = ((AbstractClientPlayer) minecraft.player).getSkinTextureLocation();
            guiGraphics.blit(skin, x, y, 40, 40, 8.0F, 8.0F, 8, 8, 64, 64);
            guiGraphics.blit(skin, x, y, 40, 40, 40.0F, 8.0F, 8, 8, 64, 64);
        } else {
            guiGraphics.fill(x, y, x + 40, y + 40, 0xFF1B2422);
        }
    }
}
