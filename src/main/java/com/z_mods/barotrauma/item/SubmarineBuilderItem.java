package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.navigation.NavigationWorldData;
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

/** Map-builder item that registers an arbitrary cuboid as one movable submarine. */
public final class SubmarineBuilderItem extends Item {
    private static final String FIRST = "SubmarineFirstCorner";
    private static final String DIMENSION = "SubmarineDimension";
    private static final String LAST_VESSEL = "LastCreatedVessel";

    public SubmarineBuilderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!serverPlayer.isCreative() && !serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.literal("Требуется оператор или творческий режим."), true);
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        CompoundAccess tag = new CompoundAccess(stack);
        if (serverPlayer.isShiftKeyDown()) {
            tag.clearSelection();
            serverPlayer.displayClientMessage(Component.literal("Выделение подлодки сброшено."), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        String dimension = dimensionName(serverLevel.dimension());
        if (!tag.hasFirst()) {
            tag.setFirst(clicked, dimension);
            serverPlayer.displayClientMessage(Component.literal("Первая точка подлодки: " + clicked.toShortString()), true);
            return InteractionResult.SUCCESS;
        }
        if (!dimension.equals(tag.dimension())) {
            tag.clearSelection();
            serverPlayer.displayClientMessage(Component.literal("Обе точки должны быть в одном измерении."), true);
            return InteractionResult.FAIL;
        }

        BlockPos first = tag.first();
        String name = "Подлодка " + first.toShortString();
        NavigationWorldData.VesselState vessel = NavigationWorldData.get(serverLevel)
                .registerMultiblock(first, clicked, name);
        tag.clearSelection();
        if (vessel == null) {
            serverPlayer.displayClientMessage(Component.literal(
                    "Не удалось создать подлодку: объём должен быть от 1 до 32768 блоков."), true);
            return InteractionResult.FAIL;
        }
        stack.getOrCreateTag().putUUID(LAST_VESSEL, vessel.id());
        serverPlayer.displayClientMessage(Component.literal("Мультиблочная подлодка зарегистрирована: "
                + vessel.min().toShortString() + " → " + vessel.max().toShortString()), true);
        return InteractionResult.SUCCESS;
    }

    private static String dimensionName(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("ПКМ по двум противоположным углам: зарегистрировать подлодку")
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Shift + ПКМ: сбросить первую точку").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Максимальный объём: 32768 блоков").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static final class CompoundAccess {
        private final ItemStack stack;

        private CompoundAccess(ItemStack stack) {
            this.stack = stack;
        }

        boolean hasFirst() { return stack.getOrCreateTag().contains(FIRST); }
        BlockPos first() { return BlockPos.of(stack.getOrCreateTag().getLong(FIRST)); }
        String dimension() { return stack.getOrCreateTag().getString(DIMENSION); }
        void setFirst(BlockPos pos, String dimension) {
            stack.getOrCreateTag().putLong(FIRST, pos.asLong());
            stack.getOrCreateTag().putString(DIMENSION, dimension);
        }
        void clearSelection() {
            stack.getOrCreateTag().remove(FIRST);
            stack.getOrCreateTag().remove(DIMENSION);
        }
    }
}
