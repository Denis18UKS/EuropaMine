package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.HotbarLayoutScreen;
import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.hotbar.ExtraHotbarStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
        channel.messageBuilder(ServerboundSelectExtra.class, ids.getAsInt())
                .encoder(ServerboundSelectExtra::encode).decoder(ServerboundSelectExtra::decode)
                .consumerMainThread(ServerboundSelectExtra::handle).add();
    }

    public static void open(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundOpenHotbarLayout());
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        ItemStack[] stacks = new ItemStack[ExtraHotbar.MAX_EXTRA];
        for (int i = 0; i < stacks.length; i++) stacks[i] = ExtraHotbar.getStack(player, i).copy();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundHotbarSync(ExtraHotbar.getAppliedCount(player), ExtraHotbar.getSelectedExtra(player), stacks));
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

    public record ClientboundHotbarSync(int count, int selectedExtra, ItemStack[] stacks) {
        static void encode(ClientboundHotbarSync packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.count);
            buf.writeVarInt(packet.selectedExtra + 1);
            for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
                ItemStack stack = i < packet.stacks.length ? packet.stacks[i] : ItemStack.EMPTY;
                buf.writeItem(stack);
            }
        }

        static ClientboundHotbarSync decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            int selected = buf.readVarInt() - 1;
            ItemStack[] stacks = new ItemStack[ExtraHotbar.MAX_EXTRA];
            for (int i = 0; i < stacks.length; i++) stacks[i] = buf.readItem();
            return new ClientboundHotbarSync(count, selected, stacks);
        }

        static void handle(ClientboundHotbarSync packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ExtraHotbar.setClientAppliedCount(packet.count);
                ExtraHotbar.setClientSelectedExtra(packet.selectedExtra);
                if (Minecraft.getInstance().player == null) return;
                ExtraHotbarStorage storage = ExtraHotbar.storage(Minecraft.getInstance().player);
                if (storage == null) return;
                for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
                    storage.setStackInSlot(i, packet.stacks[i].copy());
                }
            }));
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
                sync(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ServerboundSelectExtra(int extraIndex) {
        static void encode(ServerboundSelectExtra packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.extraIndex + 1); }
        static ServerboundSelectExtra decode(FriendlyByteBuf buf) { return new ServerboundSelectExtra(buf.readVarInt() - 1); }
        static void handle(ServerboundSelectExtra packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) return;
                ExtraHotbar.setSelectedExtra(player, packet.extraIndex);
                player.resetLastActionTime();
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
