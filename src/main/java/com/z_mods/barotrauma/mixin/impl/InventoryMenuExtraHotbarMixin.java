package com.z_mods.barotrauma.mixin.impl;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.hotbar.ExtraHotbarSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuExtraHotbarMixin extends AbstractContainerMenu {
    protected InventoryMenuExtraHotbarMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void barotrauma$addExtraSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
            // Vertical column to the right of the vanilla inventory, slots 10..18.
            this.addSlot(new ExtraHotbarSlot(inventory, i, 180, 8 + i * 18));
        }
    }
}
