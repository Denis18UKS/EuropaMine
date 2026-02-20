package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class VentDecoModel extends GeoModel<VentDecoEntity> {
    @Override
    public ResourceLocation getModelResource(VentDecoEntity object) {
        return new ResourceLocation(Barotrauma.MOD_ID, "geo/block/vent_deco.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(VentDecoEntity object) {
        // Используем текстуру корпуса
        return new ResourceLocation(Barotrauma.MOD_ID, "textures/block/vent_deco.png");
    }

    @Override
    public ResourceLocation getAnimationResource(VentDecoEntity animatable) {
        return new ResourceLocation(Barotrauma.MOD_ID, "animations/vent_deco.animation.json");
    }
}