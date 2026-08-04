package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.NavigationTerminalScreen;
import net.minecraft.client.Minecraft;
import com.z_mods.barotrauma.navigation.NavigationSystem;
import com.z_mods.barotrauma.navigation.NavigationWorldData;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Server-authoritative protocol for the navigation terminal. */
public final class NavigationPackets {
    private NavigationPackets() {
    }

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundOpenNavigation.class, ids.getAsInt())
                .encoder(ClientboundOpenNavigation::encode).decoder(ClientboundOpenNavigation::decode)
                .consumerMainThread(ClientboundOpenNavigation::handle).add();
        channel.messageBuilder(ClientboundNavigationState.class, ids.getAsInt())
                .encoder(ClientboundNavigationState::encode).decoder(ClientboundNavigationState::decode)
                .consumerMainThread(ClientboundNavigationState::handle).add();
        channel.messageBuilder(ClientboundVesselMotion.class, ids.getAsInt())
                .encoder(ClientboundVesselMotion::encode).decoder(ClientboundVesselMotion::decode)
                .consumerMainThread(ClientboundVesselMotion::handle).add();
        channel.messageBuilder(ServerboundNavigationAction.class, ids.getAsInt())
                .encoder(ServerboundNavigationAction::encode).decoder(ServerboundNavigationAction::decode)
                .consumerMainThread(ServerboundNavigationAction::handle).add();
    }

    public static void sendOpen(ServerPlayer player, BlockPos terminalPos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        NavigationWorldData data = NavigationWorldData.get(level);
        BlockPos resolved = data.resolveTerminalPos(terminalPos);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundOpenNavigation(resolved, data.stateTag(level, resolved)));
    }

    public static void sendState(ServerPlayer player, BlockPos terminalPos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        NavigationWorldData data = NavigationWorldData.get(level);
        BlockPos resolved = data.resolveTerminalPos(terminalPos);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundNavigationState(resolved, data.stateTag(level, resolved)));
    }

    public record ClientboundOpenNavigation(BlockPos terminalPos, CompoundTag state) {
        static void encode(ClientboundOpenNavigation packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.terminalPos);
            buffer.writeNbt(packet.state);
        }

        static ClientboundOpenNavigation decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            CompoundTag tag = buffer.readNbt();
            return new ClientboundOpenNavigation(pos, tag == null ? new CompoundTag() : tag);
        }

        static void handle(ClientboundOpenNavigation packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> NavigationTerminalScreen.open(packet.terminalPos, packet.state)));
            context.get().setPacketHandled(true);
        }
    }

    public record ClientboundNavigationState(BlockPos terminalPos, CompoundTag state) {
        static void encode(ClientboundNavigationState packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.terminalPos);
            buffer.writeNbt(packet.state);
        }

        static ClientboundNavigationState decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            CompoundTag tag = buffer.readNbt();
            return new ClientboundNavigationState(pos, tag == null ? new CompoundTag() : tag);
        }

        static void handle(ClientboundNavigationState packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> NavigationTerminalScreen.applyState(packet.terminalPos, packet.state)));
            context.get().setPacketHandled(true);
        }
    }

    public static void sendVesselMotion(ServerPlayer player, Vec3 delta) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundVesselMotion(delta.x, delta.y, delta.z));
    }

    public record ClientboundVesselMotion(double x, double y, double z) {
        static void encode(ClientboundVesselMotion packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.x);
            buffer.writeDouble(packet.y);
            buffer.writeDouble(packet.z);
        }

        static ClientboundVesselMotion decode(FriendlyByteBuf buffer) {
            return new ClientboundVesselMotion(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        static void handle(ClientboundVesselMotion packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null) return;
                minecraft.player.setPos(minecraft.player.getX() + packet.x,
                        minecraft.player.getY() + packet.y,
                        minecraft.player.getZ() + packet.z);
                minecraft.player.fallDistance = 0.0F;
            }));
            context.get().setPacketHandled(true);
        }
    }

    public record ServerboundNavigationAction(BlockPos terminalPos, String action, int value, float x, float y) {
        public ServerboundNavigationAction(BlockPos terminalPos, String action, int value) {
            this(terminalPos, action, value, 0.0F, 0.0F);
        }

        static void encode(ServerboundNavigationAction packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.terminalPos);
            buffer.writeUtf(packet.action, 64);
            buffer.writeVarInt(packet.value);
            buffer.writeFloat(packet.x);
            buffer.writeFloat(packet.y);
        }

        static ServerboundNavigationAction decode(FriendlyByteBuf buffer) {
            return new ServerboundNavigationAction(buffer.readBlockPos(), buffer.readUtf(64),
                    buffer.readVarInt(), buffer.readFloat(), buffer.readFloat());
        }

        static void handle(ServerboundNavigationAction packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> handleAction(player, packet));
            context.get().setPacketHandled(true);
        }
    }

    private static void handleAction(ServerPlayer player, ServerboundNavigationAction packet) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        NavigationWorldData data = NavigationWorldData.get(level);
        BlockPos terminalPos = data.resolveTerminalPos(packet.terminalPos);
        if (player.distanceToSqr(terminalPos.getX() + 0.5D, terminalPos.getY() + 0.5D, terminalPos.getZ() + 0.5D) > 400.0D
                && !player.isCreative()) return;
        if (!NavigationWorldData.NAVIGATION_GUI.equals(PowerWorldData.get(level).guiAt(terminalPos))) return;

        NavigationWorldData.TerminalState terminal = data.terminalOrCreate(terminalPos);
        switch (packet.action) {
            case "request" -> {
                sendState(player, terminalPos);
                return;
            }
            case "toggle_sonar" -> terminal.toggleSonar();
            case "toggle_directional" -> terminal.toggleDirectional();
            case "toggle_autopilot" -> {
                terminal.toggleAutopilot();
                NavigationWorldData.VesselState vessel = data.vessel(terminal.vesselId());
                if (terminal.autopilot() && terminal.selectedDestination() == 0 && vessel != null) {
                    terminal.setMaintainPos(vessel.anchor());
                }
            }
            case "zoom" -> terminal.setZoom(packet.value);
            case "select" -> {
                terminal.selectDestination(packet.value);
                NavigationWorldData.VesselState vessel = data.vessel(terminal.vesselId());
                if (packet.value == 0 && vessel != null) terminal.setMaintainPos(vessel.anchor());
            }
            case "manual" -> terminal.setManual(packet.x, packet.y);
            case "beam" -> terminal.setBeamAngle(packet.x);
            case "shutdown_reactor" -> {
                int count = NavigationSystem.shutdownReactors(level, terminalPos);
                level.playSound(null, terminalPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.55F);
                player.displayClientMessage(Component.literal(count > 0
                        ? "Подключённые реакторы остановлены: " + count
                        : "Подключённый работающий реактор не найден."), true);
            }
            default -> {
                return;
            }
        }
        data.setDirty();
        sendState(player, terminalPos);
    }
}
