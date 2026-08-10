package com.z_mods.barotrauma.hotbar;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Common storage/index helpers for the real extra hotbar slots. */
public final class ExtraHotbar {
    public static final int VANILLA_HOTBAR = 9;
    public static final int VANILLA_MAIN_SIZE = 36;
    public static final int MAX_EXTRA = 9;
    public static final int EXPANDED_MAIN_SIZE = VANILLA_MAIN_SIZE + MAX_EXTRA;
    public static final String COUNT_KEY = "barotrauma_extra_hotbar_count";

    private static volatile int clientAppliedCount = 1;

    private ExtraHotbar() {}

    public static int inventoryIndex(int extraIndex) {
        return VANILLA_MAIN_SIZE + extraIndex;
    }

    public static int selectedToInventoryIndex(int selected) {
        if (selected < VANILLA_HOTBAR) return selected;
        int extra = selected - VANILLA_HOTBAR;
        return extra >= 0 && extra < MAX_EXTRA ? inventoryIndex(extra) : 0;
    }

    public static int getAppliedCount(Player player) {
        if (player == null) return clientAppliedCount;
        if (player.level().isClientSide) return clientAppliedCount;
        int raw = player.getPersistentData().contains(COUNT_KEY)
                ? player.getPersistentData().getInt(COUNT_KEY) : 1;
        return clamp(raw);
    }

    public static void setAppliedCount(Player player, int count) {
        int value = clamp(count);
        if (player != null && !player.level().isClientSide) {
            player.getPersistentData().putInt(COUNT_KEY, value);
            if (player.getInventory().selected >= VANILLA_HOTBAR + value) {
                player.getInventory().selected = Math.max(0, VANILLA_HOTBAR + value - 1);
            }
        }
    }

    public static void setClientAppliedCount(int count) {
        clientAppliedCount = clamp(count);
    }

    public static int getClientAppliedCount() {
        return clientAppliedCount;
    }

    public static int totalHotbarSlots(Player player) {
        return VANILLA_HOTBAR + getAppliedCount(player);
    }

    public static ItemStack getSelected(Inventory inventory) {
        int selected = inventory.selected;
        if (selected >= 0 && selected < VANILLA_HOTBAR) return inventory.items.get(selected);
        int extra = selected - VANILLA_HOTBAR;
        int index = inventoryIndex(extra);
        return extra >= 0 && extra < MAX_EXTRA && index < inventory.items.size()
                ? inventory.items.get(index) : ItemStack.EMPTY;
    }

    public static int clamp(int value) {
        return Math.max(1, Math.min(MAX_EXTRA, value));
    }
}
