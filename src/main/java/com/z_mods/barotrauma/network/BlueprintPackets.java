package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.SubmarineBlueprintScreen;
import com.z_mods.barotrauma.navigation.NavigationWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class BlueprintPackets {
    private BlueprintPackets() {}

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundOpenBlueprint.class, ids.getAsInt())
                .encoder(ClientboundOpenBlueprint::encode).decoder(ClientboundOpenBlueprint::decode)
                .consumerMainThread(ClientboundOpenBlueprint::handle).add();
        channel.messageBuilder(ServerboundBuildBlueprint.class, ids.getAsInt())
                .encoder(ServerboundBuildBlueprint::encode).decoder(ServerboundBuildBlueprint::decode)
                .consumerMainThread(ServerboundBuildBlueprint::handle).add();
    }

    public static void open(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundOpenBlueprint());
    }

    public record ClientboundOpenBlueprint() {
        static void encode(ClientboundOpenBlueprint p, FriendlyByteBuf b) {}
        static ClientboundOpenBlueprint decode(FriendlyByteBuf b) { return new ClientboundOpenBlueprint(); }
        static void handle(ClientboundOpenBlueprint p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> Minecraft.getInstance().setScreen(new SubmarineBlueprintScreen())));
            c.get().setPacketHandled(true);
        }
    }

    public record ServerboundBuildBlueprint(int width, int height, byte[] cells) {
        static void encode(ServerboundBuildBlueprint p, FriendlyByteBuf b) {
            b.writeVarInt(p.width); b.writeVarInt(p.height); b.writeByteArray(p.cells);
        }
        static ServerboundBuildBlueprint decode(FriendlyByteBuf b) {
            return new ServerboundBuildBlueprint(b.readVarInt(), b.readVarInt(), b.readByteArray(2048));
        }
        static void handle(ServerboundBuildBlueprint p, Supplier<NetworkEvent.Context> c) {
            ServerPlayer player = c.get().getSender();
            c.get().enqueueWork(() -> build(player, p));
            c.get().setPacketHandled(true);
        }
    }

    private static void build(ServerPlayer player, ServerboundBuildBlueprint packet) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        int width = packet.width;
        int height = packet.height;
        if (width < 1 || width > 48 || height < 1 || height > 24 || packet.cells.length != width * height) return;
        int pixels = 0;
        for (byte cell : packet.cells) if (cell != 0) pixels++;
        if (pixels == 0 || pixels > 1152) return;

        Direction facing = player.getDirection();
        Direction horizontal = facing.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        BlockPos center = player.blockPosition().relative(facing, 4).above(1);
        BlockPos minPlaced = null;
        BlockPos maxPlaced = null;
        BlockState material = Blocks.IRON_BLOCK.defaultBlockState();
        int placed = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (packet.cells[row * width + col] == 0) continue;
                int horizontalOffset = col - width / 2;
                int verticalOffset = height - 1 - row;
                BlockPos pos = center.relative(horizontal, horizontalOffset).above(verticalOffset);
                if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) continue;
                BlockState existing = level.getBlockState(pos);
                if (!existing.isAir() && existing.getFluidState().isEmpty() && !existing.canBeReplaced()) continue;
                if (level.setBlock(pos, material, 3)) {
                    placed++;
                    minPlaced = minPlaced == null ? pos : new BlockPos(Math.min(minPlaced.getX(), pos.getX()),
                            Math.min(minPlaced.getY(), pos.getY()), Math.min(minPlaced.getZ(), pos.getZ()));
                    maxPlaced = maxPlaced == null ? pos : new BlockPos(Math.max(maxPlaced.getX(), pos.getX()),
                            Math.max(maxPlaced.getY(), pos.getY()), Math.max(maxPlaced.getZ(), pos.getZ()));
                }
            }
        }
        if (placed > 0 && minPlaced != null && maxPlaced != null) {
            NavigationWorldData.VesselState vessel = NavigationWorldData.get(level)
                    .registerMultiblock(minPlaced, maxPlaced, "Нарисованная подлодка");
            player.displayClientMessage(Component.literal("Построено блоков: " + placed
                    + (vessel == null ? ". Не удалось зарегистрировать конструкцию." : ". Подлодка зарегистрирована.")), true);
        } else {
            player.displayClientMessage(Component.literal("Нет свободного места для блоков чертежа."), true);
        }
    }
}
