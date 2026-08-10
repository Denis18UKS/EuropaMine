package com.z_mods.barotrauma.mixin;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryExtraHotbarMixin {
    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow public int selected;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void barotrauma$expandMainInventory(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        while (items.size() < ExtraHotbar.EXPANDED_MAIN_SIZE) items.add(ItemStack.EMPTY);
    }

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void barotrauma$getExtraSelected(CallbackInfoReturnable<ItemStack> cir) {
        if (selected >= ExtraHotbar.VANILLA_HOTBAR) {
            int extra = selected - ExtraHotbar.VANILLA_HOTBAR;
            int index = ExtraHotbar.inventoryIndex(extra);
            if (extra >= 0 && extra < ExtraHotbar.MAX_EXTRA && index < items.size()) {
                cir.setReturnValue(items.get(index));
            }
        }
    }
}
