package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.menu.VentMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class VentScreen extends AbstractContainerScreen<VentMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Barotrauma.MOD_ID, "textures/gui/container/vent_shulker_box.png");

    private final Inventory playerInventory;

    public VentScreen(VentMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.playerInventory = playerInventory;
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
        drawSlotLocks(guiGraphics, x, y);
    }

    private void drawSlotLocks(GuiGraphics guiGraphics, int x, int y) {
        if (!hasSlotLockTool()) {
            return;
        }

        for (int slot = 0; slot < VentMenu.VENT_SLOT_COUNT; slot++) {
            if (this.menu.isSlotLocked(slot)) {
                int col = slot % 9;
                int row = slot / 9;
                int slotX = x + 8 + col * 18;
                int slotY = y + 18 + row * 18;
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x99000000);
                guiGraphics.drawCenteredString(this.font, "x", slotX + 8, slotY + 4, 0xFFE05050);
            }
        }
    }

    private boolean hasSlotLockTool() {
        if (this.menu.getCarried().is(ModItems.SLOT_LOCK_TOOL.get())) {
            return true;
        }

        for (ItemStack stack : playerInventory.items) {
            if (stack.is(ModItems.SLOT_LOCK_TOOL.get())) {
                return true;
            }
        }

        for (ItemStack stack : playerInventory.offhand) {
            if (stack.is(ModItems.SLOT_LOCK_TOOL.get())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasSlotLockTool()) {
            int slot = getVentSlotAt(mouseX, mouseY);
            if (slot >= 0) {
                Slot menuSlot = this.menu.slots.get(slot);
                this.slotClicked(menuSlot, slot, button, ClickType.PICKUP);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getVentSlotAt(double mouseX, double mouseY) {
        int x = this.leftPos + 8;
        int y = this.topPos + 18;

        for (int slot = 0; slot < VentMenu.VENT_SLOT_COUNT; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            int slotX = x + col * 18;
            int slotY = y + row * 18;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }

        return -1;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
