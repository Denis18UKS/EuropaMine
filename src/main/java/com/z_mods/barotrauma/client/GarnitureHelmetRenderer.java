package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.item.GarnitureHelmetItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class GarnitureHelmetRenderer extends GeoArmorRenderer<GarnitureHelmetItem> {
    public GarnitureHelmetRenderer() {
        super(new GarnitureHelmetModel());
    }
}
