package com.z_mods.barotrauma.panel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PanelSettingsSavedData extends SavedData {
    private static final String NAME = "barotrauma_panel_settings";
    private PanelSettings settings = new PanelSettings();

    public PanelSettingsSavedData() {
    }

    private static PanelSettingsSavedData load(CompoundTag tag) {
        PanelSettingsSavedData data = new PanelSettingsSavedData();
        data.settings = PanelSettings.fromTag(tag.getCompound("Settings"));
        return data;
    }

    public static PanelSettingsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PanelSettingsSavedData::load, PanelSettingsSavedData::new, NAME);
    }

    public PanelSettings getSettings() {
        return settings.copy();
    }

    public void setSettings(PanelSettings settings) {
        this.settings = settings.copy();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Settings", settings.toTag());
        return tag;
    }
}
