package com.z_mods.barotrauma.power;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.item.GuiBinderItem;
import com.z_mods.barotrauma.item.NavigationLinkerItem;
import com.z_mods.barotrauma.item.SubmarineBuilderItem;
import com.z_mods.barotrauma.item.WireToolItem;
import com.z_mods.barotrauma.navigation.NavigationSystem;
import com.z_mods.barotrauma.navigation.NavigationWorldData;
import com.z_mods.barotrauma.network.PanelNetworkSync;
import com.z_mods.barotrauma.network.PowerPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

/** Server-side routing, validation and ticking for the configurable GUI/power system. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PowerSystem {
    public static final List<GuiEntry> GUI_CATALOG = List.of(
            new GuiEntry(PowerWorldData.REACTOR_GUI, "Ядерный реактор"),
            new GuiEntry(PowerWorldData.ELECTRICAL_PANEL_GUI, "Электрощиток"),
            new GuiEntry("settings_panel", "Панель настроек игры"),
            new GuiEntry("vent", "Интерактивная вентиляция"),
            new GuiEntry(NavigationWorldData.NAVIGATION_GUI, "Навигационный терминал"),
            new GuiEntry("structure_config", "Настройка конструкции")
    );

    private PowerSystem() {
    }

    public static boolean isKnownGui(String id) {
        return GUI_CATALOG.stream().anyMatch(entry -> entry.id.equals(id));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            PowerWorldData.get(serverLevel).tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof GuiBinderItem || held.getItem() instanceof WireToolItem
                || held.getItem() instanceof NavigationLinkerItem || held.getItem() instanceof SubmarineBuilderItem) return;

        BlockPos pos = event.getPos();
        String guiId = PowerWorldData.get(level).guiAt(pos);
        if (guiId == null || guiId.isBlank()) return;

        event.setUseBlock(Event.Result.DENY);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        openBoundGui(player, level, pos, guiId, event.getHitVec());
    }

    public static void openBoundGui(ServerPlayer player, ServerLevel level, BlockPos pos, String guiId,
                                    BlockHitResult hit) {
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 100.0D
                && !player.isCreative()) {
            return;
        }
        PowerWorldData data = PowerWorldData.get(level);
        switch (guiId) {
            case PowerWorldData.REACTOR_GUI -> {
                data.machineOrCreate(pos, PowerWorldData.REACTOR_GUI);
                PowerPackets.sendOpenMachine(player, pos, guiId, false);
            }
            case PowerWorldData.ELECTRICAL_PANEL_GUI -> {
                data.machineOrCreate(pos, PowerWorldData.ELECTRICAL_PANEL_GUI);
                PowerPackets.sendOpenMachine(player, pos, guiId, false);
            }
            case "settings_panel" -> PanelNetworkSync.openSettings(player);
            case NavigationWorldData.NAVIGATION_GUI -> NavigationSystem.open(player, level, pos);
            case "vent", "structure_config" -> openNativeBlockGui(player, level, pos, hit);
            default -> player.displayClientMessage(Component.literal("Неизвестный GUI: " + guiId), true);
        }
    }

    private static void openNativeBlockGui(ServerPlayer player, ServerLevel level, BlockPos pos, BlockHitResult hit) {
        BlockState state = level.getBlockState(pos);
        InteractionResult result = state.use(level, player, InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction()) {
            player.displayClientMessage(Component.literal("Этот GUI требует родной блок соответствующего типа."), true);
        }
    }

    public static boolean hasScrewdriver(ServerPlayer player) {
        return player.isCreative()
                || player.getMainHandItem().is(ModItems.SCREWDIN.get())
                || player.getOffhandItem().is(ModItems.SCREWDIN.get());
    }

    public static int electronicsSkill(ServerPlayer player) {
        if (player.isCreative()) return 100;
        return Math.max(0, player.getPersistentData().getInt("barotrauma_electronics"));
    }

    public static boolean isTraitor(ServerPlayer player) {
        if (player.getPersistentData().getBoolean("barotrauma_traitor")) return true;
        for (String tag : player.getTags()) {
            String normalized = tag.toLowerCase(Locale.ROOT);
            if (normalized.equals("traitor") || normalized.equals("barotrauma_traitor")
                    || normalized.equals("предатель")) return true;
        }
        if (player.getTeam() != null) {
            String team = player.getTeam().getName().toLowerCase(Locale.ROOT);
            return team.contains("traitor") || team.contains("предател");
        }
        return false;
    }

    public record GuiEntry(String id, String title) {
    }
}
