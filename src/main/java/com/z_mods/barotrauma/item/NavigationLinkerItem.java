package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.navigation.NavigationWorldData;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Two-stage tool: select a controlled vessel/block, then link it to a bound navigation GUI. */
public final class NavigationLinkerItem extends Item {
    private static final String MODE = "NavigationMode";
    private static final String VESSEL = "NavigationVessel";
    private static final String VESSEL_NAME = "NavigationVesselName";

    public NavigationLinkerItem(Properties properties) {
        super(properties);
    }

    private static boolean allowed(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    public static NavigationWorldData.VesselMode mode(ItemStack stack) {
        try {
            return NavigationWorldData.VesselMode.valueOf(stack.getOrCreateTag().getString(MODE));
        } catch (IllegalArgumentException ignored) {
            return NavigationWorldData.VesselMode.MULTIBLOCK;
        }
    }

    private static void toggleMode(ItemStack stack) {
        NavigationWorldData.VesselMode next = mode(stack) == NavigationWorldData.VesselMode.MULTIBLOCK
                ? NavigationWorldData.VesselMode.SINGLE_BLOCK
                : NavigationWorldData.VesselMode.MULTIBLOCK;
        stack.getOrCreateTag().putString(MODE, next.name());
        stack.getOrCreateTag().remove(VESSEL);
        stack.getOrCreateTag().remove(VESSEL_NAME);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!allowed(serverPlayer)) return InteractionResultHolder.fail(stack);
            if (serverPlayer.isShiftKeyDown()) {
                toggleMode(stack);
                serverPlayer.displayClientMessage(Component.literal("Режим привязчика: "
                        + (mode(stack) == NavigationWorldData.VesselMode.MULTIBLOCK
                        ? "готовая мультиблочная подлодка" : "одиночный блок")), true);
            } else {
                String selected = stack.getOrCreateTag().getString(VESSEL_NAME);
                serverPlayer.displayClientMessage(Component.literal(selected.isBlank()
                        ? "Сначала выберите подлодку/блок, затем блок с привязанным GUI навигационного терминала."
                        : "Выбрано: " + selected), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!allowed(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("Требуется оператор или творческий режим."), true);
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (serverPlayer.isShiftKeyDown()) {
            stack.getOrCreateTag().remove(VESSEL);
            stack.getOrCreateTag().remove(VESSEL_NAME);
            serverPlayer.displayClientMessage(Component.literal("Выбранная подлодка сброшена."), true);
            return InteractionResult.SUCCESS;
        }

        String gui = PowerWorldData.get(serverLevel).guiAt(clicked);
        if (NavigationWorldData.NAVIGATION_GUI.equals(gui)) {
            if (!stack.getOrCreateTag().hasUUID(VESSEL)) {
                serverPlayer.displayClientMessage(Component.literal("Сначала выберите подлодку или одиночный блок."), true);
                return InteractionResult.FAIL;
            }
            UUID vesselId = stack.getOrCreateTag().getUUID(VESSEL);
            boolean linked = NavigationWorldData.get(serverLevel).link(clicked, vesselId);
            serverPlayer.displayClientMessage(Component.literal(linked
                    ? "Навигационный терминал связан с «" + stack.getOrCreateTag().getString(VESSEL_NAME) + "»."
                    : "Выбранная конструкция больше не существует."), true);
            return linked ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        NavigationWorldData data = NavigationWorldData.get(serverLevel);
        NavigationWorldData.VesselState vessel;
        if (mode(stack) == NavigationWorldData.VesselMode.SINGLE_BLOCK) {
            vessel = data.registerSingle(clicked, serverLevel.getBlockState(clicked).getBlock().getName().getString());
        } else {
            vessel = data.vesselContaining(clicked);
            if (vessel == null) {
                serverPlayer.displayClientMessage(Component.literal(
                        "Эта конструкция не зарегистрирована. Сначала выделите её предметом создания мультиблочной подлодки."), true);
                return InteractionResult.FAIL;
            }
        }
        stack.getOrCreateTag().putUUID(VESSEL, vessel.id());
        stack.getOrCreateTag().putString(VESSEL_NAME, vessel.name());
        serverPlayer.displayClientMessage(Component.literal("Выбрано для управления: " + vessel.name()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Режим: " + (mode(stack) == NavigationWorldData.VesselMode.MULTIBLOCK
                ? "мультиблочная подлодка" : "одиночный блок")).withStyle(ChatFormatting.AQUA));
        String selected = stack.getOrCreateTag().getString(VESSEL_NAME);
        if (!selected.isBlank()) lines.add(Component.literal("Выбрано: " + selected).withStyle(ChatFormatting.GREEN));
        lines.add(Component.literal("Shift + ПКМ в воздух: сменить режим").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("ПКМ по конструкции, затем по блоку с GUI терминала").withStyle(ChatFormatting.GRAY));
    }
}
