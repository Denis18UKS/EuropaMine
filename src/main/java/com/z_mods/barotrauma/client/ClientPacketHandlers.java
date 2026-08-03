package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.panel.PanelSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void openPanel(CompoundTag tag, boolean editable) {
        PanelSettings settings = PanelSettings.fromTag(tag);
        ClientPanelState.apply(settings);
        ClientPanelState.setEditable(editable);
        Minecraft.getInstance().setScreen(new SettingsPanelScreen(settings.copy(), editable));
    }

    public static void openCamera(CompoundTag tag, boolean editable) {
        PanelSettings settings = PanelSettings.fromTag(tag);
        ClientPanelState.apply(settings);
        ClientPanelState.setEditable(editable);
        Minecraft.getInstance().setScreen(new PanelCameraScreen(settings.copy(), editable));
    }

    public static void applySettings(CompoundTag tag) {
        PanelSettings settings = PanelSettings.fromTag(tag);
        ClientPanelState.apply(settings);
        if (Minecraft.getInstance().screen instanceof SettingsPanelScreen screen) screen.applyServerSettings(settings);
        if (Minecraft.getInstance().screen instanceof PanelCameraScreen screen) screen.applyServerSettings(settings);
    }

    public static void applyPhoto(int slot, byte[] png) {
        ClientPanelPhotos.apply(slot, png);
    }
}
