package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.network.SubmarineZonePackets;
import com.z_mods.barotrauma.submarines.SubmarineZoneData;
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

public final class SubmarineZoneToolItem extends Item {
    private static final String FIRST = "ZoneFirstCorner";
    private static final String DIMENSION = "ZoneDimension";
    private final SubmarineZoneData.Kind kind;

    public SubmarineZoneToolItem(SubmarineZoneData.Kind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public SubmarineZoneData.Kind kind() { return kind; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("Инструменты зон доступны оператору или в творческом режиме."), true);
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            clear(stack);
            player.displayClientMessage(Component.literal("Первая точка сброшена. Shift+ПКМ по воздуху открывает библиотеку зон."), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        String dimension = server.dimension().location().toString();
        if (!stack.getOrCreateTag().contains(FIRST)) {
            stack.getOrCreateTag().putLong(FIRST, clicked.asLong());
            stack.getOrCreateTag().putString(DIMENSION, dimension);
            player.displayClientMessage(Component.literal(kind.title() + ": точка 1 = " + clicked.toShortString()), true);
            return InteractionResult.SUCCESS;
        }
        if (!dimension.equals(stack.getOrCreateTag().getString(DIMENSION))) {
            clear(stack);
            player.displayClientMessage(Component.literal("Обе точки зоны должны быть в одном измерении."), true);
            return InteractionResult.FAIL;
        }

        BlockPos first = BlockPos.of(stack.getOrCreateTag().getLong(FIRST));
        SubmarineZoneData.Zone zone = SubmarineZoneData.get(server).add(kind, first, clicked);
        clear(stack);
        if (zone == null) {
            player.displayClientMessage(Component.literal("Не удалось создать зону: слишком большой объём."), true);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.literal("Зона сохранена: " + zone.name()), true);
        SubmarineZonePackets.open(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()) {
            SubmarineZonePackets.open(serverPlayer);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static void clear(ItemStack stack) {
        stack.getOrCreateTag().remove(FIRST);
        stack.getOrCreateTag().remove(DIMENSION);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal(kind.title()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("ПКМ по двум точкам: создать 3D-зону.").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Shift+ПКМ по воздуху: библиотека зон и телепорт.").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, lines, flag);
    }
}
