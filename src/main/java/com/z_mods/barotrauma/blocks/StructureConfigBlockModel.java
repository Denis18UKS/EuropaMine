package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.init.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class StructureConfigBlockModel extends GeoModel<StructureConfigBlockEntity> {
    @Override
    public ResourceLocation getModelResource(StructureConfigBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "geo/block/electrical/submarine_button_block.geo.json");
        }
        return new ResourceLocation(Barotrauma.MOD_ID, "geo/block/electrical/submarine_door.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(StructureConfigBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "textures/block/submarine_button_block.png");
        }
        return new ResourceLocation(Barotrauma.MOD_ID, "textures/block/submarine_door.png");
    }

    @Override
    public ResourceLocation getAnimationResource(StructureConfigBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
            return new ResourceLocation(Barotrauma.MOD_ID, "animations/electrical/submarine_btn_click.json");
        }
        return new ResourceLocation(Barotrauma.MOD_ID, "animations/electrical/submarine_door.anim.json");
    }
}
