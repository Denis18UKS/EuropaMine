package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.z_mods.barotrauma.blocks.HotbarLayoutPanelBlock;
import com.z_mods.barotrauma.blocks.HotbarLayoutPanelBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Always-visible wall UI for hotbar slot layout. */
public final class HotbarLayoutPanelRenderer implements BlockEntityRenderer<HotbarLayoutPanelBlockEntity> {
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final int TEXT = 0xFFFFF6C8;
    private static final int MUTED = 0xFFA5B5AF;
    private static final int ACCENT = 0xFF64CDB3;
    private static final int PANEL = 0xF207100E;
    private final Font font;

    public HotbarLayoutPanelRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(HotbarLayoutPanelBlockEntity entity, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = entity.getBlockState().getValue(HotbarLayoutPanelBlock.FACING);
        Direction right = facing.getCounterClockWise();

        poses.pushPose();
        poses.translate(0.5D + right.getStepX() * 3.0D, 2.0D,
                0.5D + right.getStepZ() * 3.0D);
        poses.translate(facing.getStepX() * 0.506D, 0.0D, facing.getStepZ() * 0.506D);
        poses.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poses.scale(0.013F, -0.013F, 0.013F);

        rect(poses, buffers, -240, -135, 240, 135, PANEL, 0.010F);
        border(poses, buffers, -238, -133, 238, 133, 0xFF5CA894, 2.0F, 0.012F);
        drawCentered(poses, buffers, "НАСТРОЙКА СЛОТОВ ХОТБАРА", 0, -128, TEXT);
        drawText(poses, buffers, "ПРЕДВАРИТЕЛЬНЫЙ ПРОСМОТР", -222, -112, TEXT);
        button(poses, buffers, HotbarLayoutSettings.SHOW_X1, HotbarLayoutSettings.SHOW_Y1,
                HotbarLayoutSettings.SHOW_X2, HotbarLayoutSettings.SHOW_Y2,
                HotbarLayoutSettings.showDraftOnHud() ? "СКРЫТЬ" : "ПОКАЗАТЬ",
                HotbarLayoutSettings.showDraftOnHud());

        rect(poses, buffers, HotbarLayoutSettings.PREVIEW_LEFT, HotbarLayoutSettings.PREVIEW_TOP,
                HotbarLayoutSettings.PREVIEW_RIGHT, HotbarLayoutSettings.PREVIEW_BOTTOM, 0xD9010504, 0.014F);
        border(poses, buffers, HotbarLayoutSettings.PREVIEW_LEFT, HotbarLayoutSettings.PREVIEW_TOP,
                HotbarLayoutSettings.PREVIEW_RIGHT, HotbarLayoutSettings.PREVIEW_BOTTOM, 0xFF35584F, 1.0F, 0.015F);

        texture(poses, buffers, WIDGETS,
                HotbarLayoutSettings.PREVIEW_VANILLA_X, HotbarLayoutSettings.PREVIEW_HOTBAR_Y,
                HotbarLayoutSettings.PREVIEW_VANILLA_X + 182, HotbarLayoutSettings.PREVIEW_HOTBAR_Y + 22,
                0.0F, 0.0F, 182.0F / 256.0F, 22.0F / 256.0F, 0.020F);

        int selected = HotbarLayoutSettings.selectedDraftSlot();
        int count = HotbarLayoutSettings.draftCount();
        for (int i = 0; i < count; i++) {
            int x = HotbarLayoutSettings.PREVIEW_EXTRA_BASE_X + HotbarLayoutSettings.draftX(i);
            int y = HotbarLayoutSettings.PREVIEW_HOTBAR_Y + HotbarLayoutSettings.draftY(i);
            drawVanillaSlot(poses, buffers, x, y, selected == i);
            drawCentered(poses, buffers, Integer.toString(10 + i), x + 11, y + 7, 0xFFD8E2DD);
        }

        drawText(poses, buffers, "Выбран: слот " + (10 + selected), 150, 62, ACCENT);
        drawText(poses, buffers, "Положение: " + HotbarLayoutSettings.draftX(selected)
                + ", " + HotbarLayoutSettings.draftY(selected), 150, 76, MUTED);

        button(poses, buffers, HotbarLayoutSettings.UP_X1, HotbarLayoutSettings.UP_Y1,
                HotbarLayoutSettings.UP_X2, HotbarLayoutSettings.UP_Y2, "▲", false);
        button(poses, buffers, HotbarLayoutSettings.LEFT_X1, HotbarLayoutSettings.LEFT_Y1,
                HotbarLayoutSettings.LEFT_X2, HotbarLayoutSettings.LEFT_Y2, "◀", false);
        button(poses, buffers, HotbarLayoutSettings.RIGHT_X1, HotbarLayoutSettings.RIGHT_Y1,
                HotbarLayoutSettings.RIGHT_X2, HotbarLayoutSettings.RIGHT_Y2, "▶", false);
        button(poses, buffers, HotbarLayoutSettings.DOWN_X1, HotbarLayoutSettings.DOWN_Y1,
                HotbarLayoutSettings.DOWN_X2, HotbarLayoutSettings.DOWN_Y2, "▼", false);

        button(poses, buffers, HotbarLayoutSettings.MINUS_X1, HotbarLayoutSettings.MINUS_Y1,
                HotbarLayoutSettings.MINUS_X2, HotbarLayoutSettings.MINUS_Y2, "−", false);
        button(poses, buffers, HotbarLayoutSettings.PLUS_X1, HotbarLayoutSettings.PLUS_Y1,
                HotbarLayoutSettings.PLUS_X2, HotbarLayoutSettings.PLUS_Y2, "+", false);
        drawText(poses, buffers, "Доп. слотов: " + count + "  |  Всего: " + (9 + count), -140, 100, TEXT);
        button(poses, buffers, HotbarLayoutSettings.SAVE_X1, HotbarLayoutSettings.SAVE_Y1,
                HotbarLayoutSettings.SAVE_X2, HotbarLayoutSettings.SAVE_Y2, "СОХРАНИТЬ", false);
        button(poses, buffers, HotbarLayoutSettings.APPLY_X1, HotbarLayoutSettings.APPLY_Y1,
                HotbarLayoutSettings.APPLY_X2, HotbarLayoutSettings.APPLY_Y2, "ПРИМЕНИТЬ", true);

        String notice = HotbarLayoutSettings.notice();
        if (!notice.isEmpty()) drawCentered(poses, buffers, notice, 0, 127, ACCENT);
        poses.popPose();
    }

