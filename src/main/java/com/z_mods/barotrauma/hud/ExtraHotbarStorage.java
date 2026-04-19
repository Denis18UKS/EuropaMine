package com.z_mods.barotrauma.hud;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ExtraHotbarStorage {
    private static final String EXTRA_HOTBAR_TAG = "barotrauma_extra_hotbar";

    private ExtraHotbarStorage() {
    }

    public static ItemStack get(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(EXTRA_HOTBAR_TAG)) {
            return ItemStack.EMPTY;
        }

        return ItemStack.of(data.getCompound(EXTRA_HOTBAR_TAG));
    }

    public static void set(Player player, ItemStack stack) {
        CompoundTag data = player.getPersistentData();
        if (stack.isEmpty()) {
            data.remove(EXTRA_HOTBAR_TAG);
            return;
        }

        data.put(EXTRA_HOTBAR_TAG, stack.save(new CompoundTag()));
    }

    public static ItemStack swapWithSelected(Player player, int selectedSlot) {
        if (selectedSlot < 0 || selectedSlot >= 9) {
            return get(player);
        }

        ItemStack selected = player.getInventory().getItem(selectedSlot).copy();
        ItemStack extra = get(player);

        player.getInventory().setItem(selectedSlot, extra);
        set(player, selected);
        player.getInventory().setChanged();
        return selected;
    }

    public static void copy(Player original, Player target) {
        set(target, get(original));
    }
}
