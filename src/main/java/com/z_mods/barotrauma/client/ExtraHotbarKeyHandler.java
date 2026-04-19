package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.ServerboundSwapExtraHotbarPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.InputEvent.MouseScrollingEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ExtraHotbarKeyHandler {
    private static final KeyMapping EXTRA_HOTBAR_KEY = new KeyMapping(
            "key.barotrauma.extra_hotbar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.inventory"
    );

    private ExtraHotbarKeyHandler() {
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(EXTRA_HOTBAR_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }

            if (ClientExtraHotbarSlot.isSelected()
                    && event.getAction() == GLFW.GLFW_PRESS
                    && event.getKey() >= GLFW.GLFW_KEY_1
                    && event.getKey() <= GLFW.GLFW_KEY_9) {
                restoreSelectedExtraSlot();
                return;
            }

            while (EXTRA_HOTBAR_KEY.consumeClick()) {
                int selected = minecraft.player.getInventory().selected;
                ModNetworking.CHANNEL.sendToServer(
                        new ServerboundSwapExtraHotbarPacket(selected)
                );
                ClientExtraHotbarSlot.toggleSelected(selected);
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(MouseScrollingEvent event) {
            if (ClientExtraHotbarSlot.isSelected()) {
                restoreSelectedExtraSlot();
            }
        }

        private static void restoreSelectedExtraSlot() {
            ModNetworking.CHANNEL.sendToServer(
                    new ServerboundSwapExtraHotbarPacket(ClientExtraHotbarSlot.getSelectedVanillaSlot())
            );
            ClientExtraHotbarSlot.clearSelected();
        }
    }
}
