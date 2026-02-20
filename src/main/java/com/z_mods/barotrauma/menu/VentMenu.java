package com.z_mods.barotrauma.menu;

import com.z_mods.barotrauma.blocks.VentDecoIntEntity;
import com.z_mods.barotrauma.init.ModMenus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

public class VentMenu extends AbstractContainerMenu {
    private final VentDecoIntEntity blockEntity;
    private final Level level;

    // Конструктор принимает int, Inventory, BlockPos
    public VentMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.VENT_MENU.get(), id);
        this.level = playerInventory.player.level();
        BlockEntity entity = level.getBlockEntity(pos);
        
        if (entity instanceof VentDecoIntEntity) {
            this.blockEntity = (VentDecoIntEntity) entity;
        } else {
            throw new IllegalStateException("Wrong block entity at " + pos);
        }

        // СЛОТ БЛОКА (один слот)
        this.addSlot(new Slot(blockEntity, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });

        // ИНВЕНТАРЬ ИГРОКА
        for(int i = 0; i < 3; ++i) {
            for(int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        // ХОТБАР
        for(int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            
            if (index == 0) { // Слот блока
                if (!this.moveItemStackTo(itemstack1, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            
            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }
    
    public VentDecoIntEntity getBlockEntity() {
        return blockEntity;
    }
}