package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.ServerboundStructureConfigPacket;
import com.z_mods.barotrauma.structure.StructureSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class StructureConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 190;

    private final InteractionHand hand;
    private final StructureSettings.Kind kind;
    private final StructureSettings.WallType wallType;
    private boolean unbreakable;
    private boolean noAiTarget;
    private EditBox healthField;
    private Button unbreakableButton;
    private Button noAiTargetButton;

    private StructureConfigScreen(InteractionHand hand, StructureSettings.Kind kind, StructureSettings.WallType wallType) {
        super(Component.literal("Structure Settings"));
        this.hand = hand;
        this.kind = kind;
        this.wallType = wallType;
    }

    public static StructureConfigScreen platform(InteractionHand hand) {
        return new StructureConfigScreen(hand, StructureSettings.Kind.PLATFORM, StructureSettings.WallType.INTERNAL);
    }

    public static StructureConfigScreen wall(InteractionHand hand, boolean internal) {
        return new StructureConfigScreen(hand, StructureSettings.Kind.WALL,
                internal ? StructureSettings.WallType.INTERNAL : StructureSettings.WallType.EXTERNAL);
    }

    @Override
    protected void init() {
        StructureSettings.Config config = StructureSettings.read(getHeldStack());
        this.unbreakable = config.unbreakable();
        this.noAiTarget = config.noAiTarget();

        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        this.unbreakableButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.unbreakable = !this.unbreakable;
            refreshButtons();
        }).bounds(left + 24, top + 54, 155, 20).build());

        this.noAiTargetButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.noAiTarget = !this.noAiTarget;
            refreshButtons();
        }).bounds(left + 24, top + 82, 155, 20).build());

        this.healthField = this.addRenderableWidget(new EditBox(this.font, left + 164, top + 120, 158, 20,
                Component.literal("Health")));
        this.healthField.setFilter(value -> value.isEmpty() || value.matches("[0-9]+(\\.[0-9]*)?"));
        this.healthField.setValue(Float.toString(config.health()));

        this.addRenderableWidget(Button.builder(Component.literal("\u041f\u0440\u0438\u043c\u0435\u043d\u0438\u0442\u044c \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438"), button -> save())
                .bounds(left + 66, top + 154, 128, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u041d\u0430\u0437\u0430\u0434"), button -> {
            if (this.kind == StructureSettings.Kind.WALL) {
                this.minecraft.setScreen(new WallTypeScreen(this.hand));
            } else {
                this.minecraft.setScreen(new StructureKindScreen(this.hand));
            }
        }).bounds(left + 202, top + 154, 64, 20).build());

        refreshButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0080D0B);
        guiGraphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF58BFA7);
        guiGraphics.drawString(this.font, getHeader(), left + 18, top + 18, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, "\u041c\u0430\u043a\u0441. \u0437\u0434\u043e\u0440\u043e\u0432\u044c\u0435", left + 34, top + 126, 0xFFE6DFC0, false);
        guiGraphics.renderItem(getHeldStack(), left + PANEL_WIDTH - 38, top + 18);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        float health = parseHealth();
        StructureSettings.Config config = new StructureSettings.Config(this.kind, this.wallType,
                this.unbreakable, this.noAiTarget, health);
        ModNetworking.CHANNEL.sendToServer(new ServerboundStructureConfigPacket(this.hand, config));
        StructureSettings.write(getHeldStack(), config);
        getHeldStack().setHoverName(Component.literal(getHeader()));
        this.onClose();
    }

    private void refreshButtons() {
        if (this.unbreakableButton != null) {
            this.unbreakableButton.setMessage(Component.literal((this.unbreakable ? "[x] " : "[ ] ")
                    + "\u041d\u0435\u0440\u0430\u0437\u0440\u0443\u0448\u0438\u043c\u043e\u0435"));
        }
        if (this.noAiTargetButton != null) {
            this.noAiTargetButton.setMessage(Component.literal((this.noAiTarget ? "[x] " : "[ ] ")
                    + "\u041d\u0435 \u0446\u0435\u043b\u044c \u0434\u043b\u044f \u0418\u0418"));
        }
    }

    private String getHeader() {
        if (this.kind == StructureSettings.Kind.PLATFORM) {
            return "\u0411\u0430\u0437\u043e\u0432\u0430\u044f \u043f\u043b\u0430\u0442\u0444\u043e\u0440\u043c\u0430";
        }
        return this.wallType == StructureSettings.WallType.INTERNAL
                ? "\u0412\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u044f\u044f \u0441\u0442\u0435\u043d\u0430"
                : "\u0412\u043d\u0435\u0448\u043d\u044f\u044f \u0441\u0442\u0435\u043d\u0430";
    }

    private ItemStack getHeldStack() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(this.hand);
    }

    private float parseHealth() {
        try {
            return Math.max(0.0F, Float.parseFloat(this.healthField.getValue()));
        } catch (NumberFormatException ignored) {
            return 100.0F;
        }
    }
}
