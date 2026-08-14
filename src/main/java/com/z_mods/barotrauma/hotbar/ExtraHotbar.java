package com.z_mods.barotrauma.hotbar;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Helpers for extra hotbar slots stored outside the vanilla Inventory.items list. */
public final class ExtraHotbar {
    public static final int VANILLA_HOTBAR = 9;
    public static final int VANILLA_MAIN_SIZE = 36;
    public static final int MAX_EXTRA = 9;
    public static final String COUNT_KEY = "barotrauma_extra_hotbar_count";
    public static final String SELECTED_KEY = "barotrauma_extra_hotbar_selected";

    private static volatile int clientAppliedCount = 1;
    private static volatile int clientSelectedExtra = -1;

    private ExtraHotbar() {}

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
            int selected = getSelectedExtra(player);
            if (selected >= value) setSelectedExtra(player, -1);
            sanitizeVanillaSelected(player);
        }
    }

    public static int getSelectedExtra(Player player) {
        if (player == null) return clientSelectedExtra;
        if (player.level().isClientSide) return clientSelectedExtra;
        if (!player.getPersistentData().contains(SELECTED_KEY)) return -1;
        int value = player.getPersistentData().getInt(SELECTED_KEY);
        return value >= 0 && value < getAppliedCount(player) ? value : -1;
    }

    public static void setSelectedExtra(Player player, int extraIndex) {
        if (player == null) return;
        if (player.level().isClientSide) {
            setClientSelectedExtra(extraIndex);
            return;
        }
        if (extraIndex >= 0 && extraIndex < getAppliedCount(player)) {
            player.getPersistentData().putInt(SELECTED_KEY, extraIndex);
        } else {
            player.getPersistentData().remove(SELECTED_KEY);
        }
        sanitizeVanillaSelected(player);
    }

    public static void clearSelectedExtra(Player player) {
        setSelectedExtra(player, -1);
    }

    public static void setClientAppliedCount(int count) {
        clientAppliedCount = clamp(count);
        if (clientSelectedExtra >= clientAppliedCount) clientSelectedExtra = -1;
    }

    public static int getClientAppliedCount() {
        return clientAppliedCount;
    }

    public static void setClientSelectedExtra(int extraIndex) {
        clientSelectedExtra = extraIndex >= 0 && extraIndex < clientAppliedCount ? extraIndex : -1;
    }

    public static int getClientSelectedExtra() {
        return clientSelectedExtra;
    }

    public static int totalHotbarSlots(Player player) {
        return VANILLA_HOTBAR + getAppliedCount(player);
    }

    public static ItemStack getStack(Player player, int extraIndex) {
        if (player == null || extraIndex < 0 || extraIndex >= MAX_EXTRA) return ItemStack.EMPTY;
        ExtraHotbarStorage storage = ExtraHotbarCapability.get(player);
        return storage == null ? ItemStack.EMPTY : storage.getStackInSlot(extraIndex);
    }

    public static void setStack(Player player, int extraIndex, ItemStack stack) {
        if (player == null || extraIndex < 0 || extraIndex >= MAX_EXTRA) return;
        ExtraHotbarStorage storage = ExtraHotbarCapability.get(player);
        if (storage != null) storage.setStackInSlot(extraIndex, stack == null ? ItemStack.EMPTY : stack);
    }

    public static ExtraHotbarStorage storage(Player player) {
        return player == null ? null : ExtraHotbarCapability.get(player);
    }

    public static void sanitizeVanillaSelected(Player player) {
        if (player == null) return;
        int selected = player.getInventory().selected;
        if (selected < 0 || selected >= VANILLA_HOTBAR) player.getInventory().selected = 0;
    }

    public static int clamp(int value) {
        return Math.max(1, Math.min(MAX_EXTRA, value));
    }
}
