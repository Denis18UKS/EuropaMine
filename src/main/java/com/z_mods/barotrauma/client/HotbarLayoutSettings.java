package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.network.HotbarPackets;
import com.z_mods.barotrauma.network.ModNetworking;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Local hotbar layout state used by the physical wall panel.
 * Draft and applied positions are deliberately separate: Save stores only the draft,
 * while Apply copies the draft to the live HUD and synchronizes the active slot count.
 */
public final class HotbarLayoutSettings {
    public static final int PANEL_W = 480;
    public static final int PANEL_H = 270;

    public static final int PREVIEW_LEFT = -230;
    public static final int PREVIEW_TOP = -92;
    public static final int PREVIEW_RIGHT = 140;
    public static final int PREVIEW_BOTTOM = 66;
    public static final int PREVIEW_VANILLA_X = -225;
    public static final int PREVIEW_HOTBAR_Y = -12;
    public static final int PREVIEW_EXTRA_BASE_X = PREVIEW_VANILLA_X + 180;

    public static final int SHOW_X1 = 125, SHOW_Y1 = -126, SHOW_X2 = 225, SHOW_Y2 = -104;
    public static final int MINUS_X1 = -220, MINUS_Y1 = 92, MINUS_X2 = -190, MINUS_Y2 = 120;
    public static final int PLUS_X1 = -182, PLUS_Y1 = 92, PLUS_X2 = -152, PLUS_Y2 = 120;
    public static final int SAVE_X1 = 20, SAVE_Y1 = 92, SAVE_X2 = 105, SAVE_Y2 = 120;
    public static final int APPLY_X1 = 115, APPLY_Y1 = 92, APPLY_X2 = 225, APPLY_Y2 = 120;

    public static final int LEFT_X1 = 148, LEFT_Y1 = -8, LEFT_X2 = 176, LEFT_Y2 = 20;
    public static final int UP_X1 = 180, UP_Y1 = -40, UP_X2 = 208, UP_Y2 = -12;
    public static final int RIGHT_X1 = 212, RIGHT_Y1 = -8, RIGHT_X2 = 240, RIGHT_Y2 = 20;
    public static final int DOWN_X1 = 180, DOWN_Y1 = 24, DOWN_X2 = 208, DOWN_Y2 = 52;

    private static final Path PATH = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve("barotrauma_extra_hotbar.properties");
    private static final int[] DRAFT_X = new int[ExtraHotbar.MAX_EXTRA];
    private static final int[] DRAFT_Y = new int[ExtraHotbar.MAX_EXTRA];
    private static final int[] APPLIED_X = new int[ExtraHotbar.MAX_EXTRA];
    private static final int[] APPLIED_Y = new int[ExtraHotbar.MAX_EXTRA];
    private static int draftCount = 1;
    private static int selectedDraftSlot;
    private static boolean showDraftOnHud;
    private static boolean loaded;
    private static String notice = "";
    private static long noticeUntil;

