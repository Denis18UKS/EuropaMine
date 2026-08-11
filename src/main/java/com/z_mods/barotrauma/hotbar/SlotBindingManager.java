package com.z_mods.barotrauma.hotbar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Persistent mapping between extra hotbar slots and concrete item registry ids. */
public final class SlotBindingManager {
    public static final String ROOT_KEY = "barotrauma_extra_hotbar_bindings";

    private SlotBindingManager() {}

    public static String getBinding(Player player, int extraIndex) {
        if (player == null || extraIndex < 0 || extraIndex >= ExtraHotbar.MAX_EXTRA) return "";
        CompoundTag root = player.getPersistentData().getCompound(ROOT_KEY);
        return root.getString(Integer.toString(extraIndex));
    }

    public static String[] getBindings(Player player) {
        String[] result = new String[ExtraHotbar.MAX_EXTRA];
        for (int i = 0; i < result.length; i++) result[i] = getBinding(player, i);
        return result;
    }

    public static void setBinding(Player player, int extraIndex, String itemId) {
        if (player == null || extraIndex < 0 || extraIndex >= ExtraHotbar.MAX_EXTRA) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT_KEY).copy();
        String key = Integer.toString(extraIndex);
        if (itemId == null || itemId.isBlank()) root.remove(key);
        else root.putString(key, itemId);
        player.getPersistentData().put(ROOT_KEY, root);
    }

    public static void copyBindings(Player from, Player to) {
        if (from == null || to == null || !from.getPersistentData().contains(ROOT_KEY)) return;
        to.getPersistentData().put(ROOT_KEY, from.getPersistentData().getCompound(ROOT_KEY).copy());
    }

    public static int findBoundSlot(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return -1;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return -1;
        String value = id.toString();
        int count = ExtraHotbar.getAppliedCount(player);
        for (int i = 0; i < count; i++) {
            if (value.equals(getBinding(player, i))) return i;
        }
        return -1;
    }

    /**
     * Moves the held stack into its assigned extra slot. Matching stacks merge; different stacks swap.
     * Returns true only when a matching configured slot exists and an actual transfer/swap happened.
     */
    public static boolean moveHeldToBoundSlot(ServerPlayer player, InteractionHand hand) {
        if (player == null) return false;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        int slot = findBoundSlot(player, held);
        if (slot < 0) return false;

        ItemStack target = ExtraHotbar.getStack(player, slot);
        if (target.isEmpty()) {
            ExtraHotbar.setStack(player, slot, held.copy());
            player.setItemInHand(hand, ItemStack.EMPTY);
            return true;
        }

        if (ItemStack.isSameItemSameTags(target, held)) {
            ExtraHotbarStorage storage = ExtraHotbar.storage(player);
            if (storage == null) return false;
            int max = Math.min(target.getMaxStackSize(), storage.getSlotLimit(slot));
            int room = Math.max(0, max - target.getCount());
            if (room <= 0) return false;
            int moved = Math.min(room, held.getCount());
            ItemStack merged = target.copy();
            merged.grow(moved);
            ItemStack remainder = held.copy();
            remainder.shrink(moved);
            ExtraHotbar.setStack(player, slot, merged);
            player.setItemInHand(hand, remainder);
            return moved > 0;
        }

        // The user explicitly requested immediate relocation to the bound slot. Swapping prevents loss.
        ExtraHotbar.setStack(player, slot, held.copy());
        player.setItemInHand(hand, target.copy());
        return true;
    }

    public static boolean isValidItemId(String value) {
        if (value == null || value.isBlank()) return true;
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) return false;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != null && item != net.minecraft.world.item.Items.AIR && BuiltInRegistries.ITEM.containsKey(id);
    }
}
