package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.hud.ExtraHotbarStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record ServerboundSwapExtraHotbarPacket(int selectedSlot) {
    public static void encode(ServerboundSwapExtraHotbarPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.selectedSlot);
    }

    public static ServerboundSwapExtraHotbarPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundSwapExtraHotbarPacket(buffer.readVarInt());
    }

    public static void handle(ServerboundSwapExtraHotbarPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        ItemStack extra = ExtraHotbarStorage.swapWithSelected(player, packet.selectedSlot);
        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ClientboundExtraHotbarSyncPacket(extra)
        );
    }
}
