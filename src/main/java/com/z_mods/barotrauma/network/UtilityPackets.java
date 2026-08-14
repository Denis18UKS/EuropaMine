package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.StructureKindScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class UtilityPackets {
    private UtilityPackets() {}
    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundOpenStructureConfig.class, ids.getAsInt())
                .encoder(ClientboundOpenStructureConfig::encode).decoder(ClientboundOpenStructureConfig::decode)
                .consumerMainThread(ClientboundOpenStructureConfig::handle).add();
    }
    public static void openStructureConfig(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundOpenStructureConfig());
    }
    public record ClientboundOpenStructureConfig() {
        static void encode(ClientboundOpenStructureConfig p, FriendlyByteBuf b) {}
        static ClientboundOpenStructureConfig decode(FriendlyByteBuf b) { return new ClientboundOpenStructureConfig(); }
        static void handle(ClientboundOpenStructureConfig p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> Minecraft.getInstance().setScreen(new StructureKindScreen(InteractionHand.MAIN_HAND))));
            c.get().setPacketHandled(true);
        }
    }
}
