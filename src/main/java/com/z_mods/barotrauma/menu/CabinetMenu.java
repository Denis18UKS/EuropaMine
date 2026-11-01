package com.z_mods.barotrauma.menu;

import com.z_mods.barotrauma.blocks.CabinetBlock;
import com.z_mods.barotrauma.blocks.CabinetBlockEntity;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.init.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CabinetMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final boolean large;
    private final Container cabinet;
    private final int cabinetSlots;
    private final int mainSlots;
    private final int specialSlots;
    private final int playerStart;

    public CabinetMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, data.readBlockPos(), data.readBoolean());
    }

    public CabinetMenu(int id, Inventory inventory, BlockPos pos, boolean large) {
        super(ModMenus.CABINET_MENU.get(), id);
        this.pos = pos;
        this.large = large;
        CabinetBlock.Size size = large ? CabinetBlock.Size.LARGE : CabinetBlock.Size.MEDIUM;
        this.mainSlots = size.mainSlots();
        this.specialSlots = size.specialSlots();
        this.cabinetSlots = size.totalSlots();
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        this.cabinet = blockEntity instanceof CabinetBlockEntity entity ? entity : new SimpleContainer(40);

        int columns = large ? 7 : 5;
        int rows = large ? 5 : 4;
        int mainX = (212 - columns * 18) / 2;
        int mainY = 24;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                addSlot(new Slot(cabinet, index, mainX + col * 18, mainY + row * 18));
            }
        }

        int specialY = large ? 120 : 101;
        int specialX = (212 - specialSlots * 18) / 2;
        for (int i = 0; i < specialSlots; i++) {
            addSlot(new SpecialSlot(cabinet, mainSlots + i, specialX + i * 18, specialY));
        }

        this.playerStart = cabinetSlots;
        int playerY = large ? 160 : 145;
        int playerX = 25;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, playerX + col * 18, playerY + row * 18));
            }
        }
        int hotbarY = playerY + 58;
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, playerX + col * 18, hotbarY));
    }

    public boolean isLarge() { return large; }
    public int mainSlots() { return mainSlots; }
    public int specialSlots() { return specialSlots; }

    private static boolean isSpecial(ItemStack stack) {
        return stack.is(ModItems.TOOL_BELT.get()) || stack.is(ModItems.CRATE.get()) || stack.is(ModItems.LARGE_CRATE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < cabinetSlots) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (isSpecial(stack)) {
                if (!moveItemStackTo(stack, mainSlots, cabinetSlots, false)
                        && !moveItemStackTo(stack, 0, mainSlots, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, mainSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D) <= 64.0D || player.isCreative();
    }

    private static final class SpecialSlot extends Slot {
        private SpecialSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return isSpecial(stack); }
    }
}
