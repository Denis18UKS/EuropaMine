package com.z_mods.barotrauma.integration;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

public final class CuriosSlots {
    public static final String DIVING_SUIT_SLOT = "diving_suit";
    public static final String BADGE_SLOT = "badge";
    public static final String HEADSET_SLOT = "headset";
    public static final String TOOL_BELT_SLOT = "tool_belt";
    public static final String EXTRA_HOTBAR_SLOT = "extra_hotbar";

    public static final String[] INVENTORY_SLOTS = {
            DIVING_SUIT_SLOT,
            BADGE_SLOT,
            HEADSET_SLOT,
            TOOL_BELT_SLOT
    };

    private CuriosSlots() {
    }

    public static Optional<IDynamicStackHandler> getStacks(LivingEntity entity, String slotType) {
        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(slotType))
                .map(slotHandler -> slotHandler.getStacks());
    }

    public static ItemStack getFirstStack(LivingEntity entity, String slotType) {
        return getStacks(entity, slotType)
                .filter(stacks -> stacks.getSlots() > 0)
                .map(stacks -> stacks.getStackInSlot(0))
                .orElse(ItemStack.EMPTY);
    }

    public static boolean swapExtraHotbarWithHotbar(Player player, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) {
            return false;
        }

        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(EXTRA_HOTBAR_SLOT)
                        .map(slotHandler -> {
                            var stackHandler = slotHandler.getStacks();
                            if (stackHandler.getSlots() <= 0) {
                                return false;
                            }

                            ItemStack hotbarStack = player.getInventory().getItem(hotbarSlot).copy();
                            ItemStack extraStack = stackHandler.getStackInSlot(0).copy();

                            curiosInventory.setEquippedCurio(EXTRA_HOTBAR_SLOT, 0, hotbarStack);
                            player.getInventory().setItem(hotbarSlot, extraStack);
                            player.getInventory().setChanged();
                            return true;
                        }))
                .orElse(false);
    }
}
