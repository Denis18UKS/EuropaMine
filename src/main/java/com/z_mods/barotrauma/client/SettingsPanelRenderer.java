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

/** Full-bright, always visible summary painted over the physical 7x4 panel. */
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
        poses.scale(0.029F, -0.029F, 0.029F);

        drawCentered(poses, buffers, "НАСТРОЙКИ СЕРВЕРА", 0, -64, 0xFFFFFFFF);
        drawCentered(poses, buffers, modeName(settings.gameMode), -79, -50, 0xFFFFF1A6);
        drawCentered(poses, buffers, PanelSettings.SUBMARINES.get(settings.submarine), 0, -50, 0xFFFFF1A6);
        drawCentered(poses, buffers, "ИГРОКИ: " + settings.minimumPlayers + "+", 79, -50, 0xFFFFF1A6);

        drawText(poses, buffers, "РЕЖИМ", -111, -36, 0xFF8AD6BE);
        drawText(poses, buffers, modeName(settings.gameMode), -111, -24, 0xFFFFFFFF);
        drawText(poses, buffers, missionSummary(settings), -111, -12, 0xFFFFF6C8);
        drawText(poses, buffers, "Сложность: " + settings.difficulty + "%", -111, 0, 0xFFFFFFFF);
        drawText(poses, buffers, "Боты: " + settings.botCount, -111, 12, 0xFFFFFFFF);
        drawText(poses, buffers, "Предатели: " + settings.betrayalChance + "%", -111, 24, 0xFFFFFFFF);
        drawText(poses, buffers, "Мин. игроков: " + settings.minimumPlayers, -111, 36, 0xFFFFFFFF);
        drawText(poses, buffers, settings.autoRestart ? "Автоперезапуск: ДА" : "Автоперезапуск: НЕТ",
                -111, 49, settings.autoRestart ? 0xFF7FFFD4 : 0xFFBCC6C1);

        drawPhoto(poses, buffers, ClientPanelPhotos.texture(settings.submarine), -35, -37, 35, 15);
        drawCentered(poses, buffers, "Цена: " + PanelSettings.submarinePrice(settings.submarine) + " кред.",
                0, 20, settings.canUseSubmarine(settings.submarine) ? 0xFFFFFFFF : 0xFFFF6F6F);
        drawCentered(poses, buffers, "Зона: " + zoneName(settings.naturalZone), 0, 33, 0xFFFFF6C8);
        drawCentered(poses, buffers, settings.gameMode == 3 ? "Кампания: " + settings.saveName : "Шифр: " + settings.levelSeed,
                0, 46, 0xFFFFFFFF);

        drawText(poses, buffers, "ВОЗРОЖДЕНИЕ", 43, -36, 0xFF8AD6BE);
        drawText(poses, buffers, respawnName(settings.respawnMode), 43, -24, 0xFFFFFFFF);
        drawText(poses, buffers, "Интервал: " + settings.respawnInterval + " с", 43, -12, 0xFFFFFFFF);
        drawText(poses, buffers, "Порог: " + settings.respawnThreshold + "%", 43, 0, 0xFFFFFFFF);
        drawText(poses, buffers, "Окно: " + settings.respawnWindow + " мин", 43, 12, 0xFFFFFFFF);
        drawText(poses, buffers, "Потеря навыка: " + settings.skillLossDeath + "%", 43, 24, 0xFFFFFFFF);
        drawText(poses, buffers, "Замена: " + settings.replacementCost + "%", 43, 36, 0xFFFFFFFF);
        drawText(poses, buffers, settings.ironMan ? "ЖЕЛЕЗНЫЙ ЧЕЛОВЕК" : "Обычные правила",
                43, 49, settings.ironMan ? 0xFFFF7A7A : 0xFF7FFFD4);
        poses.popPose();
    }

    private void drawText(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color) {
        font.drawInBatch(text, x, y, color, true,
                poses.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private void drawCentered(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color) {
        font.drawInBatch(text, x - font.width(text) / 2.0F, y, color, true,
                poses.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "ПЕСОЧНИЦА";
            case 2 -> "PVP";
            case 3 -> "КАМПАНИЯ";
            default -> "МИССИЯ";
        };
    }

    private static String missionSummary(PanelSettings settings) {
        return switch (settings.gameMode) {
            case 0 -> "Без обязательных миссий";
            case 2 -> "PVP-задач: " + count(settings.pvpMissionEnabled);
            case 3 -> "Кампания";
            default -> "Миссий: " + count(settings.missionEnabled);
        };
    }

    private static int count(boolean[] values) {
        int total = 0;
        for (boolean value : values) if (value) total++;
        return total;
    }

    private static String zoneName(int zone) {
        return switch (zone) {
            case 1 -> "Холодные пещеры";
            case 2 -> "Европейский хребет";
            case 3 -> "Бездна";
            case 4 -> "Руины";
            default -> "Случайно";
        };
    }

    private static String respawnName(int mode) {
        return switch (mode) {
            case 1 -> "Между раундами";
            case 2 -> "Отключено";
            default -> "В ходе раунда";
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
        return 512;
    }
}
