package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.client.PowerScreens;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.item.GuiBinderItem;
import com.z_mods.barotrauma.power.PowerSystem;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Network protocol for GUI selection, machine screens and all server-authoritative actions. */
public final class PowerPackets {
    private PowerPackets() {
    }

    public static void register(SimpleChannel channel, IntSupplier ids) {
        channel.messageBuilder(ClientboundOpenBinder.class, ids.getAsInt())
                .encoder(ClientboundOpenBinder::encode).decoder(ClientboundOpenBinder::decode)
                .consumerMainThread(ClientboundOpenBinder::handle).add();
        channel.messageBuilder(ServerboundSelectGui.class, ids.getAsInt())
                .encoder(ServerboundSelectGui::encode).decoder(ServerboundSelectGui::decode)
                .consumerMainThread(ServerboundSelectGui::handle).add();
        channel.messageBuilder(ClientboundOpenMachine.class, ids.getAsInt())
                .encoder(ClientboundOpenMachine::encode).decoder(ClientboundOpenMachine::decode)
                .consumerMainThread(ClientboundOpenMachine::handle).add();
        channel.messageBuilder(ClientboundMachineState.class, ids.getAsInt())
                .encoder(ClientboundMachineState::encode).decoder(ClientboundMachineState::decode)
                .consumerMainThread(ClientboundMachineState::handle).add();
        channel.messageBuilder(ServerboundMachineAction.class, ids.getAsInt())
                .encoder(ServerboundMachineAction::encode).decoder(ServerboundMachineAction::decode)
                .consumerMainThread(ServerboundMachineAction::handle).add();
        channel.messageBuilder(ServerboundOpenLookedAt.class, ids.getAsInt())
                .encoder(ServerboundOpenLookedAt::encode).decoder(ServerboundOpenLookedAt::decode)
                .consumerMainThread(ServerboundOpenLookedAt::handle).add();
    }

