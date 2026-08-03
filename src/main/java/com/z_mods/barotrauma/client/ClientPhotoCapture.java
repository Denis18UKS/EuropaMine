package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.PanelPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.z_mods.barotrauma.Barotrauma;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
public final class ClientPhotoCapture {
    private static int pendingSlot = -1;
    private static int delayTicks;

    private ClientPhotoCapture() {
    }

    public static void schedule(int slot) {
        pendingSlot = slot;
        delayTicks = 2;
        Minecraft.getInstance().setScreen(null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingSlot < 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || delayTicks-- > 0) return;
        int slot = pendingSlot;
        pendingSlot = -1;
        NativeImage screenshot = null;
        NativeImage square = null;
        try {
            screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            int sourceSize = Math.min(screenshot.getWidth(), screenshot.getHeight());
            int targetSize = Math.min(256, sourceSize);
            int sourceX = (screenshot.getWidth() - sourceSize) / 2;
            int sourceY = (screenshot.getHeight() - sourceSize) / 2;
            square = new NativeImage(targetSize, targetSize, false);
            for (int y = 0; y < targetSize; y++) {
                int sampleY = sourceY + y * sourceSize / targetSize;
                for (int x = 0; x < targetSize; x++) {
                    int sampleX = sourceX + x * sourceSize / targetSize;
                    square.setPixelRGBA(x, y, screenshot.getPixelRGBA(sampleX, sampleY));
                }
            }
            ModNetworking.CHANNEL.sendToServer(new PanelPackets.ServerboundPhotoUpload(slot, square.asByteArray()));
        } catch (IOException | RuntimeException exception) {
            minecraft.player.displayClientMessage(Component.translatable("message.barotrauma.photo_failed"), true);
        } finally {
            if (screenshot != null) screenshot.close();
            if (square != null) square.close();
        }
    }
}
