package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.ServerboundSwapCurioHotbarPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
public final class CuriosHotbarKeyHandler {
    private static final int LAST_VANILLA_HOTBAR_SLOT = 8;
    private static boolean extraHotbarActive;

    private static final KeyMapping CURIOS_HOTBAR_KEY = new KeyMapping(
            "key.barotrauma.extra_hotbar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.inventory"
    );

    private CuriosHotbarKeyHandler() {
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(CURIOS_HOTBAR_KEY);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (extraHotbarActive && minecraft.player.getInventory().selected != LAST_VANILLA_HOTBAR_SLOT) {
            ModNetworking.CHANNEL.sendToServer(new ServerboundSwapCurioHotbarPacket(LAST_VANILLA_HOTBAR_SLOT));
            extraHotbarActive = false;
        }

        while (CURIOS_HOTBAR_KEY.consumeClick()) {
            minecraft.player.getInventory().selected = LAST_VANILLA_HOTBAR_SLOT;
            ModNetworking.CHANNEL.sendToServer(new ServerboundSwapCurioHotbarPacket(LAST_VANILLA_HOTBAR_SLOT));
            extraHotbarActive = !extraHotbarActive;
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || event.getScrollDelta() == 0.0D) {
            return;
        }

        int selected = minecraft.player.getInventory().selected;
        if (extraHotbarActive) {
            ModNetworking.CHANNEL.sendToServer(new ServerboundSwapCurioHotbarPacket(LAST_VANILLA_HOTBAR_SLOT));
            minecraft.player.getInventory().selected = event.getScrollDelta() < 0.0D ? 0 : LAST_VANILLA_HOTBAR_SLOT;
            extraHotbarActive = false;
            event.setCanceled(true);
            return;
        }

        if (selected == LAST_VANILLA_HOTBAR_SLOT && event.getScrollDelta() < 0.0D) {
            ModNetworking.CHANNEL.sendToServer(new ServerboundSwapCurioHotbarPacket(LAST_VANILLA_HOTBAR_SLOT));
            extraHotbarActive = true;
            event.setCanceled(true);
        }
    }
}
