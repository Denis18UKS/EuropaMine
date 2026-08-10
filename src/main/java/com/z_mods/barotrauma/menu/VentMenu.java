package com.z_mods.barotrauma.menu;

import com.z_mods.barotrauma.blocks.VentDecoIntEntity;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.init.ModMenus;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The same 27-slot vent inventory can now be bound to any block. */
public class VentMenu extends AbstractContainerMenu {
    public static final int VENT_SLOT_COUNT = 27;
    public static final int PLAYER_MAIN_START = VENT_SLOT_COUNT;
    public static final int PLAYER_MAIN_END = PLAYER_MAIN_START + 27;
    public static final int HOTBAR_START = PLAYER_MAIN_END;
    public static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final VentDecoIntEntity nativeVent;
    private final PowerWorldData.VirtualVentState virtualVent;
    private final Level level;
    private final BlockPos pos;
    private int lockedSlots;

    public VentMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.VENT_MENU.get(), id);
        this.level = playerInventory.player.level();
        this.pos = pos;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof VentDecoIntEntity vent) {
            nativeVent = vent; virtualVent = null; container = vent;
        } else if (level instanceof ServerLevel serverLevel) {
            nativeVent = null;
            virtualVent = PowerWorldData.get(serverLevel).virtualVentOrCreate(pos);
            container = virtualVent;
        } else {
            nativeVent = null; virtualVent = null; container = new SimpleContainer(VENT_SLOT_COUNT);
        }
        this.lockedSlots = currentLocked();
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return currentLocked(); }
            @Override public void set(int value) { lockedSlots = value; }
        });

        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            int slot = col + row * 9;
            addSlot(new VentSlot(container, slot, 8 + col * 18, 18 + row * 18));
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new PlayerMainSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    private int currentLocked() {
        if (nativeVent != null) return nativeVent.getLockedSlots();
        if (virtualVent != null) return virtualVent.getLockedSlots();
        return lockedSlots;
    }

    private boolean toggleLocked(int slot) {
        if (nativeVent != null) return nativeVent.toggleSlotLocked(slot);
        if (virtualVent != null) return virtualVent.toggleSlotLocked(slot);
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < VENT_SLOT_COUNT && getCarried().is(ModItems.SLOT_LOCK_TOOL.get())) {
            boolean changed = toggleLocked(slotId);
            if (!changed && !level.isClientSide) player.displayClientMessage(Component.translatable("message.barotrauma.slot_lock_failed"), true);
            broadcastChanges(); return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem(); result = stack.copy();
            if (index < VENT_SLOT_COUNT) {
                if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, true)) return ItemStack.EMPTY;
            } else if (index >= HOTBAR_START && index < HOTBAR_END) {
                if (!moveItemStackTo(stack, 0, VENT_SLOT_COUNT, false)) return ItemStack.EMPTY;
            } else if (player.getAbilities().instabuild && index >= PLAYER_MAIN_START && index < PLAYER_MAIN_END) {
                if (!moveItemStackTo(stack, 0, VENT_SLOT_COUNT, false)) return ItemStack.EMPTY;
            } else return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return result;
    }

    @Override public boolean stillValid(Player player) {
        if (nativeVent != null) return nativeVent.stillValid(player);
        return player.distanceToSqr(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D) <= 64.0D || player.isCreative();
    }
    public boolean isSlotLocked(int slot) { return slot >= 0 && slot < VENT_SLOT_COUNT && (lockedSlots & (1 << slot)) != 0; }

    private class VentSlot extends Slot {
        private VentSlot(Container c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return !isSlotLocked(getSlotIndex()); }
        @Override public boolean mayPickup(Player player) { return !isSlotLocked(getSlotIndex()); }
        @Override public boolean isActive() { return !isSlotLocked(getSlotIndex()); }
    }
    private static class PlayerMainSlot extends Slot {
        private final Inventory inventory;
        private PlayerMainSlot(Inventory inventory, int slot, int x, int y) { super(inventory, slot, x, y); this.inventory = inventory; }
        @Override public boolean mayPlace(ItemStack stack) { return inventory.player.getAbilities().instabuild; }
        @Override public boolean mayPickup(Player player) { return player.getAbilities().instabuild; }
    }
}
