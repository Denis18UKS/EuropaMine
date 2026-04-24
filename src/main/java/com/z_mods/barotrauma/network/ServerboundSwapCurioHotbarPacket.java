package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.integration.CuriosSlots;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundSwapCurioHotbarPacket(int selectedHotbarSlot) {
    public static void encode(ServerboundSwapCurioHotbarPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.selectedHotbarSlot);
    }

    public static ServerboundSwapCurioHotbarPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundSwapCurioHotbarPacket(buffer.readVarInt());
    }

    public static void handle(ServerboundSwapCurioHotbarPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        context.enqueueWork(() -> CuriosSlots.swapExtraHotbarWithHotbar(player, packet.selectedHotbarSlot()));
        context.setPacketHandled(true);
    }
}
