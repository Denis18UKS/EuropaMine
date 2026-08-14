package com.z_mods.barotrauma.hotbar;

import net.minecraftforge.items.ItemStackHandler;

/** Dedicated storage for extra hotbar slots. It is intentionally separate from vanilla Inventory.items. */
public final class ExtraHotbarStorage extends ItemStackHandler {
    public ExtraHotbarStorage() {
        super(ExtraHotbar.MAX_EXTRA);
    }
}
