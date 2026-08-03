package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Two-click red/blue power wire tool. Connections are persistent and can target any block. */
public final class WireToolItem extends Item {
    private static final String START = "WireStart";
    private static final String DIMENSION = "WireDimension";
    private final PowerWorldData.WireColor color;

    public WireToolItem(PowerWorldData.WireColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (serverPlayer.isShiftKeyDown()) {
            stack.getOrCreateTag().remove(START);
            stack.getOrCreateTag().remove(DIMENSION);
            serverPlayer.displayClientMessage(Component.literal("Начальная точка провода сброшена."), true);
            return InteractionResult.SUCCESS;
        }

        String currentDimension = dimensionName(serverLevel.dimension());
        if (!stack.getOrCreateTag().contains(START)) {
            stack.getOrCreateTag().putLong(START, clicked.asLong());
            stack.getOrCreateTag().putString(DIMENSION, currentDimension);
            serverPlayer.displayClientMessage(Component.literal("Первая точка: " + clicked.toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        if (!currentDimension.equals(stack.getOrCreateTag().getString(DIMENSION))) {
            serverPlayer.displayClientMessage(Component.literal("Обе точки провода должны быть в одном измерении."), true);
            stack.getOrCreateTag().remove(START);
            stack.getOrCreateTag().remove(DIMENSION);
            return InteractionResult.FAIL;
        }

        BlockPos start = BlockPos.of(stack.getOrCreateTag().getLong(START));
        if (start.distSqr(clicked) > 4096.0D && !serverPlayer.isCreative()) {
            serverPlayer.displayClientMessage(Component.literal("Максимальная длина одного провода: 64 блока."), true);
            return InteractionResult.FAIL;
        }

        boolean added = PowerWorldData.get(serverLevel).connect(start, clicked, color);
        stack.getOrCreateTag().remove(START);
        stack.getOrCreateTag().remove(DIMENSION);
        serverPlayer.displayClientMessage(Component.literal(added
                ? (color == PowerWorldData.WireColor.RED ? "Красный" : "Синий") + " провод подключён."
                : "Такое соединение уже существует."), true);
        return InteractionResult.SUCCESS;
    }

    private static String dimensionName(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal(color == PowerWorldData.WireColor.RED ? "Красный силовой канал" : "Синий сигнальный канал")
                .withStyle(color == PowerWorldData.WireColor.RED ? ChatFormatting.RED : ChatFormatting.BLUE));
        lines.add(Component.literal("ПКМ по двум блокам: соединить").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Shift + ПКМ: сбросить первую точку").withStyle(ChatFormatting.DARK_GRAY));
    }
}
