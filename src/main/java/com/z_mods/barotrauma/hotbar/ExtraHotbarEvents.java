package com.z_mods.barotrauma.hotbar;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.network.HotbarPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExtraHotbarEvents {
    private ExtraHotbarEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        repairLegacySelection(player);
        HotbarPackets.sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ExtraHotbar.sanitizeVanillaSelected(player);
        HotbarPackets.sync(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        int count = event.getOriginal().getPersistentData().contains(ExtraHotbar.COUNT_KEY)
                ? event.getOriginal().getPersistentData().getInt(ExtraHotbar.COUNT_KEY) : 1;
        event.getEntity().getPersistentData().putInt(ExtraHotbar.COUNT_KEY, ExtraHotbar.clamp(count));

        if (event.getOriginal().getPersistentData().contains(ExtraHotbar.SELECTED_KEY)) {
            int selected = event.getOriginal().getPersistentData().getInt(ExtraHotbar.SELECTED_KEY);
            if (selected >= 0 && selected < ExtraHotbar.clamp(count)) {
                event.getEntity().getPersistentData().putInt(ExtraHotbar.SELECTED_KEY, selected);
            }
        }

        // Forge invalidates the old player's capabilities during clone; revive temporarily to copy the safe storage.
        event.getOriginal().reviveCaps();
        ExtraHotbarStorage oldStorage = ExtraHotbar.storage(event.getOriginal());
        ExtraHotbarStorage newStorage = ExtraHotbar.storage(event.getEntity());
        if (oldStorage != null && newStorage != null) {
            newStorage.deserializeNBT(oldStorage.serializeNBT());
        }
        event.getOriginal().invalidateCaps();
        ExtraHotbar.sanitizeVanillaSelected(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 == 0) HotbarPackets.sync(player);
    }

    private static void repairLegacySelection(ServerPlayer player) {
        int selected = player.getInventory().selected;
        if (selected >= ExtraHotbar.VANILLA_HOTBAR) {
            int extra = selected - ExtraHotbar.VANILLA_HOTBAR;
            player.getInventory().selected = 0;
            if (extra >= 0 && extra < ExtraHotbar.MAX_EXTRA) {
                if (extra >= ExtraHotbar.getAppliedCount(player)) ExtraHotbar.setAppliedCount(player, extra + 1);
                ExtraHotbar.setSelectedExtra(player, extra);
            } else {
                ExtraHotbar.clearSelectedExtra(player);
            }
        } else {
            ExtraHotbar.sanitizeVanillaSelected(player);
        }
    }
}
