package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class AnimatedStructureConfigBlockEntity extends StructureConfigBlockEntity {
    public AnimatedStructureConfigBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANIMATED_STRUCTURE_CONFIG.get(), pos, state);
    }
}
