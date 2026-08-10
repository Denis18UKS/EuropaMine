package com.z_mods.barotrauma.hotbar;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public final class ExtraHotbarSlot extends Slot {
    private final Player owner;
    private final int extraIndex;

    public ExtraHotbarSlot(Inventory inventory, int extraIndex, int x, int y) {
        super(inventory, ExtraHotbar.inventoryIndex(extraIndex), x, y);
        this.owner = inventory.player;
        this.extraIndex = extraIndex;
    }

    @Override
    public boolean isActive() {
        return extraIndex < ExtraHotbar.getAppliedCount(owner);
    }
}
