package com.z_mods.barotrauma.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class HudSettings {
    public static final int DEFAULT_RIGHT_OFFSET = 12;
    public static final int DEFAULT_BOTTOM_OFFSET = 34;
    public static final float DEFAULT_SCALE = 1.0F;

    private static final String RIGHT_OFFSET_KEY = "rightOffset";
    private static final String BOTTOM_OFFSET_KEY = "bottomOffset";
    private static final String SCALE_KEY = "scale";
    private static final Path SETTINGS_PATH = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("barotrauma_hud.properties");

    private static int rightOffset = DEFAULT_RIGHT_OFFSET;
    private static int bottomOffset = DEFAULT_BOTTOM_OFFSET;
    private static float scale = DEFAULT_SCALE;
    private static boolean loaded;

    private HudSettings() {
    }

    public static void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        if (!Files.exists(SETTINGS_PATH)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(SETTINGS_PATH)) {
            properties.load(stream);
            rightOffset = parseInt(properties.getProperty(RIGHT_OFFSET_KEY), DEFAULT_RIGHT_OFFSET);
            bottomOffset = parseInt(properties.getProperty(BOTTOM_OFFSET_KEY), DEFAULT_BOTTOM_OFFSET);
            scale = clampScale(parseFloat(properties.getProperty(SCALE_KEY), DEFAULT_SCALE));
        } catch (IOException ignored) {
            reset();
        }
    }

    public static void save() {
        loaded = true;
        Properties properties = new Properties();
        properties.setProperty(RIGHT_OFFSET_KEY, Integer.toString(rightOffset));
        properties.setProperty(BOTTOM_OFFSET_KEY, Integer.toString(bottomOffset));
        properties.setProperty(SCALE_KEY, Float.toString(scale));

        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            try (OutputStream stream = Files.newOutputStream(SETTINGS_PATH)) {
                properties.store(stream, "Barotrauma HUD settings");
            }
        } catch (IOException ignored) {
        }
    }

    public static void reset() {
        rightOffset = DEFAULT_RIGHT_OFFSET;
        bottomOffset = DEFAULT_BOTTOM_OFFSET;
        scale = DEFAULT_SCALE;
    }

    public static int getRightOffset() {
        load();
        return rightOffset;
    }

    public static int getBottomOffset() {
        load();
        return bottomOffset;
    }

    public static float getScale() {
        load();
        return scale;
    }

    public static void setOffsets(int rightOffset, int bottomOffset) {
        HudSettings.rightOffset = Math.max(0, rightOffset);
        HudSettings.bottomOffset = Math.max(0, bottomOffset);
    }

    public static void setScale(float scale) {
        HudSettings.scale = clampScale(scale);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return value == null ? fallback : Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float clampScale(float scale) {
        return Math.max(0.5F, Math.min(2.0F, scale));
    }
}