    private void drawVanillaSlot(PoseStack poses, MultiBufferSource buffers, float x, float y, boolean selected) {
        texture(poses, buffers, WIDGETS, x, y, x + 22, y + 22,
                20.0F / 256.0F, 0.0F, 42.0F / 256.0F, 22.0F / 256.0F, 0.022F);
        if (selected) {
            texture(poses, buffers, WIDGETS, x - 1, y - 1, x + 23, y + 23,
                    0.0F, 22.0F / 256.0F, 24.0F / 256.0F, 46.0F / 256.0F, 0.024F);
        }
    }

    private void button(PoseStack poses, MultiBufferSource buffers, float x1, float y1, float x2, float y2,
                        String label, boolean active) {
        rect(poses, buffers, x1, y1, x2, y2, active ? 0xFF315A50 : 0xFF18231F, 0.020F);
        border(poses, buffers, x1, y1, x2, y2, active ? 0xFF79D8C0 : 0xFF55746A, 1.0F, 0.022F);
        drawCentered(poses, buffers, label, (x1 + x2) / 2.0F, (y1 + y2) / 2.0F - 4, TEXT);
    }

    private void drawText(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color) {
        FormattedCharSequence line = fixed(text);
        font.drawInBatch(line, x, y, color, true, poses.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private void drawCentered(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color) {
        FormattedCharSequence line = fixed(text);
        font.drawInBatch(line, x - font.width(line) / 2.0F, y, color, true, poses.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private FormattedCharSequence fixed(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(AbstractPanelScreen.PANEL_FONT).withBold(true))
                .getVisualOrderText();
    }

    private static void border(PoseStack poses, MultiBufferSource buffers, float x1, float y1, float x2, float y2,
                               int color, float thickness, float z) {
        rect(poses, buffers, x1, y1, x2, y1 + thickness, color, z);
        rect(poses, buffers, x1, y2 - thickness, x2, y2, color, z);
        rect(poses, buffers, x1, y1, x1 + thickness, y2, color, z);
        rect(poses, buffers, x2 - thickness, y1, x2, y2, color, z);
    }

    private static void rect(PoseStack poses, MultiBufferSource buffers, float left, float top,
                             float right, float bottom, int color, float z) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.gui());
        int alpha = color >>> 24;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        vertices.vertex(poses.last().pose(), left, bottom, z).color(red, green, blue, alpha).endVertex();
        vertices.vertex(poses.last().pose(), right, bottom, z).color(red, green, blue, alpha).endVertex();
        vertices.vertex(poses.last().pose(), right, top, z).color(red, green, blue, alpha).endVertex();
        vertices.vertex(poses.last().pose(), left, top, z).color(red, green, blue, alpha).endVertex();
    }

    private static void texture(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                                float left, float top, float right, float bottom,
                                float u0, float v0, float u1, float v1, float z) {
        PoseStack.Pose pose = poses.last();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
        vertex(vertices, pose, left, bottom, z, u0, v1);
        vertex(vertices, pose, right, bottom, z, u1, v1);
        vertex(vertices, pose, right, top, z, u1, v0);
        vertex(vertices, pose, left, top, z, u0, v0);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        vertices.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, 1).endVertex();
    }

    @Override public boolean shouldRenderOffScreen(HotbarLayoutPanelBlockEntity entity) { return true; }
    @Override public int getViewDistance() { return 512; }
}
