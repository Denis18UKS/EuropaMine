package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-side draft layout: Save stores the draft; Apply stores it and synchronizes the slot count with the server. */
public final class HotbarLayoutSettings {
    private static final Path PATH = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve("barotrauma_extra_hotbar.properties");
    private static final int[] X = new int[ExtraHotbar.MAX_EXTRA];
    private static final int[] Y = new int[ExtraHotbar.MAX_EXTRA];
    private static int draftCount = 1;
    private static boolean loaded;

    private HotbarLayoutSettings() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        resetPositions();
        if (!Files.exists(PATH)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            p.load(in);
            draftCount = ExtraHotbar.clamp(parse(p.getProperty("count"), 1));
            for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
                X[i] = parse(p.getProperty("slot." + i + ".x"), i * 20);
                Y[i] = parse(p.getProperty("slot." + i + ".y"), 0);
            }
        } catch (IOException ignored) {}
    }

    public static int draftCount() { load(); return draftCount; }
    public static void setDraftCount(int value) { load(); draftCount = ExtraHotbar.clamp(value); }
    public static int x(int index) { load(); return X[index]; }
    public static int y(int index) { load(); return Y[index]; }
    public static void move(int index, int x, int y) { load(); X[index] = x; Y[index] = y; }

    public static void resetPositions() {
        for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
            X[i] = i * 20;
            Y[i] = 0;
        }
    }

    public static void save() {
        load();
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(draftCount));
        for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
            p.setProperty("slot." + i + ".x", Integer.toString(X[i]));
            p.setProperty("slot." + i + ".y", Integer.toString(Y[i]));
        }
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                p.store(out, "EuropaMine extra hotbar draft layout");
            }
        } catch (IOException ignored) {}
    }

    private static int parse(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
