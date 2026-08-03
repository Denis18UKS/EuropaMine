package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SettingsPanelBlockEntity extends BlockEntity {
    public SettingsPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SETTINGS_PANEL.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(8.0D, 5.0D, 8.0D);
    }
}
