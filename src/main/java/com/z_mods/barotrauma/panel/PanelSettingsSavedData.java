package com.z_mods.barotrauma.panel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class PanelSettingsSavedData extends SavedData {
    private static final String NAME = "barotrauma_panel_settings";
    private static final int MAX_CHAT_LINES = 50;
    private PanelSettings settings = new PanelSettings();
    private final List<String> chatLines = new ArrayList<>();

    public PanelSettingsSavedData() {
    }

    private static PanelSettingsSavedData load(CompoundTag tag) {
        PanelSettingsSavedData data = new PanelSettingsSavedData();
        data.settings = PanelSettings.fromTag(tag.getCompound("Settings"));
        ListTag chat = tag.getList("Chat", 8);
        for (int i = 0; i < chat.size() && data.chatLines.size() < MAX_CHAT_LINES; i++) {
            data.chatLines.add(chat.getString(i));
        }
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

    public List<String> getChatLines() {
        return List.copyOf(chatLines);
    }

    public void addChatLine(String line) {
        chatLines.add(line);
        while (chatLines.size() > MAX_CHAT_LINES) chatLines.remove(0);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Settings", settings.toTag());
        ListTag chat = new ListTag();
        for (String line : chatLines) chat.add(StringTag.valueOf(line));
        tag.put("Chat", chat);
        return tag;
    }
}
