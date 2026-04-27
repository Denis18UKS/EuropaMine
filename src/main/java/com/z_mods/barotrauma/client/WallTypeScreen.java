package com.z_mods.barotrauma.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public class WallTypeScreen extends Screen {
    private final InteractionHand hand;

    public WallTypeScreen(InteractionHand hand) {
        super(Component.literal("Wall Type"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 28;
        this.addRenderableWidget(Button.builder(Component.literal("[ \u0412\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u044f\u044f ]"),
                button -> this.minecraft.setScreen(StructureConfigScreen.wall(this.hand, true)))
                .bounds(centerX - 128, y, 112, 48).build());
        this.addRenderableWidget(Button.builder(Component.literal("[ \u0412\u043d\u0435\u0448\u043d\u044f\u044f ]"),
                button -> this.minecraft.setScreen(StructureConfigScreen.wall(this.hand, false)))
                .bounds(centerX + 16, y, 112, 48).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int x = this.width / 2 - 160;
        int y = this.height / 2 - 82;
        guiGraphics.fill(x, y, x + 320, y + 140, 0xE0080D0B);
        guiGraphics.renderOutline(x, y, 320, 140, 0xFF58BFA7);
        guiGraphics.drawCenteredString(this.font, "\u0422\u0438\u043f \u0441\u0442\u0435\u043d\u044b", this.width / 2, this.height / 2 - 62, 0xFFE7F6E7);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
