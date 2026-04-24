package com.z_mods.barotrauma.mixin;

import com.z_mods.barotrauma.integration.CuriosSlots;
import com.z_mods.barotrauma.inventory.BarotraumaCurioSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.CuriosApi;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    private static final int HELMET_SLOT_ID = 5;
    private static final int CHEST_SLOT_ID = 6;
    private static final int LEGS_SLOT_ID = 7;
    private static final int FEET_SLOT_ID = 8;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void barotrauma$moveArmorAndAddCurios(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        InventoryMenu menu = (InventoryMenu) (Object) this;

        moveSlot(menu.getSlot(HELMET_SLOT_ID), 134, 26);
        moveSlot(menu.getSlot(CHEST_SLOT_ID), 152, 26);
        moveSlot(menu.getSlot(LEGS_SLOT_ID), -2000, -2000);
        moveSlot(menu.getSlot(FEET_SLOT_ID), -2000, -2000);

        CuriosApi.getCuriosInventory(owner).ifPresent(curiosInventory -> {
            addCurioSlot(owner, CuriosSlots.DIVING_SUIT_SLOT, 134, 44);
            addCurioSlot(owner, CuriosSlots.BADGE_SLOT, 152, 44);
            addCurioSlot(owner, CuriosSlots.HEADSET_SLOT, 134, 62);
            addCurioSlot(owner, CuriosSlots.TOOL_BELT_SLOT, 152, 62);
        });
    }

    private static void moveSlot(Slot slot, int x, int y) {
        SlotAccessor accessor = (SlotAccessor) slot;
        accessor.barotrauma$setX(x);
        accessor.barotrauma$setY(y);
    }

    private void addCurioSlot(Player owner, String slotType, int x, int y) {
        CuriosApi.getCuriosInventory(owner)
                .resolve()
                .flatMap(curiosInventory -> curiosInventory.getStacksHandler(slotType))
                .ifPresent(slotHandler -> {
                    if (slotHandler.getStacks().getSlots() > 0) {
                        ((AbstractContainerMenuAccessor) (AbstractContainerMenu) (Object) this)
                                .barotrauma$addSlot(new BarotraumaCurioSlot(slotHandler.getStacks(), 0, x, y, slotType, owner));
                    }
                });
    }
}
