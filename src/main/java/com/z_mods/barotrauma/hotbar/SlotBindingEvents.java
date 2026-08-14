package com.z_mods.barotrauma.hotbar;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.network.HotbarPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Quick-store behavior for item/block types assigned to extra slots. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlotBindingEvents {
    private SlotBindingEvents() {}

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        SlotBindingManager.copyBindings(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getItemStack().is(ModItems.SLOT_BINDING_CONFIGURATOR.get())) return;
        if (!SlotBindingManager.moveHeldToBoundSlot(player, event.getHand())) return;
        HotbarPackets.sync(player);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getItemStack().is(ModItems.SLOT_BINDING_CONFIGURATOR.get())) return;
        if (!SlotBindingManager.moveHeldToBoundSlot(player, event.getHand())) return;
        HotbarPackets.sync(player);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
