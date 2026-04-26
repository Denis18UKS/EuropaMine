package com.z_mods.barotrauma.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class HudConfigScreen extends Screen {
    private SlotLayoutSettings.SlotEntry selectedSlot;
    private SlotLayoutSettings.SlotEntry draggedSlot;
    private double dragOffsetX;
    private double dragOffsetY;
    private Button typeButton;
    private Button deleteButton;

    public HudConfigScreen() {
        super(Component.literal("Slot Layout"));
    }

    @Override
    protected void init() {
        int y = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> {
            selectedSlot = SlotLayoutSettings.addSlot(this.width / 2 - 11, this.height / 2 - 11);
            refreshButtons();
        }).bounds(8, y, 48, 20).build());

        typeButton = this.addRenderableWidget(Button.builder(Component.literal("Type"), button -> {
            if (selectedSlot != null) {
                selectedSlot.setType(selectedSlot.getType().next());
                refreshButtons();
            }
        }).bounds(62, y, 82, 20).build());

        deleteButton = this.addRenderableWidget(Button.builder(Component.literal("Delete"), button -> {
            if (selectedSlot != null) {
                SlotLayoutSettings.removeSlot(selectedSlot);
                selectedSlot = null;
                refreshButtons();
            }
        }).bounds(150, y, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            SlotLayoutSettings.reset();
            selectedSlot = null;
            refreshButtons();
        }).bounds(216, y, 54, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            SlotLayoutSettings.save();
            this.onClose();
        }).bounds(276, y, 54, 20).build());

        refreshButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        Minecraft minecraft = Minecraft.getInstance();

        guiGraphics.drawString(this.font, "Slot layout editor", 8, 8, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, "Left click selects and drags. Right click deletes. Type cycles the selected slot.", 8, 22, 0xFFE0E0E0, true);

        for (SlotLayoutSettings.SlotEntry slot : SlotLayoutSettings.getSlots()) {
            PlayerProfileOverlay.drawSlot(guiGraphics, minecraft, slot, slot == selectedSlot);
        }

        PlayerProfileOverlay.drawProfilePanel(guiGraphics, minecraft, this.width, this.height, true);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        SlotLayoutSettings.SlotEntry hit = getSlotAt(mouseX, mouseY);
        if (hit == null) {
            selectedSlot = null;
            refreshButtons();
            return false;
        }

        if (button == 1) {
            SlotLayoutSettings.removeSlot(hit);
            if (selectedSlot == hit) {
                selectedSlot = null;
            }
            refreshButtons();
            return true;
        }

        if (button == 0) {
            selectedSlot = hit;
            draggedSlot = hit;
            dragOffsetX = mouseX - hit.getX();
            dragOffsetY = mouseY - hit.getY();
            refreshButtons();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggedSlot != null && button == 0) {
            int x = (int) Math.round(mouseX - dragOffsetX);
            int y = (int) Math.round(mouseY - dragOffsetY);
            x = Math.max(0, Math.min(this.width - SlotLayoutSettings.SLOT_SIZE, x));
            y = Math.max(0, Math.min(this.height - SlotLayoutSettings.SLOT_SIZE, y));
            draggedSlot.moveTo(x, y);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggedSlot = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        SlotLayoutSettings.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private SlotLayoutSettings.SlotEntry getSlotAt(double mouseX, double mouseY) {
        List<SlotLayoutSettings.SlotEntry> slots = SlotLayoutSettings.getSlots();
        for (int index = slots.size() - 1; index >= 0; index--) {
            SlotLayoutSettings.SlotEntry slot = slots.get(index);
            if (mouseX >= slot.getX() && mouseX < slot.getX() + SlotLayoutSettings.SLOT_SIZE
                    && mouseY >= slot.getY() && mouseY < slot.getY() + SlotLayoutSettings.SLOT_SIZE) {
                return slot;
            }
        }
        return null;
    }

    private void refreshButtons() {
        if (typeButton != null) {
            typeButton.active = selectedSlot != null;
            typeButton.setMessage(Component.literal(selectedSlot == null ? "Type" : selectedSlot.getType().getLabel()));
        }
        if (deleteButton != null) {
            deleteButton.active = selectedSlot != null;
        }
    }
}
