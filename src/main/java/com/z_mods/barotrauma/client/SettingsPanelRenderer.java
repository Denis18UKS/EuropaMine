package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.z_mods.barotrauma.blocks.SettingsPanelBlock;
import com.z_mods.barotrauma.blocks.SettingsPanelBlockEntity;
import com.z_mods.barotrauma.panel.PanelSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-bright dashboard painted over the complete physical 7x4 settings panel. */
public final class SettingsPanelRenderer implements BlockEntityRenderer<SettingsPanelBlockEntity> {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFFFF6C8;
    private static final int MUTED = 0xFF9AA9A3;
    private static final int ACCENT = 0xFF65C4A8;
    private static final int DANGER = 0xFFDB6868;
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
        poses.scale(0.013F, -0.013F, 0.013F);

        rect(poses, buffers, -240, -135, 240, 135, 0xF207100E, 0.010F);
        rect(poses, buffers, -239, -134, 239, -132, ACCENT, 0.012F);
        rect(poses, buffers, -121, -132, -119, 132, 0xFF557B6F, 0.012F);
        rect(poses, buffers, -2, -132, 0, 132, 0xFF557B6F, 0.012F);
        rect(poses, buffers, 119, -132, 121, 132, 0xFF557B6F, 0.012F);

        drawModesAndMissions(poses, buffers, settings);
        drawSubmarine(poses, buffers, settings);
        drawSettings(poses, buffers, settings);
        drawPlayerAndChat(poses, buffers, settings);
        poses.popPose();
    }

    private void drawModesAndMissions(PoseStack poses, MultiBufferSource buffers, PanelSettings settings) {
        drawText(poses, buffers, "РЕЖИМ ИГРЫ", -234, -126, WHITE, 108);
        String[] modes = {"Песочница", "Миссия", "Игрок против игрока", "Кампания"};
        for (int i = 0; i < modes.length; i++) {
            drawText(poses, buffers, (settings.gameMode == i ? "● " : "○ ") + modes[i],
                    -234, -111 + i * 13, settings.gameMode == i ? ACCENT : MUTED, 108);
        }
        drawText(poses, buffers, settings.gameMode == 3 ? "КАМПАНИЯ" : "ТИП МИССИИ", -234, -52, WHITE, 108);
        List<String> missions = selectedMissionNames(settings);
        if (missions.isEmpty()) missions = List.of("Без обязательных задач");
        int visible = Math.min(8, missions.size());
        for (int i = 0; i < visible; i++) {
            drawText(poses, buffers, "✓ " + missions.get(i), -234, -37 + i * 12, TEXT, 108);
        }
        if (missions.size() > visible) {
            drawText(poses, buffers, "Ещё: " + (missions.size() - visible), -234, -37 + visible * 12, ACCENT, 108);
        }
        drawText(poses, buffers, "Игроков: " + settings.minimumPlayers + "+", -234, 112, WHITE, 108);
        drawText(poses, buffers, settings.autoRestart ? "Автоперезапуск: да" : "Автоперезапуск: нет",
                -234, 124, settings.autoRestart ? ACCENT : MUTED, 108);
    }

    private void drawSubmarine(PoseStack poses, MultiBufferSource buffers, PanelSettings settings) {
        drawText(poses, buffers, "ПОДЛОДКА", -114, -126, WHITE, 108);
        drawPhoto(poses, buffers, ClientPanelPhotos.texture(settings.submarine), -113, -111, -7, -53);
        drawCentered(poses, buffers, PanelSettings.SUBMARINES.get(settings.submarine), -60, -49, TEXT, 106);
        int priceColor = settings.canUseSubmarine(settings.submarine) ? WHITE : DANGER;
        drawCentered(poses, buffers,
                String.format(Locale.ROOT, "%,d кред.", PanelSettings.submarinePrice(settings.submarine)),
                -60, -36, priceColor, 106);
        drawText(poses, buffers, "СПИСОК ПОДЛОДОК", -114, -19, WHITE, 108);
        int start = Math.max(0, Math.min(settings.submarine - 2, PanelSettings.SUBMARINES.size() - 6));
        for (int row = 0; row < 6; row++) {
            int index = start + row;
            boolean selected = settings.submarine == index;
            boolean locked = settings.gameMode == 3 && !settings.canUseSubmarine(index);
            drawText(poses, buffers, (selected ? "› " : "  ") + PanelSettings.SUBMARINES.get(index),
                    -114, -4 + row * 15, selected ? ACCENT : TEXT, 61);
            drawText(poses, buffers, String.format(Locale.ROOT, "%,d", PanelSettings.submarinePrice(index)),
                    -47, -4 + row * 15, locked ? DANGER : MUTED, 39);
        }
        drawText(poses, buffers, "Шифр: " + settings.levelSeed, -114, 100, WHITE, 108);
        drawText(poses, buffers, "Зона: " + zoneName(settings.naturalZone), -114, 113, TEXT, 108);
        drawText(poses, buffers, "Сложность: " + settings.difficulty + "%", -114, 126, TEXT, 108);
    }

    private void drawSettings(PoseStack poses, MultiBufferSource buffers, PanelSettings settings) {
        drawText(poses, buffers, "НАСТРОЙКИ ИГРЫ", 6, -126, WHITE, 108);
        drawText(poses, buffers, "Боты: " + settings.botCount, 6, -110, TEXT, 108);
        drawText(poses, buffers, "Предатели: " + settings.betrayalChance + "%", 6, -97, TEXT, 108);
        drawText(poses, buffers, "Опасность: " + settings.maxDanger + "/3", 6, -84, TEXT, 108);
        drawText(poses, buffers, "Мин. игроков: " + settings.minimumPlayers, 6, -71, TEXT, 108);
        drawText(poses, buffers, settings.ironMan ? "Железный человек" : "Обычные правила",
                6, -58, settings.ironMan ? DANGER : ACCENT, 108);

        drawText(poses, buffers, "ВОЗРОЖДЕНИЕ", 6, -39, WHITE, 108);
        drawText(poses, buffers, respawnName(settings.respawnMode), 6, -24, TEXT, 108);
        drawText(poses, buffers, settings.respawnShuttle
                        ? "Челнок: " + PanelSettings.RESPAWN_SHUTTLES.get(settings.respawnShuttleType)
                        : "Челнок: отключён",
                6, -11, settings.respawnShuttle ? ACCENT : MUTED, 108);
        drawText(poses, buffers, "Интервал: " + settings.respawnInterval + " с", 6, 2, TEXT, 108);
        drawText(poses, buffers, "Порог: " + settings.respawnThreshold + "%", 6, 15, TEXT, 108);
        drawText(poses, buffers, "Окно: " + settings.respawnWindow + " мин", 6, 28, TEXT, 108);
        drawText(poses, buffers, "Потеря навыка: " + settings.skillLossDeath + "%", 6, 41, TEXT, 108);

        drawText(poses, buffers, "ОЧКИ ПРЕИМУЩЕСТВА", 6, 60, WHITE, 108);
        int y = 75;
        for (int i = 0; i < settings.advantageEnabled.length && y <= 117; i++) {
            if (!settings.advantageEnabled[i]) continue;
            drawText(poses, buffers, "✓ " + PanelSettings.ADVANTAGES.get(i), 6, y, ACCENT, 108);
            y += 13;
        }
        if (y == 75) drawText(poses, buffers, "Не выбраны", 6, y, MUTED, 108);
        drawText(poses, buffers, "Осталось: " + settings.advantagePointsRemaining(), 6, 126, WHITE, 108);
    }

    private void drawPlayerAndChat(PoseStack poses, MultiBufferSource buffers, PanelSettings settings) {
        drawText(poses, buffers, "ИГРОК И ЧАТ", 126, -126, WHITE, 108);
        Minecraft minecraft = Minecraft.getInstance();
        String name = minecraft.player == null ? "Игрок" : minecraft.player.getName().getString();
        if (minecraft.player instanceof AbstractClientPlayer player) {
            drawHead(poses, buffers, player.getSkinTextureLocation(), 126, -112, 22);
        }
        drawText(poses, buffers, name, 152, -107, TEXT, 80);
        drawText(poses, buffers, "Профессия: Капитан", 126, -84, ACCENT, 108);
        drawText(poses, buffers, "НАБЛЮДАТЬ: НЕТ", 126, -69, MUTED, 108);

        drawText(poses, buffers, "ЧАТ", 126, -49, WHITE, 108);
        List<String> chat = ClientPanelChat.lines();
        int first = Math.max(0, chat.size() - 7);
        int y = -34;
        if (chat.isEmpty()) {
            drawText(poses, buffers, "Сообщений пока нет", 126, y, MUTED, 108);
        } else {
            for (int i = first; i < chat.size(); i++) {
                drawText(poses, buffers, chat.get(i), 126, y, 0xFF91EF9B, 108);
                y += 14;
            }
        }
        drawText(poses, buffers, "Введите сообщение в GUI", 126, 76, MUTED, 108);
        drawText(poses, buffers, "Подлодка: " + PanelSettings.SUBMARINES.get(settings.submarine),
                126, 96, TEXT, 108);
        drawText(poses, buffers, "Готовы начать: нет", 126, 111, MUTED, 108);
        drawCentered(poses, buffers, "НАЧАТЬ", 180, 125, WHITE, 108);
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
        return Component.literal(text).withStyle(
                Style.EMPTY.withFont(AbstractPanelScreen.PANEL_FONT).withBold(true)).getVisualOrderText();
    }

    private String fit(String text, int maximumWidth) {
        if (font.width(fixed(text)) <= maximumWidth) return text;
        String result = text;
        while (!result.isEmpty() && font.width(fixed(result + "…")) > maximumWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static List<String> selectedMissionNames(PanelSettings settings) {
        if (settings.gameMode == 0) return new ArrayList<>();
        if (settings.gameMode == 2) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < settings.pvpMissionEnabled.length; i++) {
                if (settings.pvpMissionEnabled[i]) result.add(PanelSettings.PVP_MISSIONS.get(i));
            }
            return result;
        }
        if (settings.gameMode == 3) return List.of(settings.saveName, "Баланс: " + settings.startingBalanceCredits());
        return settings.selectedMissions();
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

    private static void drawPhoto(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                                  float left, float top, float right, float bottom) {
        textureQuad(poses, buffers, texture, left, top, right, bottom, 0, 0, 1, 1, 0.05F);
    }

    private static void drawHead(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                                 float left, float top, float size) {
        textureQuad(poses, buffers, texture, left, top, left + size, top + size,
                8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, 0.06F);
    }

    private static void textureQuad(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
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

    @Override
    public boolean shouldRenderOffScreen(SettingsPanelBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
