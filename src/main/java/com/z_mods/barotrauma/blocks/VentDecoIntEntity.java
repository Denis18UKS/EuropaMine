package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class VentDecoIntEntity extends BlockEntity implements GeoBlockEntity, Container {
    public static final int CONTAINER_SIZE = 27;
    public static final int DEFAULT_UNLOCKED_SLOT = 13;
    private static final String LOCKED_SLOTS_TAG = "LockedSlots";
    private static final int SLOT_MASK = (1 << CONTAINER_SIZE) - 1;
    private static final int DEFAULT_LOCKED_SLOTS = SLOT_MASK & ~(1 << DEFAULT_UNLOCKED_SLOT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int lockedSlots = DEFAULT_LOCKED_SLOTS;

    private static final RawAnimation FAN_SPIN = RawAnimation.begin().thenLoop("animation_fan");

    public VentDecoIntEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT_DECO_INT.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fan_controller", 0, state -> state.setAndContinue(FAN_SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    public boolean isSlotLocked(int slot) {
        return slot >= 0 && slot < CONTAINER_SIZE && (lockedSlots & (1 << slot)) != 0;
    }

    public int getLockedSlots() {
        return lockedSlots;
    }

    public boolean toggleSlotLocked(int slot) {
        if (slot < 0 || slot >= CONTAINER_SIZE) {
            return false;
        }

        if (isSlotLocked(slot)) {
            lockedSlots &= ~(1 << slot);
            setChanged();
            return true;
        }

        if (!items.get(slot).isEmpty() || getUnlockedSlotCount() <= 1) {
            return false;
        }

        lockedSlots |= 1 << slot;
        setChanged();
        return true;
    }

    private int getUnlockedSlotCount() {
        return CONTAINER_SIZE - Integer.bitCount(lockedSlots);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt(LOCKED_SLOTS_TAG, lockedSlots);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.contains(LOCKED_SLOTS_TAG)) {
            lockedSlots = tag.getInt(LOCKED_SLOTS_TAG) & SLOT_MASK;
            return;
        }

        lockedSlots = DEFAULT_LOCKED_SLOTS;
        if (!items.get(0).isEmpty() && items.get(DEFAULT_UNLOCKED_SLOT).isEmpty()) {
            items.set(DEFAULT_UNLOCKED_SLOT, items.get(0));
            items.set(0, ItemStack.EMPTY);
        } else if (!items.get(0).isEmpty()) {
            lockedSlots &= ~1;
        }
    }
}
