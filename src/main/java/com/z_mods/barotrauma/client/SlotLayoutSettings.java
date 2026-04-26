package com.z_mods.barotrauma.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class SlotLayoutSettings {
    public static final int SLOT_SIZE = 22;

    private static final String COUNT_KEY = "count";
    private static final Path SETTINGS_PATH = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("barotrauma_slot_layout.properties");

    private static final List<SlotEntry> SLOTS = new ArrayList<>();
    private static boolean loaded;

    private SlotLayoutSettings() {
    }

    public static List<SlotEntry> getSlots() {
        load();
        return Collections.unmodifiableList(SLOTS);
    }

    public static SlotEntry addSlot(int x, int y) {
        load();
        SlotEntry entry = new SlotEntry(SlotType.CUSTOM, x, y);
        SLOTS.add(entry);
        return entry;
    }

    public static void removeSlot(SlotEntry entry) {
        load();
        SLOTS.remove(entry);
    }

    public static void reset() {
        load();
        SLOTS.clear();
        SLOTS.add(new SlotEntry(SlotType.HELMET, 134, 26));
        SLOTS.add(new SlotEntry(SlotType.CHESTPLATE, 152, 26));
        SLOTS.add(new SlotEntry(SlotType.DIVING_SUIT, 134, 44));
        SLOTS.add(new SlotEntry(SlotType.BADGE, 152, 44));
        SLOTS.add(new SlotEntry(SlotType.HEADSET, 134, 62));
        SLOTS.add(new SlotEntry(SlotType.TOOL_BELT, 152, 62));
    }

    public static void save() {
        load();
        Properties properties = new Properties();
        properties.setProperty(COUNT_KEY, Integer.toString(SLOTS.size()));

        for (int index = 0; index < SLOTS.size(); index++) {
            SlotEntry entry = SLOTS.get(index);
            properties.setProperty("slot." + index + ".type", entry.getType().name());
            properties.setProperty("slot." + index + ".x", Integer.toString(entry.getX()));
            properties.setProperty("slot." + index + ".y", Integer.toString(entry.getY()));
        }

        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            try (OutputStream stream = Files.newOutputStream(SETTINGS_PATH)) {
                properties.store(stream, "Barotrauma slot layout");
            }
        } catch (IOException ignored) {
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        if (!Files.exists(SETTINGS_PATH)) {
            reset();
            return;
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(SETTINGS_PATH)) {
            properties.load(stream);
            SLOTS.clear();

            int count = parseInt(properties.getProperty(COUNT_KEY), 0);
            for (int index = 0; index < count; index++) {
                SlotType type = SlotType.byName(properties.getProperty("slot." + index + ".type"));
                int x = parseInt(properties.getProperty("slot." + index + ".x"), 8 + index * SLOT_SIZE);
                int y = parseInt(properties.getProperty("slot." + index + ".y"), 8);
                SLOTS.add(new SlotEntry(type, x, y));
            }
        } catch (IOException ignored) {
            SLOTS.clear();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public enum SlotType {
        HELMET("Helmet"),
        CHESTPLATE("Chest"),
        DIVING_SUIT("Suit"),
        BADGE("Badge"),
        HEADSET("Headset"),
        TOOL_BELT("Belt"),
        EXTRA_HOTBAR("Hotbar+"),
        CUSTOM("Custom");

        private final String label;

        SlotType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public SlotType next() {
            SlotType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private static SlotType byName(String name) {
            if (name != null) {
                for (SlotType type : values()) {
                    if (type.name().equals(name)) {
                        return type;
                    }
                }
            }
            return CUSTOM;
        }
    }

    public static final class SlotEntry {
        private SlotType type;
        private int x;
        private int y;

        private SlotEntry(SlotType type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }

        public SlotType getType() {
            return type;
        }

        public void setType(SlotType type) {
            this.type = type;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public void moveTo(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
