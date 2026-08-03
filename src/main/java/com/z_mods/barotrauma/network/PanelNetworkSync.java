package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.panel.PanelPhotoStorage;
import com.z_mods.barotrauma.panel.PanelSettings;
import com.z_mods.barotrauma.panel.PanelSettingsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID)
public final class PanelNetworkSync {
    private PanelNetworkSync() {
    }

    public static void openSettings(ServerPlayer player) {
        PanelSettings settings = PanelSettingsSavedData.get(player.server).getSettings();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PanelPackets.ClientboundOpenPanel(settings.toTag(), canEdit(player)));
        syncPhotos(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PanelSettings settings = PanelSettingsSavedData.get(player.server).getSettings();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PanelPackets.ClientboundSettings(settings.toTag()));
        syncPhotos(player);
    }

    public static void openCamera(ServerPlayer player) {
        PanelSettings settings = PanelSettingsSavedData.get(player.server).getSettings();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PanelPackets.ClientboundOpenCamera(settings.toTag(), canEdit(player)));
        syncPhotos(player);
    }

    public static void syncPhotos(ServerPlayer player) {
        for (int slot = 0; slot < PanelSettings.PHOTO_SLOTS; slot++) {
            byte[] bytes = PanelPhotoStorage.read(slot);
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PanelPackets.ClientboundPhoto(slot, bytes == null ? new byte[0] : bytes));
        }
    }

    public static void broadcastSettings(MinecraftServer server) {
        PanelSettings settings = PanelSettingsSavedData.get(server).getSettings();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PanelPackets.ClientboundSettings(settings.toTag()));
        }
    }

    public static void broadcastPhoto(MinecraftServer server, int slot, byte[] bytes) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PanelPackets.ClientboundPhoto(slot, bytes == null ? new byte[0] : bytes));
        }
    }

    public static boolean canEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.getAbilities().instabuild;
    }
}
