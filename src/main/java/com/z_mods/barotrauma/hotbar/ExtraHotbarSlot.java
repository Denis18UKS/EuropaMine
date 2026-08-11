package com.z_mods.barotrauma.hotbar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Inventory-menu slot backed by the dedicated extra-hotbar capability. */
public final class ExtraHotbarSlot extends SlotItemHandler {
    private final Player owner;
    private final int extraIndex;

    public ExtraHotbarSlot(Inventory inventory, int extraIndex, int x, int y) {
        super(requireStorage(inventory.player), extraIndex, x, y);
        this.owner = inventory.player;
        this.extraIndex = extraIndex;
    }

    private static ExtraHotbarStorage requireStorage(Player player) {
        ExtraHotbarStorage storage = ExtraHotbar.storage(player);
        if (storage == null) {
            throw new IllegalStateException("Extra hotbar capability is missing for player " + player.getGameProfile().getName());
        }
        return storage;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        String binding = SlotBindingManager.getBinding(owner, extraIndex);
        if (binding.isBlank()) return true;
        return binding.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    @Override
    public boolean isActive() {
        return extraIndex < ExtraHotbar.getAppliedCount(owner);
    }
}
