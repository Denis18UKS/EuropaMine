package com.z_mods.barotrauma.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

public class BarotraumaCurioSlot extends SlotItemHandler {
    private final String slotType;
    private final Player player;

    public BarotraumaCurioSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition, String slotType, Player player) {
        super(itemHandler, index, xPosition, yPosition);
        this.slotType = slotType;
        this.player = player;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return CuriosApi.isStackValid(new SlotContext(slotType, player, getSlotIndex(), false, true), stack);
    }
}
