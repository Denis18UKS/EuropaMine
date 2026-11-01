package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.RoundPresentationClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class RoundPackets {
    private RoundPackets() {}

    public static void register(SimpleChannel channel, IntSupplier nextId) {
        channel.messageBuilder(ClientboundRoundStart.class, nextId.getAsInt())
                .encoder(ClientboundRoundStart::encode)
                .decoder(ClientboundRoundStart::decode)
                .consumerMainThread(ClientboundRoundStart::handle)
                .add();
    }

    public record ClientboundRoundStart(String mission, int loadingTicks) {
        public static void encode(ClientboundRoundStart packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.mission, 256);
            buf.writeVarInt(packet.loadingTicks);
        }
        public static ClientboundRoundStart decode(FriendlyByteBuf buf) {
            return new ClientboundRoundStart(buf.readUtf(256), buf.readVarInt());
        }
        public static void handle(ClientboundRoundStart packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> RoundPresentationClient.start(packet.mission, packet.loadingTicks)));
            context.setPacketHandled(true);
        }
    }
}
