package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.z_mods.barotrauma.entity.SubmarineContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the captured hull as one continuously translated/rotated contraption. */
public final class SubmarineContraptionRenderer extends EntityRenderer<SubmarineContraptionEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public SubmarineContraptionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SubmarineContraptionEntity entity, float entityYaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int packedLight) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        poses.mulPose(Axis.ZP.rotationDegrees(pitch));

        for (SubmarineContraptionEntity.BlockSnapshot snapshot : entity.blocks()) {
            BlockState state = snapshot.state();
            if (state.isAir()) continue;
            poses.pushPose();
            poses.translate(snapshot.x() - entity.pivotLocalX(),
                    snapshot.y() - entity.pivotLocalY(),
                    snapshot.z() - entity.pivotLocalZ());
            blockRenderer.renderSingleBlock(state, poses, buffers, packedLight, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        poses.popPose();
        super.render(entity, entityYaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SubmarineContraptionEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
