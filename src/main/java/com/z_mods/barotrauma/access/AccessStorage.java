package com.z_mods.barotrauma.access;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AccessStorage {
    private static final String ACCESS_LEVEL_TAG = "BarotraumaAccessLevel";

    private AccessStorage() {
    }

    public static AccessLevel get(BlockEntity blockEntity) {
        return AccessLevel.byId(blockEntity.getPersistentData().getString(ACCESS_LEVEL_TAG));
    }

    public static void set(BlockEntity blockEntity, AccessLevel accessLevel) {
        CompoundTag persistentData = blockEntity.getPersistentData();
        AccessLevel storedLevel = accessLevel == null ? AccessLevel.NONE : accessLevel;
        if (storedLevel == AccessLevel.NONE) {
            persistentData.remove(ACCESS_LEVEL_TAG);
        } else {
            persistentData.putString(ACCESS_LEVEL_TAG, storedLevel.getId());
        }
        blockEntity.setChanged();
    }
}
