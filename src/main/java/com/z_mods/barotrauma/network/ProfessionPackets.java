package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.client.SettingsPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProfessionPackets {
    private static final String KEY = "barotrauma_profession";
    private ProfessionPackets() {}

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundProfession.class, ids.getAsInt())
                .encoder(ClientboundProfession::encode).decoder(ClientboundProfession::decode)
                .consumerMainThread(ClientboundProfession::handle).add();
        channel.messageBuilder(ServerboundProfession.class, ids.getAsInt())
                .encoder(ServerboundProfession::encode).decoder(ServerboundProfession::decode)
                .consumerMainThread(ServerboundProfession::handle).add();
    }

    public static int get(ServerPlayer player) {
        return Mth.clamp(player.getPersistentData().contains(KEY) ? player.getPersistentData().getInt(KEY) : 5, 0, 5);
    }

    public static void sync(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundProfession(get(player)));
    }

    public record ClientboundProfession(int profession) {
        static void encode(ClientboundProfession p, FriendlyByteBuf b) { b.writeVarInt(p.profession); }
        static ClientboundProfession decode(FriendlyByteBuf b) { return new ClientboundProfession(b.readVarInt()); }
        static void handle(ClientboundProfession p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().screen instanceof SettingsPanelScreen screen) screen.applyProfession(p.profession);
            }));
            c.get().setPacketHandled(true);
        }
    }

    public record ServerboundProfession(int profession) {
        static void encode(ServerboundProfession p, FriendlyByteBuf b) { b.writeVarInt(p.profession); }
        static ServerboundProfession decode(FriendlyByteBuf b) { return new ServerboundProfession(b.readVarInt()); }
        static void handle(ServerboundProfession p, Supplier<NetworkEvent.Context> c) {
            ServerPlayer player = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (player == null) return;
                player.getPersistentData().putInt(KEY, Mth.clamp(p.profession, 0, 5));
                sync(player);
            });
            c.get().setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.getOriginal().getPersistentData().contains(KEY)) return;
        event.getEntity().getPersistentData().putInt(KEY, event.getOriginal().getPersistentData().getInt(KEY));
    }
}
