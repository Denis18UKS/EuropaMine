package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.item.GarnitureHelmetItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GarnitureHelmetModel extends GeoModel<GarnitureHelmetItem> {
    @Override
    public ResourceLocation getModelResource(GarnitureHelmetItem animatable) {
        return new ResourceLocation(Barotrauma.MOD_ID, "geo/armor/garniture.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GarnitureHelmetItem animatable) {
        return new ResourceLocation(Barotrauma.MOD_ID, "textures/armor/garniture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GarnitureHelmetItem animatable) {
        return new ResourceLocation(Barotrauma.MOD_ID, "animations/armor/garniture.animation.json");
    }
}
