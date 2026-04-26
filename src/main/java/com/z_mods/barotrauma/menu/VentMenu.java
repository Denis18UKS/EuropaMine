package com.z_mods.barotrauma.menu;

import com.z_mods.barotrauma.blocks.VentDecoIntEntity;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.init.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class VentMenu extends AbstractContainerMenu {
    public static final int VENT_SLOT_COUNT = VentDecoIntEntity.CONTAINER_SIZE;
    public static final int PLAYER_MAIN_START = VENT_SLOT_COUNT;
    public static final int PLAYER_MAIN_END = PLAYER_MAIN_START + 27;
    public static final int HOTBAR_START = PLAYER_MAIN_END;
    public static final int HOTBAR_END = HOTBAR_START + 9;

    private final VentDecoIntEntity blockEntity;
    private final Level level;
    private int lockedSlots;

    public VentMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.VENT_MENU.get(), id);
        this.level = playerInventory.player.level();
        BlockEntity entity = level.getBlockEntity(pos);

        if (entity instanceof VentDecoIntEntity ventEntity) {
            this.blockEntity = ventEntity;
        } else {
            throw new IllegalStateException("Wrong block entity at " + pos);
        }

        this.lockedSlots = blockEntity.getLockedSlots();
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return blockEntity.getLockedSlots();
            }

            @Override
            public void set(int value) {
                lockedSlots = value;
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slot = col + row * 9;
                this.addSlot(new VentSlot(blockEntity, slot, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new PlayerMainSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < VENT_SLOT_COUNT && getCarried().is(ModItems.SLOT_LOCK_TOOL.get())) {
            boolean changed = blockEntity.toggleSlotLocked(slotId);
            if (!changed && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.barotrauma.slot_lock_failed"), true);
            }
            broadcastChanges();
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < VENT_SLOT_COUNT) {
                if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= HOTBAR_START && index < HOTBAR_END) {
                if (!this.moveItemStackTo(stack, 0, VENT_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (player.getAbilities().instabuild && index >= PLAYER_MAIN_START && index < PLAYER_MAIN_END) {
                if (!this.moveItemStackTo(stack, 0, VENT_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }

    public VentDecoIntEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isSlotLocked(int slot) {
        return slot >= 0 && slot < VENT_SLOT_COUNT && (lockedSlots & (1 << slot)) != 0;
    }

    private class VentSlot extends Slot {
        private VentSlot(VentDecoIntEntity container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !VentMenu.this.isSlotLocked(getSlotIndex());
        }

        @Override
        public boolean mayPickup(Player player) {
            return !VentMenu.this.isSlotLocked(getSlotIndex());
        }

        @Override
        public boolean isActive() {
            return !VentMenu.this.isSlotLocked(getSlotIndex());
        }
    }

    private static class PlayerMainSlot extends Slot {
        private final Inventory inventory;

        private PlayerMainSlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
            this.inventory = inventory;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return inventory.player.getAbilities().instabuild;
        }

        @Override
        public boolean mayPickup(Player player) {
            return player.getAbilities().instabuild;
        }
    }

}