    private HotbarLayoutSettings() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        setDefaults(DRAFT_X, DRAFT_Y);
        setDefaults(APPLIED_X, APPLIED_Y);
        if (!Files.exists(PATH)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            p.load(in);
            int oldCount = ExtraHotbar.clamp(parse(p.getProperty("count"), 1));
            draftCount = ExtraHotbar.clamp(parse(p.getProperty("draft.count"), oldCount));
            for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
                int oldX = parse(p.getProperty("slot." + i + ".x"), i * 20);
                int oldY = parse(p.getProperty("slot." + i + ".y"), 0);
                DRAFT_X[i] = parse(p.getProperty("draft.slot." + i + ".x"), oldX);
                DRAFT_Y[i] = parse(p.getProperty("draft.slot." + i + ".y"), oldY);
                APPLIED_X[i] = parse(p.getProperty("applied.slot." + i + ".x"), oldX);
                APPLIED_Y[i] = parse(p.getProperty("applied.slot." + i + ".y"), oldY);
            }
        } catch (IOException ignored) {
        }
        selectedDraftSlot = Math.max(0, Math.min(selectedDraftSlot, draftCount - 1));
    }

    private static void setDefaults(int[] x, int[] y) {
        for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
            x[i] = i * 20;
            y[i] = 0;
        }
    }

    public static int draftCount() { load(); return draftCount; }

    public static void setDraftCount(int value) {
        load();
        draftCount = ExtraHotbar.clamp(value);
        selectedDraftSlot = Math.max(0, Math.min(selectedDraftSlot, draftCount - 1));
    }

    public static int x(int index) { load(); return showDraftOnHud ? DRAFT_X[index] : APPLIED_X[index]; }
    public static int y(int index) { load(); return showDraftOnHud ? DRAFT_Y[index] : APPLIED_Y[index]; }
    public static int draftX(int index) { load(); return DRAFT_X[index]; }
    public static int draftY(int index) { load(); return DRAFT_Y[index]; }

    public static int displayedCount() {
        load();
        return showDraftOnHud ? draftCount : ExtraHotbar.getClientAppliedCount();
    }

    public static void move(int index, int x, int y) {
        load();
        if (index < 0 || index >= ExtraHotbar.MAX_EXTRA) return;
        DRAFT_X[index] = Math.max(-180, Math.min(170, x));
        DRAFT_Y[index] = Math.max(-105, Math.min(70, y));
    }

    public static void nudgeSelected(int dx, int dy) {
        load();
        if (selectedDraftSlot < 0 || selectedDraftSlot >= draftCount) return;
        move(selectedDraftSlot, DRAFT_X[selectedDraftSlot] + dx, DRAFT_Y[selectedDraftSlot] + dy);
    }

    public static int selectedDraftSlot() { load(); return selectedDraftSlot; }
    public static void setSelectedDraftSlot(int index) {
        load();
        if (index >= 0 && index < draftCount) selectedDraftSlot = index;
    }

    public static boolean showDraftOnHud() { load(); return showDraftOnHud; }
    public static void toggleShowDraftOnHud() { load(); showDraftOnHud = !showDraftOnHud; }

    public static void resetPositions() {
        load();
        setDefaults(DRAFT_X, DRAFT_Y);
    }

    public static void save() { saveDraft(); }

    public static void saveDraft() {
        load();
        writeProperties();
    }

    public static void applyDraft() {
        load();
        System.arraycopy(DRAFT_X, 0, APPLIED_X, 0, ExtraHotbar.MAX_EXTRA);
        System.arraycopy(DRAFT_Y, 0, APPLIED_Y, 0, ExtraHotbar.MAX_EXTRA);
        showDraftOnHud = false;
        writeProperties();
        ModNetworking.CHANNEL.sendToServer(new HotbarPackets.ServerboundApplyHotbar(draftCount));
    }

    private static void writeProperties() {
        Properties p = new Properties();
        p.setProperty("format", "2");
        p.setProperty("draft.count", Integer.toString(draftCount));
        for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
            p.setProperty("draft.slot." + i + ".x", Integer.toString(DRAFT_X[i]));
            p.setProperty("draft.slot." + i + ".y", Integer.toString(DRAFT_Y[i]));
            p.setProperty("applied.slot." + i + ".x", Integer.toString(APPLIED_X[i]));
            p.setProperty("applied.slot." + i + ".y", Integer.toString(APPLIED_Y[i]));
        }
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                p.store(out, "EuropaMine extra hotbar wall-panel layout");
            }
        } catch (IOException ignored) {
        }
    }

    public static void handlePanelClick(float canvasX, float canvasY) {
        load();
        float x = canvasX - PANEL_W / 2.0F;
        float y = canvasY - PANEL_H / 2.0F;

        for (int i = draftCount - 1; i >= 0; i--) {
            int sx = PREVIEW_EXTRA_BASE_X + DRAFT_X[i];
            int sy = PREVIEW_HOTBAR_Y + DRAFT_Y[i];
            if (inside(x, y, sx, sy, sx + 22, sy + 22)) {
                selectedDraftSlot = i;
                notice("Выбран слот " + (10 + i));
                return;
            }
        }

        if (inside(x, y, SHOW_X1, SHOW_Y1, SHOW_X2, SHOW_Y2)) {
            showDraftOnHud = !showDraftOnHud;
            notice(showDraftOnHud ? "Черновик показан на HUD" : "Показана применённая раскладка");
            return;
        }
        if (inside(x, y, MINUS_X1, MINUS_Y1, MINUS_X2, MINUS_Y2)) {
            setDraftCount(draftCount - 1);
            notice("Дополнительных слотов: " + draftCount);
            return;
        }
        if (inside(x, y, PLUS_X1, PLUS_Y1, PLUS_X2, PLUS_Y2)) {
            setDraftCount(draftCount + 1);
            notice("Дополнительных слотов: " + draftCount);
            return;
        }

        if (inside(x, y, LEFT_X1, LEFT_Y1, LEFT_X2, LEFT_Y2)) { nudgeSelected(-1, 0); return; }
        if (inside(x, y, RIGHT_X1, RIGHT_Y1, RIGHT_X2, RIGHT_Y2)) { nudgeSelected(1, 0); return; }
        if (inside(x, y, UP_X1, UP_Y1, UP_X2, UP_Y2)) { nudgeSelected(0, -1); return; }
        if (inside(x, y, DOWN_X1, DOWN_Y1, DOWN_X2, DOWN_Y2)) { nudgeSelected(0, 1); return; }

        if (inside(x, y, SAVE_X1, SAVE_Y1, SAVE_X2, SAVE_Y2)) {
            saveDraft();
            notice("Черновик сохранён. HUD не изменён.");
            return;
        }
        if (inside(x, y, APPLY_X1, APPLY_Y1, APPLY_X2, APPLY_Y2)) {
            applyDraft();
            notice("Сохранено и применено.");
        }
    }

    public static String notice() {
        load();
        return System.currentTimeMillis() < noticeUntil ? notice : "";
    }

    public static void notice(String value) {
        notice = value == null ? "" : value;
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    private static boolean inside(float x, float y, float x1, float y1, float x2, float y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private static int parse(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
