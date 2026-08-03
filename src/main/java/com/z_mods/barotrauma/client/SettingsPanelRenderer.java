package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.z_mods.barotrauma.blocks.SettingsPanelBlock;
import com.z_mods.barotrauma.blocks.SettingsPanelBlockEntity;
import com.z_mods.barotrauma.panel.PanelSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Full-bright world preview painted over the physical 7x4 panel. */
public final class SettingsPanelRenderer implements BlockEntityRenderer<SettingsPanelBlockEntity> {
    private final Font font;

    public SettingsPanelRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(SettingsPanelBlockEntity entity, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = entity.getBlockState().getValue(SettingsPanelBlock.FACING);
        Direction right = facing.getCounterClockWise();
        PanelSettings settings = ClientPanelState.settings();

        poses.pushPose();
        poses.translate(0.5D + right.getStepX() * 3.0D, 2.0D,
                0.5D + right.getStepZ() * 3.0D);
        poses.translate(facing.getStepX() * 0.506D, 0.0D, facing.getStepZ() * 0.506D);
        poses.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poses.scale(0.025F, -0.025F, 0.025F);

        drawCentered(poses, buffers, "НАСТРОЙКИ СЕРВЕРА", 0, -67, 0xFFB7D5CD);
        drawCentered(poses, buffers, modeName(settings.gameMode), -88, -48, 0xFFE9E3B0);
        drawCentered(poses, buffers, PanelSettings.SUBMARINES.get(settings.submarine), 0, 50, 0xFFFFE8A6);
        drawCentered(poses, buffers, "Игроков: " + settings.minimumPlayers + "+", 88, -48, 0xFFE9E3B0);
        drawCentered(poses, buffers, settings.autoRestart ? "Автоперезапуск: да" : "Автоперезапуск: нет",
                88, 51, settings.autoRestart ? 0xFF69D3B2 : 0xFF8B9690);
        drawPhoto(poses, buffers, ClientPanelPhotos.texture(settings.submarine), -50, -42, 50, 43);
        poses.popPose();
    }

    private void drawCentered(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color) {
        font.drawInBatch(text, x - font.width(text) / 2.0F, y, color, false,
                poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "ПЕСОЧНИЦА";
            case 2 -> "PVP";
            case 3 -> "КАМПАНИЯ";
            default -> "МИССИЯ";
        };
    }

    private static void drawPhoto(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                                  float left, float top, float right, float bottom) {
        PoseStack.Pose pose = poses.last();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        vertex(vertices, pose, left, bottom, 0, 1);
        vertex(vertices, pose, right, bottom, 1, 1);
        vertex(vertices, pose, right, top, 1, 0);
        vertex(vertices, pose, left, top, 0, 0);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v) {
        vertices.vertex(pose.pose(), x, y, 0.05F).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, 1).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(SettingsPanelBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
