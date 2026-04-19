package com.z_mods.barotrauma.client;

import net.minecraft.world.item.ItemStack;

public final class ClientExtraHotbarSlot {
    private static ItemStack stack = ItemStack.EMPTY;
    private static boolean selected;
    private static int selectedVanillaSlot;

    private ClientExtraHotbarSlot() {
    }

    public static ItemStack get() {
        return stack;
    }

    public static void set(ItemStack stack) {
        ClientExtraHotbarSlot.stack = stack;
    }

    public static boolean isSelected() {
        return selected;
    }

    public static int getSelectedVanillaSlot() {
        return selectedVanillaSlot;
    }

    public static void toggleSelected(int selectedVanillaSlot) {
        if (selected && ClientExtraHotbarSlot.selectedVanillaSlot == selectedVanillaSlot) {
            selected = false;
            return;
        }

        selected = true;
        ClientExtraHotbarSlot.selectedVanillaSlot = selectedVanillaSlot;
    }

    public static void clearSelected() {
        selected = false;
    }
}
