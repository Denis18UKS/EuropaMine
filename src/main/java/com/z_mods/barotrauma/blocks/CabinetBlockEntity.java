package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.init.ModBlockEntities;
import com.z_mods.barotrauma.init.ModBlocks;
import com.z_mods.barotrauma.menu.CabinetMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class CabinetBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final int STORAGE_SIZE = 40;
    private final NonNullList<ItemStack> items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);

    public CabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABINET.get(), pos, state);
    }

    public boolean isLarge() {
        return getBlockState().is(ModBlocks.LARGE_STEEL_CABINET.get());
    }

    public int activeSlots() {
        return isLarge() ? CabinetBlock.Size.LARGE.totalSlots() : CabinetBlock.Size.MEDIUM.totalSlots();
    }

    @Override public Component getDisplayName() {
        return Component.literal(isLarge() ? CabinetBlock.Size.LARGE.title() : CabinetBlock.Size.MEDIUM.title());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CabinetMenu(id, inventory, worldPosition, isLarge());
    }

    @Override public int getContainerSize() { return STORAGE_SIZE; }
    @Override public boolean isEmpty() {
        for (int i = 0; i < activeSlots(); i++) if (!items.get(i).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < STORAGE_SIZE ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack out = ContainerHelper.removeItem(items, slot, amount); if (!out.isEmpty()) setChanged(); return out; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= activeSlots()) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5D, worldPosition.getY() + .5D, worldPosition.getZ() + .5D) <= 64.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
    }
}
