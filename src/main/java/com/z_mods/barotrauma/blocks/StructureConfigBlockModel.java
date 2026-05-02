package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.init.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class StructureConfigBlockModel extends GeoModel<AnimatedStructureConfigBlockEntity> {
    @Override
    public ResourceLocation getModelResource(AnimatedStructureConfigBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "geo/block/electrical/submarine_button_block.geo.json");
        }
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_DOOR.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "geo/block/electrical/submarine_door.geo.json");
        }
        return new ResourceLocation(Barotrauma.MOD_ID, "geo/block/electrical/empty.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AnimatedStructureConfigBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "textures/block/bar_button_block.png");
        }
        return new ResourceLocation(Barotrauma.MOD_ID, "textures/block/submarine_door.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AnimatedStructureConfigBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "animations/electrical/bar_button.animation.json");
        }
        return new ResourceLocation(Barotrauma.MOD_ID, "animations/electrical/submarine_door.anim.json");
    }
}
