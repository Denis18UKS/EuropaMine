package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.roles.PlayerRoleStorage;
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
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_FRAME = 18;
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 58;
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

        hideVanillaSelectedSlotDuplicate(guiGraphics, minecraft, screenWidth, screenHeight);
        drawExtraHotbarSlot(guiGraphics, minecraft, screenWidth, screenHeight);
        drawProfilePanel(guiGraphics, minecraft, screenWidth, screenHeight, false);
    }

    private static void hideVanillaSelectedSlotDuplicate(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        if (!ClientExtraHotbarSlot.isSelected()) {
            return;
        }

        int selected = minecraft.player.getInventory().selected;
        int vanillaHotbarLeft = screenWidth / 2 - 91;
        int slotX = vanillaHotbarLeft + selected * 20;
        int slotY = screenHeight - 22;

        guiGraphics.blit(WIDGETS, slotX, slotY, 0, 0, 22, 22);
        guiGraphics.blit(WIDGETS, slotX - 1, slotY - 1, 0, 22, 24, 24);
    }

    private static void drawExtraHotbarSlot(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        int vanillaHotbarLeft = screenWidth / 2 - 91;
        int x = vanillaHotbarLeft + 182;
        int y = screenHeight - 22;

        guiGraphics.blit(WIDGETS, x, y, 0, 0, 22, 22);
        if (ClientExtraHotbarSlot.isSelected()) {
            guiGraphics.blit(WIDGETS, x - 1, y - 1, 0, 22, 24, 24);
        }

        ItemStack stack = ClientExtraHotbarSlot.isSelected()
                ? minecraft.player.getInventory().getSelected()
                : ClientExtraHotbarSlot.get();
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

        guiGraphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, editMode ? 0xF00B1514 : 0xE00B1514);
        guiGraphics.fill(2, 2, PANEL_WIDTH - 2, PANEL_HEIGHT - 2, 0xE0192A27);

        int slotY = 28;
        for (int slot = 0; slot < 6; slot++) {
            drawSlotFrame(guiGraphics, 12 + slot * 24, slotY, 0xFF2D3D39);
        }

        int headX = PANEL_WIDTH - 48;
        int headY = 7;
        drawPlayerHead(guiGraphics, minecraft, headX, headY);
        drawRoleIcon(guiGraphics, minecraft, headX - 26, 20);

        if (editMode) {
            guiGraphics.drawString(minecraft.font, "HUD", 8, 7, 0xFFB7D5CD, false);
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

    private static void drawSlotFrame(GuiGraphics guiGraphics, int x, int y, int color) {
        guiGraphics.fill(x - 1, y - 1, x + SLOT_FRAME, y + SLOT_FRAME, color);
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF07100F);
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

    private static void drawRoleIcon(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y) {
        String roleId = PlayerRoleStorage.getRoleId(minecraft.player);
        if (roleId == null) {
            drawMissingRoleIcon(guiGraphics, x, y, "?");
            return;
        }

        ResourceLocation icon = new ResourceLocation(Barotrauma.MOD_ID, "textures/gui/roles/" + roleId + ".png");
        if (minecraft.getResourceManager().getResource(icon).isPresent()) {
            guiGraphics.blit(icon, x, y, 0, 0, 20, 20, 20, 20);
        } else {
            drawMissingRoleIcon(guiGraphics, x, y, roleId.substring(0, 1).toUpperCase());
        }
    }

    private static void drawMissingRoleIcon(GuiGraphics guiGraphics, int x, int y, String label) {
        Minecraft minecraft = Minecraft.getInstance();
        guiGraphics.fill(x, y, x + 20, y + 20, 0xFF263632);
        guiGraphics.drawString(minecraft.font, label, x + 7, y + 6, 0xFFB7D5CD, false);
    }
}
