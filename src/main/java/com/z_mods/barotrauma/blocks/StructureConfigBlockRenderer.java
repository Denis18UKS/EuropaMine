package com.z_mods.barotrauma.blocks;

import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class StructureConfigBlockRenderer extends GeoBlockRenderer<AnimatedStructureConfigBlockEntity> {
    public StructureConfigBlockRenderer() {
        super(new StructureConfigBlockModel());
    }
}
