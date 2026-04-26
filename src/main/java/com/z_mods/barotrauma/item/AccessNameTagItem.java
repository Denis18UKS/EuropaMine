package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.access.AccessLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AccessNameTagItem extends Item {
    public AccessNameTagItem(Properties properties) {
        super(properties);
    }

    public static AccessLevel getAccessLevel(ItemStack stack) {
        if (!stack.hasCustomHoverName()) {
            return AccessLevel.NONE;
        }
        return AccessLevel.fromText(stack.getHoverName().getString());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        AccessLevel accessLevel = getAccessLevel(stack);
        if (accessLevel == AccessLevel.NONE) {
            tooltip.add(Component.translatable("tooltip.barotrauma.nametag.rename").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.barotrauma.access_level", accessLevel.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
    }
}
