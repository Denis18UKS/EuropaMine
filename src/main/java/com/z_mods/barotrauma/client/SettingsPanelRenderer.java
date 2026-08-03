package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.z_mods.barotrauma.blocks.SettingsPanelBlock;
import com.z_mods.barotrauma.blocks.SettingsPanelBlockEntity;
import com.z_mods.barotrauma.panel.PanelSettings;
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
        // A wider logical canvas and a fixed uniform font keep the physical panel identical with
        // "Force Unicode font" enabled or disabled. NORMAL/POLYGON_OFFSET also prevents text from
        // bleeding through the enlarged photo or distant blocks.
        poses.scale(0.020F, -0.020F, 0.020F);

        drawPhoto(poses, buffers, ClientPanelPhotos.texture(settings.submarine), -43, -48, 43, 12);
        drawCentered(poses, buffers, "НАСТРОЙКИ СЕРВЕРА", 0, -86, 0xFFFFFFFF, 220);
        drawCentered(poses, buffers, modeName(settings.gameMode), -106, -68, 0xFFFFF1A6, 90);
        drawCentered(poses, buffers, PanelSettings.SUBMARINES.get(settings.submarine), 0, -68, 0xFFFFF1A6, 98);
        drawCentered(poses, buffers, "ИГРОКИ: " + settings.minimumPlayers + "+", 106, -68, 0xFFFFF1A6, 90);

        drawText(poses, buffers, "РЕЖИМ", -157, -48, 0xFF8AD6BE, 100);
        drawText(poses, buffers, modeName(settings.gameMode), -157, -31, 0xFFFFFFFF, 100);
        drawText(poses, buffers, missionSummary(settings), -157, -14, 0xFFFFF6C8, 100);
        drawText(poses, buffers, "Сложность: " + settings.difficulty + "%", -157, 3, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Боты: " + settings.botCount, -157, 20, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Предатели: " + settings.betrayalChance + "%", -157, 37, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Игроков: " + settings.minimumPlayers, -157, 54, 0xFFFFFFFF, 100);

        drawCentered(poses, buffers, "Цена: " + PanelSettings.submarinePrice(settings.submarine) + " кред.",
                0, 21, settings.canUseSubmarine(settings.submarine) ? 0xFFFFFFFF : 0xFFFF6F6F, 108);
        drawCentered(poses, buffers, "Зона: " + zoneName(settings.naturalZone), 0, 39, 0xFFFFF6C8, 108);
        drawCentered(poses, buffers,
                settings.gameMode == 3 ? "Кампания: " + settings.saveName : "Шифр: " + settings.levelSeed,
                0, 57, 0xFFFFFFFF, 108);

        drawText(poses, buffers, "ВОЗРОЖДЕНИЕ", 57, -48, 0xFF8AD6BE, 100);
        drawText(poses, buffers, respawnName(settings.respawnMode), 57, -31, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Интервал: " + settings.respawnInterval + " с", 57, -14, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Порог: " + settings.respawnThreshold + "%", 57, 3, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Окно: " + settings.respawnWindow + " мин", 57, 20, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Потеря: " + settings.skillLossDeath + "%", 57, 37, 0xFFFFFFFF, 100);
        drawText(poses, buffers, "Замена: " + settings.replacementCost + "%", 57, 54, 0xFFFFFFFF, 100);

        drawCentered(poses, buffers, settings.autoRestart ? "АВТОПЕРЕЗАПУСК: ДА" : "АВТОПЕРЕЗАПУСК: НЕТ",
                -82, 77, settings.autoRestart ? 0xFF7FFFD4 : 0xFFBCC6C1, 145);
        drawCentered(poses, buffers, settings.ironMan ? "ЖЕЛЕЗНЫЙ ЧЕЛОВЕК" : "ОБЫЧНЫЕ ПРАВИЛА",
                82, 77, settings.ironMan ? 0xFFFF7A7A : 0xFF7FFFD4, 145);
        poses.popPose();
    }

    private void drawText(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color,
                          int maximumWidth) {
        FormattedCharSequence line = fixed(fit(text, maximumWidth));
        font.drawInBatch(line, x, y, color, true,
                poses.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private void drawCentered(PoseStack poses, MultiBufferSource buffers, String text, float x, float y, int color,
                              int maximumWidth) {
        FormattedCharSequence line = fixed(fit(text, maximumWidth));
        font.drawInBatch(line, x - font.width(line) / 2.0F, y, color, true,
                poses.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private FormattedCharSequence fixed(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(AbstractPanelScreen.PANEL_FONT)).getVisualOrderText();
    }

    private String fit(String text, int maximumWidth) {
        if (font.width(fixed(text)) <= maximumWidth) return text;
        String result = text;
        while (!result.isEmpty() && font.width(fixed(result + "…")) > maximumWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "ПЕСОЧНИЦА";
            case 2 -> "ПРОТИВ ИГРОКА";
            case 3 -> "КАМПАНИЯ";
            default -> "МИССИЯ";
        };
    }

    private static String missionSummary(PanelSettings settings) {
        return switch (settings.gameMode) {
            case 0 -> "Без обязательных миссий";
            case 2 -> "Задач: " + count(settings.pvpMissionEnabled);
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
