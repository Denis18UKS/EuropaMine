package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.ClientPacketHandlers;
import com.z_mods.barotrauma.panel.PanelPhotoStorage;
import com.z_mods.barotrauma.panel.PanelSettings;
import com.z_mods.barotrauma.panel.PanelSettingsSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class PanelPackets {
    private PanelPackets() {
    }

    public record ClientboundOpenPanel(CompoundTag settings, boolean editable) {
        public static void encode(ClientboundOpenPanel packet, FriendlyByteBuf buffer) {
            buffer.writeNbt(packet.settings);
            buffer.writeBoolean(packet.editable);
        }
        public static ClientboundOpenPanel decode(FriendlyByteBuf buffer) {
            return new ClientboundOpenPanel(orEmpty(buffer.readNbt()), buffer.readBoolean());
        }
        public static void handle(ClientboundOpenPanel packet, Supplier<NetworkEvent.Context> supplier) {
            client(supplier, () -> ClientPacketHandlers.openPanel(packet.settings, packet.editable));
        }
    }

    public record ClientboundOpenCamera(CompoundTag settings, boolean editable) {
        public static void encode(ClientboundOpenCamera packet, FriendlyByteBuf buffer) {
            buffer.writeNbt(packet.settings);
            buffer.writeBoolean(packet.editable);
        }
        public static ClientboundOpenCamera decode(FriendlyByteBuf buffer) {
            return new ClientboundOpenCamera(orEmpty(buffer.readNbt()), buffer.readBoolean());
        }
        public static void handle(ClientboundOpenCamera packet, Supplier<NetworkEvent.Context> supplier) {
            client(supplier, () -> ClientPacketHandlers.openCamera(packet.settings, packet.editable));
        }
    }

    public record ClientboundSettings(CompoundTag settings) {
        public static void encode(ClientboundSettings packet, FriendlyByteBuf buffer) { buffer.writeNbt(packet.settings); }
        public static ClientboundSettings decode(FriendlyByteBuf buffer) { return new ClientboundSettings(orEmpty(buffer.readNbt())); }
        public static void handle(ClientboundSettings packet, Supplier<NetworkEvent.Context> supplier) {
            client(supplier, () -> ClientPacketHandlers.applySettings(packet.settings));
        }
    }

    public record ClientboundPhoto(int slot, byte[] png) {
        public static void encode(ClientboundPhoto packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.slot);
            buffer.writeByteArray(packet.png);
        }
        public static ClientboundPhoto decode(FriendlyByteBuf buffer) {
            return new ClientboundPhoto(buffer.readVarInt(), buffer.readByteArray(PanelPhotoStorage.MAX_PHOTO_BYTES));
        }
        public static void handle(ClientboundPhoto packet, Supplier<NetworkEvent.Context> supplier) {
            client(supplier, () -> ClientPacketHandlers.applyPhoto(packet.slot, packet.png));
        }
    }

    public record ClientboundPanelChat(String line, boolean clear) {
        public static void encode(ClientboundPanelChat packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.line, 256);
            buffer.writeBoolean(packet.clear);
        }
        public static ClientboundPanelChat decode(FriendlyByteBuf buffer) {
            return new ClientboundPanelChat(buffer.readUtf(256), buffer.readBoolean());
        }
        public static void handle(ClientboundPanelChat packet, Supplier<NetworkEvent.Context> supplier) {
            client(supplier, () -> ClientPacketHandlers.applyPanelChat(packet.line, packet.clear));
        }
    }

    public record ServerboundSettings(CompoundTag settings) {
        public static void encode(ServerboundSettings packet, FriendlyByteBuf buffer) { buffer.writeNbt(packet.settings); }
        public static ServerboundSettings decode(FriendlyByteBuf buffer) { return new ServerboundSettings(orEmpty(buffer.readNbt())); }
        public static void handle(ServerboundSettings packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !PanelNetworkSync.canEdit(player)) return;
                PanelSettings settings = PanelSettings.fromTag(packet.settings);
                PanelSettingsSavedData.get(player.server).setSettings(settings);
                PanelNetworkSync.broadcastSettings(player.server);
            });
            context.setPacketHandled(true);
        }
    }

    public record ServerboundPhotoUpload(int slot, byte[] png) {
        public static void encode(ServerboundPhotoUpload packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.slot);
            buffer.writeByteArray(packet.png);
        }
        public static ServerboundPhotoUpload decode(FriendlyByteBuf buffer) {
            return new ServerboundPhotoUpload(buffer.readVarInt(), buffer.readByteArray(PanelPhotoStorage.MAX_PHOTO_BYTES));
        }
        public static void handle(ServerboundPhotoUpload packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !PanelNetworkSync.canEdit(player)) return;
                if (PanelPhotoStorage.save(packet.slot, packet.png)) {
                    PanelNetworkSync.broadcastPhoto(player.server, packet.slot, packet.png);
                    player.displayClientMessage(Component.translatable("message.barotrauma.photo_saved", packet.slot + 1), true);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record ServerboundPhotoDelete(int slot, boolean all) {
        public static void encode(ServerboundPhotoDelete packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.slot);
            buffer.writeBoolean(packet.all);
        }
        public static ServerboundPhotoDelete decode(FriendlyByteBuf buffer) {
            return new ServerboundPhotoDelete(buffer.readVarInt(), buffer.readBoolean());
        }
        public static void handle(ServerboundPhotoDelete packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !PanelNetworkSync.canEdit(player)) return;
                if (packet.all) {
                    PanelPhotoStorage.deleteAll();
                    for (int slot = 0; slot < PanelSettings.PHOTO_SLOTS; slot++) {
                        PanelNetworkSync.broadcastPhoto(player.server, slot, new byte[0]);
                    }
                } else if (PanelPhotoStorage.validSlot(packet.slot)) {
                    PanelPhotoStorage.delete(packet.slot);
                    PanelNetworkSync.broadcastPhoto(player.server, packet.slot, new byte[0]);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record ServerboundPanelChat(String message) {
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

        public static void encode(ServerboundPanelChat packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.message, 160);
        }
        public static ServerboundPanelChat decode(FriendlyByteBuf buffer) {
            return new ServerboundPanelChat(buffer.readUtf(160));
        }
        public static void handle(ServerboundPanelChat packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                String message = cleanChat(packet.message);
                if (message.isBlank()) return;
                String line = "[" + LocalTime.now().format(TIME) + "] "
                        + player.getGameProfile().getName() + ": " + message;
                PanelSettingsSavedData.get(player.server).addChatLine(line);
                PanelNetworkSync.broadcastChat(player.server, line);
                player.server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
            });
            context.setPacketHandled(true);
        }

        private static String cleanChat(String value) {
            if (value == null) return "";
            String cleaned = value.replaceAll("[\\p{Cntrl}]", "").strip();
            return cleaned.substring(0, Math.min(160, cleaned.length()));
        }
    }

    public record ServerboundStartSession() {
        public static void encode(ServerboundStartSession packet, FriendlyByteBuf buffer) { }
        public static ServerboundStartSession decode(FriendlyByteBuf buffer) { return new ServerboundStartSession(); }
        public static void handle(ServerboundStartSession packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !PanelNetworkSync.canEdit(player)) return;
                PanelSettings settings = PanelSettingsSavedData.get(player.server).getSettings();
                int players = player.server.getPlayerList().getPlayerCount();
                if (players < settings.minimumPlayers) {
                    player.displayClientMessage(Component.translatable(
                            "message.barotrauma.not_enough_players", settings.minimumPlayers), false);
                    return;
                }
                player.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                        "message.barotrauma.session_started", PanelSettings.SUBMARINES.get(settings.submarine)), false);
            });
            context.setPacketHandled(true);
        }
    }

    private static CompoundTag orEmpty(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    private static void client(Supplier<NetworkEvent.Context> supplier, Runnable action) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> action.run()));
        context.setPacketHandled(true);
    }
}
