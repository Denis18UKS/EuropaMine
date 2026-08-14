package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.SlotBindingConfiguratorScreen;
import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.hotbar.SlotBindingManager;
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

public final class SlotBindingPackets {
    private SlotBindingPackets() {}

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundOpenSlotBindings.class, ids.getAsInt())
                .encoder(ClientboundOpenSlotBindings::encode).decoder(ClientboundOpenSlotBindings::decode)
                .consumerMainThread(ClientboundOpenSlotBindings::handle).add();
        channel.messageBuilder(ServerboundSetSlotBinding.class, ids.getAsInt())
                .encoder(ServerboundSetSlotBinding::encode).decoder(ServerboundSetSlotBinding::decode)
                .consumerMainThread(ServerboundSetSlotBinding::handle).add();
    }

    public static void open(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundOpenSlotBindings(SlotBindingManager.getBindings(player)));
    }

    public record ClientboundOpenSlotBindings(String[] bindings) {
        static void encode(ClientboundOpenSlotBindings packet, FriendlyByteBuf buf) {
            for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
                String value = i < packet.bindings.length && packet.bindings[i] != null ? packet.bindings[i] : "";
                buf.writeUtf(value, 256);
            }
        }
        static ClientboundOpenSlotBindings decode(FriendlyByteBuf buf) {
            String[] bindings = new String[ExtraHotbar.MAX_EXTRA];
            for (int i = 0; i < bindings.length; i++) bindings[i] = buf.readUtf(256);
            return new ClientboundOpenSlotBindings(bindings);
        }
        static void handle(ClientboundOpenSlotBindings packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> Minecraft.getInstance().setScreen(new SlotBindingConfiguratorScreen(packet.bindings))));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ServerboundSetSlotBinding(int extraIndex, String itemId) {
        static void encode(ServerboundSetSlotBinding packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.extraIndex);
            buf.writeUtf(packet.itemId == null ? "" : packet.itemId, 256);
        }
        static ServerboundSetSlotBinding decode(FriendlyByteBuf buf) {
            return new ServerboundSetSlotBinding(buf.readVarInt(), buf.readUtf(256));
        }
        static void handle(ServerboundSetSlotBinding packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) return;
                if (packet.extraIndex < 0 || packet.extraIndex >= ExtraHotbar.getAppliedCount(player)) return;
                if (!SlotBindingManager.isValidItemId(packet.itemId)) return;
                SlotBindingManager.setBinding(player, packet.extraIndex, packet.itemId);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
