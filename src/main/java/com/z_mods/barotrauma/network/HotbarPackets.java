package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.HotbarLayoutScreen;
import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class HotbarPackets {
    private HotbarPackets() {}

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundOpenHotbarLayout.class, ids.getAsInt())
                .encoder(ClientboundOpenHotbarLayout::encode).decoder(ClientboundOpenHotbarLayout::decode)
                .consumerMainThread(ClientboundOpenHotbarLayout::handle).add();
        channel.messageBuilder(ClientboundHotbarSync.class, ids.getAsInt())
                .encoder(ClientboundHotbarSync::encode).decoder(ClientboundHotbarSync::decode)
                .consumerMainThread(ClientboundHotbarSync::handle).add();
        channel.messageBuilder(ServerboundApplyHotbar.class, ids.getAsInt())
                .encoder(ServerboundApplyHotbar::encode).decoder(ServerboundApplyHotbar::decode)
                .consumerMainThread(ServerboundApplyHotbar::handle).add();
    }

    public static void open(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundOpenHotbarLayout());
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundHotbarSync(ExtraHotbar.getAppliedCount(player)));
    }

    public record ClientboundOpenHotbarLayout() {
        static void encode(ClientboundOpenHotbarLayout packet, FriendlyByteBuf buf) {}
        static ClientboundOpenHotbarLayout decode(FriendlyByteBuf buf) { return new ClientboundOpenHotbarLayout(); }
        static void handle(ClientboundOpenHotbarLayout packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> Minecraft.getInstance().setScreen(new HotbarLayoutScreen())));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ClientboundHotbarSync(int count) {
        static void encode(ClientboundHotbarSync packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.count); }
        static ClientboundHotbarSync decode(FriendlyByteBuf buf) { return new ClientboundHotbarSync(buf.readVarInt()); }
        static void handle(ClientboundHotbarSync packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ExtraHotbar.setClientAppliedCount(packet.count)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ServerboundApplyHotbar(int count) {
        static void encode(ServerboundApplyHotbar packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.count); }
        static ServerboundApplyHotbar decode(FriendlyByteBuf buf) { return new ServerboundApplyHotbar(buf.readVarInt()); }
        static void handle(ServerboundApplyHotbar packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) return;
                ExtraHotbar.setAppliedCount(player, packet.count);
                player.getInventory().setChanged();
                sync(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
