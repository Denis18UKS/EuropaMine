package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ClientPanelPhotos {
    private static final Map<Integer, ResourceLocation> TEXTURES = new HashMap<>();
    private static final ResourceLocation PLACEHOLDER =
            new ResourceLocation("minecraft", "textures/block/dark_prismarine.png");

    private ClientPanelPhotos() {
    }

    public static ResourceLocation texture(int slot) {
        return TEXTURES.getOrDefault(slot, PLACEHOLDER);
    }

    public static boolean has(int slot) {
        return TEXTURES.containsKey(slot);
    }

    public static void apply(int slot, byte[] png) {
        remove(slot);
        if (png == null || png.length == 0) return;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register(
                    "barotrauma_panel_" + (slot + 1), new DynamicTexture(image));
            TEXTURES.put(slot, id);
        } catch (IOException | RuntimeException exception) {
            Barotrauma.LOGGER.warn("Не удалось загрузить фотографию панели {}", slot + 1, exception);
        }
    }

    public static void remove(int slot) {
        ResourceLocation old = TEXTURES.remove(slot);
        if (old != null) Minecraft.getInstance().getTextureManager().release(old);
    }

    public static void clear() {
        for (int slot : java.util.List.copyOf(TEXTURES.keySet())) remove(slot);
    }
}
