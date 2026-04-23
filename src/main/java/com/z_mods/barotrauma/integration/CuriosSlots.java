package com.z_mods.barotrauma.integration;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;

public final class CuriosSlots {
    public static final String GEAR_SLOT = "gear";
    public static final int PANEL_SLOT_COUNT = 6;

    private CuriosSlots() {
    }

    public static List<ItemStack> getGearStacks(LivingEntity entity, int limit) {
        List<ItemStack> stacks = new ArrayList<>(limit);

        CuriosApi.getCuriosInventory(entity).ifPresent(curiosInventory ->
                curiosInventory.getStacksHandler(GEAR_SLOT).ifPresent(slotHandler -> {
                    var stackHandler = slotHandler.getStacks();
                    int slots = Math.min(limit, stackHandler.getSlots());
                    for (int slot = 0; slot < slots; slot++) {
                        stacks.add(stackHandler.getStackInSlot(slot));
                    }
                })
        );

        while (stacks.size() < limit) {
            stacks.add(ItemStack.EMPTY);
        }

        return stacks;
    }

    public static int getGearSlotCount(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(GEAR_SLOT))
                .map(slotHandler -> slotHandler.getStacks().getSlots())
                .orElse(0);
    }

    public static ItemStack getGearStack(LivingEntity entity, int index) {
        if (index < 0) {
            return ItemStack.EMPTY;
        }

        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(GEAR_SLOT))
                .map(slotHandler -> {
                    var stacks = slotHandler.getStacks();
                    return index < stacks.getSlots() ? stacks.getStackInSlot(index) : ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    public static boolean swapWithHotbar(Player player, int curioIndex, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9 || curioIndex < 0) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(GEAR_SLOT)
                        .map(slotHandler -> {
                            var stackHandler = slotHandler.getStacks();
                            if (curioIndex >= stackHandler.getSlots()) {
                                return false;
                            }

                            ItemStack hotbarStack = player.getInventory().getItem(hotbarSlot).copy();
                            ItemStack curioStack = stackHandler.getStackInSlot(curioIndex).copy();
                            SlotContext slotContext = new SlotContext(GEAR_SLOT, player, curioIndex, false, slotHandler.isVisible());

                            if (!hotbarStack.isEmpty() && !CuriosApi.isStackValid(slotContext, hotbarStack)) {
                                return false;
                            }

                            curiosInventory.setEquippedCurio(GEAR_SLOT, curioIndex, hotbarStack);
                            player.getInventory().setItem(hotbarSlot, curioStack);
                            player.getInventory().setChanged();
                            return true;
                        }))
                .orElse(false);
    }

    public static boolean equipToFirstAvailable(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(GEAR_SLOT)
                        .map(slotHandler -> {
                            var stackHandler = slotHandler.getStacks();

                            for (int slot = 0; slot < stackHandler.getSlots(); slot++) {
                                if (!stackHandler.getStackInSlot(slot).isEmpty()) {
                                    continue;
                                }

                                SlotContext slotContext = new SlotContext(GEAR_SLOT, player, slot, false, slotHandler.isVisible());
                                if (!CuriosApi.isStackValid(slotContext, stack)) {
                                    continue;
                                }

                                curiosInventory.setEquippedCurio(GEAR_SLOT, slot, stack.copy());
                                return true;
                            }

                            return false;
                        }))
                .orElse(false);
    }
}
