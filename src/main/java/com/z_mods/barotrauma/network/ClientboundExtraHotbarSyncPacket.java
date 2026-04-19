package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.ClientExtraHotbarSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundExtraHotbarSyncPacket(ItemStack stack) {
    public static void encode(ClientboundExtraHotbarSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeItem(packet.stack);
    }

    public static ClientboundExtraHotbarSyncPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundExtraHotbarSyncPacket(buffer.readItem());
    }

    public static void handle(ClientboundExtraHotbarSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientExtraHotbarSlot.set(packet.stack.copy()))
        );
    }
}
