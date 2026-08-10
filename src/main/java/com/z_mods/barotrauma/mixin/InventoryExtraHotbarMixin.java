package com.z_mods.barotrauma.mixin;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.hotbar.ExtraHotbarStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps vanilla Inventory.items at its normal size. The mixin only redirects the selected main-hand stack
 * when a virtual extra slot is selected and migrates items written by the previous unsafe implementation.
 */
@Mixin(Inventory.class)
public abstract class InventoryExtraHotbarMixin {
    @Shadow @Final public Player player;
    @Shadow public int selected;

    @Inject(method = "load", at = @At("HEAD"))
    private void barotrauma$migrateLegacyExtraSlots(ListTag list, CallbackInfo ci) {
        ExtraHotbarStorage storage = ExtraHotbar.storage(player);
        if (storage == null) return;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            int slot = tag.getByte("Slot") & 255;
            int extra = slot - ExtraHotbar.VANILLA_MAIN_SIZE;
            if (extra < 0 || extra >= ExtraHotbar.MAX_EXTRA) continue;
            if (!storage.getStackInSlot(extra).isEmpty()) continue;
            ItemStack stack = ItemStack.of(tag);
            if (!stack.isEmpty()) storage.setStackInSlot(extra, stack);
        }
    }

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void barotrauma$getSafeSelected(CallbackInfoReturnable<ItemStack> cir) {
        // Repair playerdata produced by the previous build where Inventory.selected could be 9..17.
        if (selected >= ExtraHotbar.VANILLA_HOTBAR) {
            int legacyExtra = selected - ExtraHotbar.VANILLA_HOTBAR;
            selected = 0;
            if (legacyExtra >= 0 && legacyExtra < ExtraHotbar.MAX_EXTRA) {
                if (legacyExtra >= ExtraHotbar.getAppliedCount(player)) {
                    ExtraHotbar.setAppliedCount(player, legacyExtra + 1);
                }
                ExtraHotbar.setSelectedExtra(player, legacyExtra);
            }
        } else if (selected < 0) {
            selected = 0;
        }

        int extra = ExtraHotbar.getSelectedExtra(player);
        if (extra >= 0) cir.setReturnValue(ExtraHotbar.getStack(player, extra));
    }
}
