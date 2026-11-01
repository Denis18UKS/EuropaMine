package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.SubmarineZoneScreen;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.submarines.SubmarineZoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Network bridge for the persistent 3D submarine construction-zone library. */
public final class SubmarineZonePackets {
    private SubmarineZonePackets() {}

    public static void register(SimpleChannel channel, IntSupplier nextId) {
        channel.messageBuilder(ClientboundOpenZones.class, nextId.getAsInt())
                .encoder(ClientboundOpenZones::encode).decoder(ClientboundOpenZones::decode)
                .consumerMainThread(ClientboundOpenZones::handle).add();
        channel.messageBuilder(ServerboundTeleportZone.class, nextId.getAsInt())
                .encoder(ServerboundTeleportZone::encode).decoder(ServerboundTeleportZone::decode)
                .consumerMainThread(ServerboundTeleportZone::handle).add();
        channel.messageBuilder(ServerboundGiveZoneTool.class, nextId.getAsInt())
                .encoder(ServerboundGiveZoneTool::encode).decoder(ServerboundGiveZoneTool::decode)
                .consumerMainThread(ServerboundGiveZoneTool::handle).add();
        channel.messageBuilder(ServerboundDeleteZone.class, nextId.getAsInt())
                .encoder(ServerboundDeleteZone::encode).decoder(ServerboundDeleteZone::decode)
                .consumerMainThread(ServerboundDeleteZone::handle).add();
    }

    public static void open(ServerPlayer player) {
        List<ZoneView> views = new ArrayList<>();
        for (SubmarineZoneData.Zone zone : SubmarineZoneData.get(player.serverLevel()).all()) {
            views.add(new ZoneView(zone.id(), zone.kind().id(), zone.kind().title(), zone.name(), zone.min(), zone.max()));
        }
        ModNetworking.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new ClientboundOpenZones(views));
    }

    public record ZoneView(UUID id, String kindId, String kindTitle, String name, BlockPos min, BlockPos max) {
        public BlockPos center() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2,
                    (min.getZ() + max.getZ()) / 2);
        }
    }

    public record ClientboundOpenZones(List<ZoneView> zones) {
        public static void encode(ClientboundOpenZones packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.zones.size());
            for (ZoneView zone : packet.zones) {
                buf.writeUUID(zone.id);
                buf.writeUtf(zone.kindId, 32);
                buf.writeUtf(zone.kindTitle, 64);
                buf.writeUtf(zone.name, 96);
                buf.writeBlockPos(zone.min);
                buf.writeBlockPos(zone.max);
            }
        }
        public static ClientboundOpenZones decode(FriendlyByteBuf buf) {
            int count = Math.min(4096, Math.max(0, buf.readVarInt()));
            List<ZoneView> zones = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                zones.add(new ZoneView(buf.readUUID(), buf.readUtf(32), buf.readUtf(64), buf.readUtf(96),
                        buf.readBlockPos(), buf.readBlockPos()));
            }
            return new ClientboundOpenZones(List.copyOf(zones));
        }
        public static void handle(ClientboundOpenZones packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SubmarineZoneScreen.open(packet.zones)));
            context.setPacketHandled(true);
        }
    }

    public record ServerboundTeleportZone(UUID id) {
        public static void encode(ServerboundTeleportZone packet, FriendlyByteBuf buf) { buf.writeUUID(packet.id); }
        public static ServerboundTeleportZone decode(FriendlyByteBuf buf) { return new ServerboundTeleportZone(buf.readUUID()); }
        public static void handle(ServerboundTeleportZone packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || (!player.isCreative() && !player.hasPermissions(2))) return;
                SubmarineZoneData.Zone zone = SubmarineZoneData.get(player.serverLevel()).get(packet.id);
                if (zone == null) return;
                BlockPos center = zone.center();
                player.teleportTo(center.getX() + 0.5D, zone.max().getY() + 1.2D, center.getZ() + 0.5D);
                player.displayClientMessage(Component.literal("Телепорт: " + zone.name()), true);
            });
            context.setPacketHandled(true);
        }
    }

    public record ServerboundGiveZoneTool(String kindId) {
        public static void encode(ServerboundGiveZoneTool packet, FriendlyByteBuf buf) { buf.writeUtf(packet.kindId, 32); }
        public static ServerboundGiveZoneTool decode(FriendlyByteBuf buf) { return new ServerboundGiveZoneTool(buf.readUtf(32)); }
        public static void handle(ServerboundGiveZoneTool packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || (!player.isCreative() && !player.hasPermissions(2))) return;
                SubmarineZoneData.Kind kind = SubmarineZoneData.Kind.byId(packet.kindId);
                ItemStack stack = switch (kind) {
                    case EXTERNAL_WALL -> ModItems.EXTERNAL_WALL_ZONE_TOOL.get().getDefaultInstance();
                    case INTERNAL_WALL -> ModItems.INTERNAL_WALL_ZONE_TOOL.get().getDefaultInstance();
                    case FLOOR -> ModItems.FLOOR_ZONE_TOOL.get().getDefaultInstance();
                };
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                player.displayClientMessage(Component.literal("Выдан инструмент: " + kind.title() + ". Выберите две точки."), true);
            });
            context.setPacketHandled(true);
        }
    }

    public record ServerboundDeleteZone(UUID id) {
        public static void encode(ServerboundDeleteZone packet, FriendlyByteBuf buf) { buf.writeUUID(packet.id); }
        public static ServerboundDeleteZone decode(FriendlyByteBuf buf) { return new ServerboundDeleteZone(buf.readUUID()); }
        public static void handle(ServerboundDeleteZone packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || (!player.isCreative() && !player.hasPermissions(2))) return;
                if (SubmarineZoneData.get(player.serverLevel()).remove(packet.id)) {
                    player.displayClientMessage(Component.literal("Зона удалена."), true);
                    open(player);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
