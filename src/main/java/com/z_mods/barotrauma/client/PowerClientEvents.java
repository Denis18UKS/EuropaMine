package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.PowerPackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Separate keybind used to inspect an electrical panel while looking at its bound block. */
public final class PowerClientEvents {
    public static final KeyMapping OPEN_ELECTRICAL_PANEL = new KeyMapping(
            "key.barotrauma.open_electrical_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.barotrauma"
    );

    private PowerClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_ELECTRICAL_PANEL);
        }
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_ELECTRICAL_PANEL.consumeClick()) {
                if (minecraft.player == null || minecraft.level == null) continue;
                if (minecraft.hitResult instanceof BlockHitResult blockHit) {
                    ModNetworking.CHANNEL.sendToServer(new PowerPackets.ServerboundOpenLookedAt(blockHit.getBlockPos()));
                }
            }
        }
    }
}