    public static void sendOpenBinder(ServerPlayer player, String selected) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundOpenBinder(selected));
    }

    public static void sendOpenMachine(ServerPlayer player, BlockPos pos, String guiId, boolean technical) {
        if (!(player.level() instanceof ServerLevel level)) return;
        PowerWorldData data = PowerWorldData.get(level);
        CompoundTag state = data.stateTag(pos);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundOpenMachine(pos, guiId, technical, state,
                        PowerSystem.isTraitor(player), PowerSystem.electronicsSkill(player)));
    }

    public static void sendState(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundMachineState(pos, PowerWorldData.get(level).stateTag(pos)));
    }

    public record ClientboundOpenBinder(String selected) {
        static void encode(ClientboundOpenBinder packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.selected, 128);
        }

        static ClientboundOpenBinder decode(FriendlyByteBuf buffer) {
            return new ClientboundOpenBinder(buffer.readUtf(128));
        }

        static void handle(ClientboundOpenBinder packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> PowerScreens.openBinder(packet.selected)));
            context.get().setPacketHandled(true);
        }
    }

    public record ServerboundSelectGui(String guiId) {
        static void encode(ServerboundSelectGui packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.guiId, 128);
        }

        static ServerboundSelectGui decode(FriendlyByteBuf buffer) {
            return new ServerboundSelectGui(buffer.readUtf(128));
        }

        static void handle(ServerboundSelectGui packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player == null || !PowerSystem.isKnownGui(packet.guiId)) return;
                ItemStack main = player.getMainHandItem();
                ItemStack off = player.getOffhandItem();
                ItemStack target = main.getItem() instanceof GuiBinderItem ? main
                        : off.getItem() instanceof GuiBinderItem ? off : ItemStack.EMPTY;
                if (!target.isEmpty()) {
                    GuiBinderItem.select(target, packet.guiId);
                    player.getInventory().setChanged();
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record ClientboundOpenMachine(BlockPos pos, String guiId, boolean technical, CompoundTag state,
                                         boolean traitor, int electronicsSkill) {
        static void encode(ClientboundOpenMachine packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeUtf(packet.guiId, 128);
            buffer.writeBoolean(packet.technical);
            buffer.writeNbt(packet.state);
            buffer.writeBoolean(packet.traitor);
            buffer.writeVarInt(packet.electronicsSkill);
        }

        static ClientboundOpenMachine decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            String guiId = buffer.readUtf(128);
            boolean technical = buffer.readBoolean();
            CompoundTag state = buffer.readNbt();
            boolean traitor = buffer.readBoolean();
            int electronicsSkill = buffer.readVarInt();
            return new ClientboundOpenMachine(pos, guiId, technical,
                    state == null ? new CompoundTag() : state, traitor, electronicsSkill);
        }

        static void handle(ClientboundOpenMachine packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> PowerScreens.openMachine(packet.pos, packet.guiId, packet.technical,
                            packet.state, packet.traitor, packet.electronicsSkill)));
            context.get().setPacketHandled(true);
        }
    }

    public record ClientboundMachineState(BlockPos pos, CompoundTag state) {
        static void encode(ClientboundMachineState packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeNbt(packet.state);
        }

        static ClientboundMachineState decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            CompoundTag state = buffer.readNbt();
            return new ClientboundMachineState(pos, state == null ? new CompoundTag() : state);
        }

        static void handle(ClientboundMachineState packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> PowerScreens.applyState(packet.pos, packet.state)));
            context.get().setPacketHandled(true);
        }
    }

    public record ServerboundMachineAction(BlockPos pos, String action, int value) {
        static void encode(ServerboundMachineAction packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeUtf(packet.action, 64);
            buffer.writeVarInt(packet.value);
        }

        static ServerboundMachineAction decode(FriendlyByteBuf buffer) {
            return new ServerboundMachineAction(buffer.readBlockPos(), buffer.readUtf(64), buffer.readVarInt());
        }

        static void handle(ServerboundMachineAction packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> handleAction(player, packet));
            context.get().setPacketHandled(true);
        }
    }

    private static void handleAction(ServerPlayer player, ServerboundMachineAction packet) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        if (player.distanceToSqr(packet.pos.getX() + 0.5D, packet.pos.getY() + 0.5D, packet.pos.getZ() + 0.5D) > 144.0D
                && !player.isCreative()) return;

        PowerWorldData data = PowerWorldData.get(level);
        String guiId = data.guiAt(packet.pos);
        if (guiId == null) return;
        PowerWorldData.MachineState state = data.machineOrCreate(packet.pos, guiId);
        switch (packet.action) {
            case "request" -> {
                sendState(player, packet.pos);
                return;
            }
            case "toggle" -> {
                if (PowerWorldData.REACTOR_GUI.equals(guiId)) state.toggleEnabled();
            }
            case "auto" -> {
                if (PowerWorldData.REACTOR_GUI.equals(guiId)) state.toggleAutomatic();
            }
            case "plus" -> {
                if (PowerWorldData.REACTOR_GUI.equals(guiId)) state.adjustTarget(5);
            }
            case "minus" -> {
                if (PowerWorldData.REACTOR_GUI.equals(guiId)) state.adjustTarget(-5);
            }
            case "fuel_insert" -> insertFuel(player, state, packet.value, false);
            case "fuel_overload" -> insertFuel(player, state, packet.value, true);
            case "fuel_extract" -> extractFuel(player, state, packet.value);
            case "repair" -> repairPanel(player, level, packet.pos, state);
            case "sabotage" -> sabotagePanel(player, level, packet.pos, state);
            default -> {
                return;
            }
        }
        data.setDirty();
        sendState(player, packet.pos);
    }

    private static void insertFuel(ServerPlayer player, PowerWorldData.MachineState state, int rawSlot, boolean overload) {
        int slot = Mth.clamp(rawSlot, 0, 3);
        int inventorySlot = findFuelRod(player);
        if (inventorySlot < 0) {
            player.displayClientMessage(Component.literal("В инвентаре нет топливного стержня."), true);
            return;
        }
        if (state.fuel(slot) > 0) {
            if (overload) {
                consumeFuelRod(player, inventorySlot);
                state.triggerOverfuel();
                player.displayClientMessage(Component.literal("ОПАСНОСТЬ: в занятую ячейку загружен второй стержень!"), true);
            } else {
                player.displayClientMessage(Component.literal(
                        "Ячейка занята. ЛКМ извлекает стержень, ПКМ запускает аварийную перегрузку."), true);
            }
            return;
        }
        ItemStack rod = player.getInventory().getItem(inventorySlot);
        int remaining = Math.max(1, rod.getMaxDamage() - rod.getDamageValue());
        consumeFuelRod(player, inventorySlot);
        state.setFuel(slot, remaining);
    }

    private static void extractFuel(ServerPlayer player, PowerWorldData.MachineState state, int rawSlot) {
        int slot = Mth.clamp(rawSlot, 0, 3);
        int remaining = state.fuel(slot);
        if (remaining <= 0) return;
        ItemStack rod = new ItemStack(ModItems.REACTOR_FUEL_ROD.get());
        rod.setDamageValue(Mth.clamp(rod.getMaxDamage() - remaining, 0, rod.getMaxDamage() - 1));
        state.setFuel(slot, 0);
        if (!player.getInventory().add(rod)) player.drop(rod, false);
    }

    private static int findFuelRod(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.REACTOR_FUEL_ROD.get())) return i;
        }
        return -1;
    }

    private static void consumeFuelRod(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        stack.shrink(1);
        player.getInventory().setChanged();
    }

    private static void repairPanel(ServerPlayer player, ServerLevel level, BlockPos pos,
                                    PowerWorldData.MachineState state) {
        if (!PowerWorldData.ELECTRICAL_PANEL_GUI.equals(state.guiId()) || state.health() >= 100) return;
        if (!PowerSystem.hasScrewdriver(player)) {
            player.displayClientMessage(Component.literal("Для ремонта нужна отвёртка."), true);
            return;
        }
        if (PowerSystem.electronicsSkill(player) < 55) {
            player.displayClientMessage(Component.literal("Требуется навык Электроника: 55."), true);
            return;
        }
        state.repair(35);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.65F, 1.35F);
    }

    private static void sabotagePanel(ServerPlayer player, ServerLevel level, BlockPos pos,
                                      PowerWorldData.MachineState state) {
        if (!PowerWorldData.ELECTRICAL_PANEL_GUI.equals(state.guiId()) || !PowerSystem.isTraitor(player)) return;
        state.sabotage(50);
        level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 1.0F, 0.7F);
        player.displayClientMessage(Component.literal("Саботаж выполнен."), true);
    }

    public record ServerboundOpenLookedAt(BlockPos pos) {
        static void encode(ServerboundOpenLookedAt packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.pos);
        }

        static ServerboundOpenLookedAt decode(FriendlyByteBuf buffer) {
            return new ServerboundOpenLookedAt(buffer.readBlockPos());
        }

        static void handle(ServerboundOpenLookedAt packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player == null || !(player.level() instanceof ServerLevel level)) return;
                if (player.distanceToSqr(packet.pos.getX() + 0.5D, packet.pos.getY() + 0.5D, packet.pos.getZ() + 0.5D) > 100.0D
                        && !player.isCreative()) return;
                PowerWorldData data = PowerWorldData.get(level);
                if (!PowerWorldData.ELECTRICAL_PANEL_GUI.equals(data.guiAt(packet.pos))) return;
                data.machineOrCreate(packet.pos, PowerWorldData.ELECTRICAL_PANEL_GUI);
                sendOpenMachine(player, packet.pos, PowerWorldData.ELECTRICAL_PANEL_GUI,
                        PowerSystem.hasScrewdriver(player));
            });
            context.get().setPacketHandled(true);
        }
    }
}
