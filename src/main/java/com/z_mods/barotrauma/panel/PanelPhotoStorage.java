package com.z_mods.barotrauma.panel;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

/** Server-owned PNG storage shared with all players. */
public final class PanelPhotoStorage {
    public static final int MAX_PHOTO_BYTES = 512 * 1024;
    private static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve("barotrauma_panel_photos");

    private PanelPhotoStorage() {
    }

    public static byte[] read(int slot) {
        if (!validSlot(slot)) return null;
        try {
            Path path = path(slot);
            if (!Files.isRegularFile(path)) return null;
            byte[] bytes = Files.readAllBytes(path);
            return isValidPng(bytes) ? bytes : null;
        } catch (IOException exception) {
            Barotrauma.LOGGER.warn("Не удалось прочитать фотографию панели {}", slot + 1, exception);
            return null;
        }
    }

    public static boolean save(int slot, byte[] bytes) {
        if (!validSlot(slot) || !isValidPng(bytes)) return false;
        try {
            Files.createDirectories(DIRECTORY);
            Path target = path(slot);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            Barotrauma.LOGGER.warn("Не удалось сохранить фотографию панели {}", slot + 1, exception);
            return false;
        }
    }

    public static void delete(int slot) {
        if (!validSlot(slot)) return;
        try {
            Files.deleteIfExists(path(slot));
        } catch (IOException exception) {
            Barotrauma.LOGGER.warn("Не удалось удалить фотографию панели {}", slot + 1, exception);
        }
    }

    public static void deleteAll() {
        for (int slot = 0; slot < PanelSettings.PHOTO_SLOTS; slot++) delete(slot);
    }

    public static boolean validSlot(int slot) {
        return slot >= 0 && slot < PanelSettings.PHOTO_SLOTS;
    }

    private static Path path(int slot) {
        return DIRECTORY.resolve("submarine_" + (slot + 1) + ".png");
    }

    private static boolean isValidPng(byte[] bytes) {
        return bytes != null && bytes.length >= 8 && bytes.length <= MAX_PHOTO_BYTES
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E
                && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A
                && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }
}
