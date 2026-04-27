package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
public final class StructureConfiguratorKeyHandler {
    public static final KeyMapping OPEN_CONFIGURATOR = new KeyMapping(
            "key.barotrauma.structure_configurator",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.barotrauma"
    );

    private StructureConfiguratorKeyHandler() {
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_CONFIGURATOR);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CONFIGURATOR.consumeClick()) {
            if (minecraft.screen == null && canOpen(minecraft.player)) {
                minecraft.setScreen(new StructureKindScreen(findBlockHand(minecraft.player)));
            }
        }
    }

    private static boolean canOpen(Player player) {
        return player != null && player.getAbilities().instabuild && findBlockHand(player) != null;
    }

    private static InteractionHand findBlockHand(Player player) {
        if (isBlock(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        return isBlock(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    private static boolean isBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }
}
