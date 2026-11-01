package com.z_mods.barotrauma.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only endpoints used by common network packet classes. */
public final class NetworkClientScreenHandlers {
    private NetworkClientScreenHandlers() {}

    public static void openBlueprint() {
        Minecraft.getInstance().setScreen(new SubmarineBlueprintScreen());
    }

    public static void openStructureConfig() {
        Minecraft.getInstance().setScreen(new StructureKindScreen(InteractionHand.MAIN_HAND));
    }

    public static void applyProfession(int profession) {
        if (Minecraft.getInstance().screen instanceof SettingsPanelScreen screen) {
            screen.applyProfession(profession);
        }
    }
}
