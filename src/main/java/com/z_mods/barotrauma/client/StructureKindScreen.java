package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.structure.StructureSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class StructureKindScreen extends Screen {
    private final InteractionHand hand;

    public StructureKindScreen(InteractionHand hand) {
        super(Component.literal("Structure Configurator"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 35;
        this.addRenderableWidget(Button.builder(Component.literal("[ \u0421\u0442\u0435\u043d\u0430 ]"),
                button -> this.minecraft.setScreen(new WallTypeScreen(this.hand)))
                .bounds(centerX - 108, y, 96, 48).build());
        this.addRenderableWidget(Button.builder(Component.literal("[ \u041f\u043b\u0430\u0442\u0444\u043e\u0440\u043c\u0430 ]"),
                button -> this.minecraft.setScreen(StructureConfigScreen.platform(this.hand)))
                .bounds(centerX + 12, y, 112, 48).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        drawPanel(guiGraphics);
        guiGraphics.drawCenteredString(this.font, "\u0422\u0438\u043f \u0441\u0442\u0440\u0443\u043a\u0442\u0443\u0440\u044b", this.width / 2, this.height / 2 - 70, 0xFFE7F6E7);
        renderHeldBlock(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawPanel(GuiGraphics guiGraphics) {
        int x = this.width / 2 - 150;
        int y = this.height / 2 - 90;
        guiGraphics.fill(x, y, x + 300, y + 150, 0xE0080D0B);
        guiGraphics.renderOutline(x, y, 300, 150, 0xFF58BFA7);
    }

    private void renderHeldBlock(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getItemInHand(this.hand);
        guiGraphics.renderItem(stack, this.width / 2 - 8, this.height / 2 + 24);
    }
}
