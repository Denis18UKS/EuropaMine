package com.z_mods.barotrauma.blocks;

import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class VentDecoRenderer extends GeoBlockRenderer<VentDecoEntity> {
    public VentDecoRenderer() {
        super(new VentDecoModel());
    }
}