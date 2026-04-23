package com.z_mods.barotrauma.integration;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CuriosGearEvents {
    private CuriosGearEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        if (CuriosSlots.equipToFirstAvailable(event.getEntity(), stack.copy())) {
            event.getEntity().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            event.setCanceled(true);
        }
    }
}
