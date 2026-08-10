package com.z_mods.barotrauma.hotbar;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.network.HotbarPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExtraHotbarEvents {
    private ExtraHotbarEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HotbarPackets.sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HotbarPackets.sync(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        int count = event.getOriginal().getPersistentData().contains(ExtraHotbar.COUNT_KEY)
                ? event.getOriginal().getPersistentData().getInt(ExtraHotbar.COUNT_KEY) : 1;
        event.getEntity().getPersistentData().putInt(ExtraHotbar.COUNT_KEY, ExtraHotbar.clamp(count));
    }
}
