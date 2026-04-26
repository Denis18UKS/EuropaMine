package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.access.AccessLevel;
import com.z_mods.barotrauma.access.AccessStorage;
import com.z_mods.barotrauma.blocks.VentDecoIntEntity;
import com.z_mods.barotrauma.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AccessConfiguratorItem extends Item {
    public AccessConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof Container) || blockEntity instanceof VentDecoIntEntity) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            AccessLevel nextLevel = getLevelFromOffhand(context.getPlayer() == null ? ItemStack.EMPTY : context.getPlayer().getOffhandItem());
            if (nextLevel == AccessLevel.NONE) {
                nextLevel = AccessStorage.get(blockEntity).next();
            }

            AccessStorage.set(blockEntity, nextLevel);
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.barotrauma.access_set",
                        nextLevel.getDisplayName()
                ), true);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.access_configurator").withStyle(ChatFormatting.GRAY));
    }

    private static AccessLevel getLevelFromOffhand(ItemStack stack) {
        if (!stack.is(ModItems.NAMETAG.get())) {
            return AccessLevel.NONE;
        }
        return AccessNameTagItem.getAccessLevel(stack);
    }
}
