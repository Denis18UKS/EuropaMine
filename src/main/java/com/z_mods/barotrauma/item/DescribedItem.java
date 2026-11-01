package com.z_mods.barotrauma.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class DescribedItem extends Item {
    private final String[] description;
    public DescribedItem(Properties properties, String... description) { super(properties); this.description = description == null ? new String[0] : description; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Обычное качество").withStyle(ChatFormatting.GRAY));
        for (String row : description) lines.add(Component.literal(row).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.empty());
        lines.add(Component.literal("[LeftControl] Забрать 1 шт. из ячейки").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("[LeftShift] Забрать половину из ячейки").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, lines, flag);
    }
}
