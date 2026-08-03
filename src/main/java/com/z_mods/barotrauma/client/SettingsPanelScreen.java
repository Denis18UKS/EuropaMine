package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.PanelPackets;
import com.z_mods.barotrauma.panel.PanelSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Interactive server lobby/settings screen inspired by the supplied reference layout. */
public final class SettingsPanelScreen extends AbstractPanelScreen {
    private static final String[] MODES = {"ПЕСОЧНИЦА", "МИССИЯ", "ИГРОК ПРОТИВ ИГРОКА", "КАМПАНИЯ"};
    private static final String[] PROFESSIONS = {"Инженер", "Помощник", "Врач", "Механик", "Офицер охраны", "Капитан"};
    private static final String[] TEAMS = {"Коалиция", "Без предпочтений", "Сепаратисты"};
    private PanelSettings settings;
    private final boolean editable;
    private int submarineScroll;
    private int missionScroll;
    private int modeScroll;
    private String search = "";
    private String chat = "";
    private Focus focus = Focus.NONE;
    private String draggingSlider = "";
    private boolean respawnTab = true;
    private boolean watch;
    private boolean afk;
    private boolean ready;
    private int teamPreference = 1;
    private int profession = 5;
    private boolean professionPopup;
    private boolean loadoutPopup;
    private boolean enlargedPhoto;
    private String notice = "";
    private long noticeUntil;

    private enum Focus { NONE, SEARCH, SEED, SAVE, CHAT }

    public SettingsPanelScreen(PanelSettings settings, boolean editable) {
        super(Component.translatable("screen.barotrauma.settings_panel"));
        this.settings = settings;
        this.editable = editable;
    }

    public void applyServerSettings(PanelSettings value) {
        this.settings = value.copy();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beginCanvas(graphics);
        double mx = vx(mouseX), my = vy(mouseY);
        drawHeader(graphics, mx, my);
        drawModePanel(graphics, mx, my);
        drawSubmarinePanel(graphics, mx, my);
        drawMissionPanel(graphics, mx, my);
        drawGameSettings(graphics, mx, my);
        drawRespawnSettings(graphics, mx, my);
        drawPlayerPanel(graphics, mx, my);
        if (!editable) {
            graphics.fill(420, 5, 780, 24, 0xD080321F);
            centered(graphics, "Режим просмотра — изменять настройки может оператор", 600, 10, BRIGHT);
        }
        if (!notice.isEmpty() && System.currentTimeMillis() < noticeUntil) {
            graphics.fill(420, 641, 780, 662, 0xE0192824);
            centered(graphics, notice, 600, 647, BRIGHT);
        }
        if (professionPopup) drawProfessionPopup(graphics, mx, my);
        if (loadoutPopup) drawLoadoutPopup(graphics, mx, my);
        if (enlargedPhoto) drawPhotoPopup(graphics, mx, my);
        endCanvas(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics g, double mx, double my) {
        g.fill(15, 15, 815, 86, 0xFF132A25);
        border(g, 15, 15, 800, 71, BORDER);
        button(g, "Развлечение", 25, 23, 83, 18, true, inside(mx, my, 25, 23, 108, 41));
        button(g, "Открытый", 112, 23, 70, 18, true, inside(mx, my, 112, 23, 182, 41));
        String name = Minecraft.getInstance().player == null ? "Игрок" : Minecraft.getInstance().player.getName().getString();
        text(g, name, 26, 51, BRIGHT);
        button(g, "Описание", 25, 69, 160, 14, true, inside(mx, my, 25, 69, 185, 83));
        button(g, "НАСТРОЙКИ СЕРВЕРА", 665, 20, 140, 30, true, inside(mx, my, 665, 20, 805, 50));
        text(g, "⚙  Люди  Звук  Предатели  Другое", 653, 60, TEXT);
    }

    private void drawModePanel(GuiGraphics g, double mx, double my) {
        heading(g, "РЕЖИМ ИГРЫ", 20, 96);
        panel(g, 15, 110, 250, 240);
        int shown = 0;
        for (int i = modeScroll; i < MODES.length && shown < 4; i++, shown++) {
            int y = 120 + shown * 54;
            boolean selected = settings.gameMode == i;
            if (selected) g.fill(21, y - 3, 253, y + 45, 0x99203A33);
            text(g, selected ? "●" : "○", 25, y + 4, selected ? ACCENT : MUTED);
            text(g, MODES[i], 47, y, selected ? TEXT : MUTED);
            String description = switch (i) {
                case 0 -> "Свободное исследование без целей";
                case 1 -> "Командное выполнение выбранных задач";
                case 2 -> "Соревнование двух команд";
                default -> "Долгое плавание с сохранением";
            };
            text(g, fit(description, 190), 47, y + 16, selected ? TEXT : 0xFF3E4945);
            text(g, fit(i == 3 ? "Развивайте экипаж и подлодку" : "Параметры задаются ниже", 190),
                    47, y + 29, selected ? TEXT : 0xFF3E4945);
        }
        scrollbar(g, 256, 115, 230, modeScroll, Math.max(0, MODES.length - 4));
    }

    private void drawSubmarinePanel(GuiGraphics g, double mx, double my) {
        heading(g, "ПОДЛОДКА", 275, 96);
        panel(g, 270, 110, 270, 240);
        g.fill(276, 116, 534, 134, 0xFF07100E);
        border(g, 276, 116, 258, 18, focus == Focus.SEARCH ? ACCENT : MUTED);
        text(g, search.isEmpty() ? "Поиск" : search, 281, 121, search.isEmpty() ? MUTED : BRIGHT);
        text(g, "⌕", 517, 120, ACCENT);
        List<Integer> filtered = filteredSubmarines();
        submarineScroll = Mth.clamp(submarineScroll, 0, Math.max(0, filtered.size() - 8));
        for (int row = 0; row < 8 && submarineScroll + row < filtered.size(); row++) {
            int index = filtered.get(submarineScroll + row);
            int y = 141 + row * 24;
            boolean selected = settings.submarine == index;
            if (selected) g.fill(276, y - 3, 531, y + 20, 0xFF243B34);
            text(g, PanelSettings.SUBMARINES.get(index), 282, y, selected ? BRIGHT : TEXT);
            text(g, (index % 3 == 0 ? "Транспортная" : index % 3 == 1 ? "Боевая" : "Разведывательная"),
                    446, y + 9, selected ? ACCENT : MUTED);
            text(g, String.format(Locale.ROOT, "%,d кр.", 5000 + index * 2300), 465, y - 1,
                    selected ? TEXT : MUTED);
        }
        scrollbar(g, 532, 140, 202, submarineScroll, Math.max(0, filtered.size() - 8));

        panel(g, 548, 110, 267, 124);
        photo(g, ClientPanelPhotos.texture(settings.submarine), 554, 116, 255, 112);
        button(g, "⛶", 782, 201, 22, 21, true, inside(mx, my, 782, 201, 804, 222));
        panel(g, 548, 239, 267, 111);
        heading(g, PanelSettings.SUBMARINES.get(settings.submarine), 557, 247);
        text(g, submarineType(settings.submarine), 557, 264, TEXT);
        text(g, "Цена", 557, 282, TEXT);
        text(g, String.format(Locale.ROOT, "%,d кредитов", 5000 + settings.submarine * 2300), 690, 282, BRIGHT);
        text(g, "Габариты", 557, 297, TEXT);
        text(g, (35 + settings.submarine) + "×" + (9 + settings.submarine % 7) + " м", 690, 297, BRIGHT);
        text(g, "Грузоподъёмность", 557, 312, TEXT);
        text(g, (6 + settings.submarine % 5) + " ящиков", 690, 312, BRIGHT);
        text(g, "Рекомендуемая команда", 557, 327, TEXT);
        text(g, (3 + settings.submarine % 3) + "–" + (5 + settings.submarine % 4), 690, 327, BRIGHT);
    }

    private void drawMissionPanel(GuiGraphics g, double mx, double my) {
        heading(g, settings.gameMode == 3 ? "НАСТРОЙКИ КАМПАНИИ" : "ТИП МИССИИ", 20, 362);
        panel(g, 15, 376, 250, 258);
        if (settings.gameMode == 3) {
            button(g, "НОВАЯ КАМПАНИЯ", 21, 383, 113, 20, true, inside(mx, my, 21, 383, 134, 403));
            button(g, "ЗАГРУЗИТЬ", 139, 383, 118, 20, true, inside(mx, my, 139, 383, 257, 403));
            field(g, "Название сохранения", settings.saveName, 24, 415, 225, Focus.SAVE);
            field(g, "Шифр карты", settings.levelSeed, 24, 451, 225, Focus.SEED);
            text(g, "Уровень сложности", 25, 490, TEXT);
            button(g, difficultyName(), 132, 483, 116, 20, editable, inside(mx, my, 132, 483, 248, 503));
            checkbox(g, "Юпитерианская радиация", 25, 517, settings.radiation, editable);
            text(g, "Начальный запас", 25, 546, TEXT);
            button(g, choice(settings.startingSupplies, "Низкий", "Обычный", "Высокий"), 142, 540, 95, 20,
                    editable, inside(mx, my, 142, 540, 237, 560));
            text(g, "Начальный баланс", 25, 572, TEXT);
            button(g, choice(settings.startingBalance, "Низкий", "Средний", "Высокий"), 142, 566, 95, 20,
                    editable, inside(mx, my, 142, 566, 237, 586));
            text(g, "Макс. миссий за раунд", 25, 601, TEXT);
            text(g, "−", 150, 601, ACCENT);
            centered(g, Integer.toString(settings.maxMissionsPerRound), 190, 601, BRIGHT);
            text(g, "+", 229, 601, ACCENT);
            return;
        }
        button(g, "ВЫБРАТЬ ВСЕ", 21, 383, 112, 20, editable, inside(mx, my, 21, 383, 133, 403));
        button(g, "ОТМЕНИТЬ ВСЕ", 138, 383, 119, 20, editable, inside(mx, my, 138, 383, 257, 403));
        int visible = 13;
        missionScroll = Mth.clamp(missionScroll, 0, Math.max(0, PanelSettings.MISSIONS.size() - visible));
        for (int row = 0; row < visible && missionScroll + row < PanelSettings.MISSIONS.size(); row++) {
            int index = missionScroll + row;
            checkbox(g, fit(PanelSettings.MISSIONS.get(index), 205), 23, 411 + row * 16,
                    settings.missionEnabled[index], editable);
        }
        scrollbar(g, 256, 410, 216, missionScroll, Math.max(0, PanelSettings.MISSIONS.size() - visible));
    }

    private void drawGameSettings(GuiGraphics g, double mx, double my) {
        heading(g, "НАСТРОЙКИ РЕЖИМА ИГРЫ", 275, 362);
        panel(g, 270, 376, 270, 258);
        text(g, "Природная зона", 279, 386, TEXT);
        button(g, zoneName(), 386, 380, 143, 20, editable, inside(mx, my, 386, 380, 529, 400));
        field(g, "Шифр уровня", settings.levelSeed, 279, 409, 250, Focus.SEED);
        slider(g, "Сложность", 279, 445, 150, settings.difficulty, 100, "%");
        heading(g, "НАСТРОЙКИ БОТОВ", 279, 479);
        text(g, "Численность ботов", 279, 498, TEXT);
        text(g, "‹", 420, 498, ACCENT);
        centered(g, Integer.toString(settings.botCount), 461, 498, BRIGHT);
        text(g, "›", 507, 498, ACCENT);
        text(g, "Режим появления", 279, 520, TEXT);
        button(g, choice(settings.botMode, "Обычный", "Заполнение", "Без ботов"), 430, 514, 99, 20,
                editable, inside(mx, my, 430, 514, 529, 534));
        heading(g, "НАСТРОЙКИ ПРЕДАТЕЛЬСТВА", 279, 548);
        slider(g, "Вероятность предательства", 279, 565, 150, settings.betrayalChance, 100, "%");
        text(g, "Максимальная опасность", 279, 594, TEXT);
        text(g, "‹", 420, 594, ACCENT);
        centered(g, "☠".repeat(settings.maxDanger) + "·".repeat(3 - settings.maxDanger), 465, 594, DANGER);
        text(g, "›", 507, 594, ACCENT);
        text(g, "Мин. количество игроков", 279, 616, TEXT);
        text(g, "‹", 420, 616, ACCENT);
        centered(g, Integer.toString(settings.minimumPlayers), 465, 616, BRIGHT);
        text(g, "›", 507, 616, ACCENT);
    }

    private void drawRespawnSettings(GuiGraphics g, double mx, double my) {
        button(g, "НАСТРОЙКИ ВОЗРОЖДЕНИЯ", 548, 356, 137, 20, respawnTab,
                inside(mx, my, 548, 356, 685, 376));
        button(g, "ОЧКИ ПРЕИМУЩЕСТВА", 690, 356, 125, 20, !respawnTab,
                inside(mx, my, 690, 356, 815, 376));
        panel(g, 548, 376, 267, 258);
        if (respawnTab) {
            text(g, "Режим возрождения", 557, 386, TEXT);
            button(g, choice(settings.respawnMode, "В ходе раунда", "Между раундами", "Отключено"),
                    673, 380, 130, 20, editable, inside(mx, my, 673, 380, 803, 400));
            checkbox(g, "Челнок возрождения", 557, 411, settings.respawnShuttle, editable);
            slider(g, "Интервал", 557, 438, 140, settings.respawnInterval - 10, 290, " с");
            slider(g, "Порог игроков", 557, 477, 140, settings.respawnThreshold, 100, "%");
            slider(g, "Время действия челнока", 557, 516, 140, settings.respawnWindow, 30, " мин");
            slider(g, "Потеря навыка при смерти", 557, 555, 140, settings.skillLossDeath, 100, "%");
            slider(g, "Потеря при немедленном", 557, 594, 140, settings.skillLossImmediate, 100, "%");
        } else {
            heading(g, "ОЧКИ ПРЕИМУЩЕСТВА", 557, 388);
            slider(g, "Стоимость замены персонажа", 557, 415, 140, settings.replacementCost, 100, "%");
            checkbox(g, "Разрешить управление ботом", 557, 459, settings.botControl, editable);
            checkbox(g, "ЖЕЛЕЗНЫЙ ЧЕЛОВЕК", 557, 486, settings.ironMan, editable);
            text(g, "Эти параметры действуют для всех", 557, 528, MUTED);
            text(g, "игроков на сервере и сохраняются", 557, 542, MUTED);
            text(g, "вместе с миром.", 557, 556, MUTED);
        }
    }

    private void drawPlayerPanel(GuiGraphics g, double mx, double my) {
        panel(g, 830, 15, 355, 619);
        checkbox(g, "Наблюдать", 843, 31, watch, true);
        checkbox(g, "AFK", 1020, 31, afk, true);
        g.fill(842, 56, 1173, 74, 0xFF07100E);
        border(g, 842, 56, 331, 18, BORDER);
        String name = Minecraft.getInstance().player == null ? "Игрок" : Minecraft.getInstance().player.getName().getString();
        centered(g, name, 1008, 61, BRIGHT);
        button(g, "ПРЕДПОЧТЕНИЕ В РАБОТЕ", 842, 80, 160, 20, true, inside(mx, my, 842, 80, 1002, 100));
        button(g, "ВНЕШНОСТЬ", 1007, 80, 166, 20, false, inside(mx, my, 1007, 80, 1173, 100));
        panel(g, 842, 104, 331, 250);
        drawPlayerHead(g, 968, 116, 80);
        text(g, "1", 887, 226, profession == 0 ? BRIGHT : MUTED);
        text(g, "2", 998, 226, profession == 1 ? BRIGHT : MUTED);
        text(g, "3", 1109, 226, profession == 2 ? BRIGHT : MUTED);
        drawProfessionIcon(g, 870, 244, profession);
        text(g, PROFESSIONS[profession], 878, 327, profession == 2 ? DANGER : TEXT);
        centered(g, "Щёлкните для выбора профессии", 1060, 300, TEXT);
        text(g, "ⓘ", 853, 329, ACCENT);
        text(g, "⊗", 1140, 329, MUTED);
        for (int i = 0; i < TEAMS.length; i++) {
            int x = 842 + i * 110;
            button(g, TEAMS[i], x, 358, 110, 20, teamPreference == i, inside(mx, my, x, 358, x + 110, 378));
        }
        button(g, "ЧАТ", 842, 388, 160, 20, true, inside(mx, my, 842, 388, 1002, 408));
        button(g, "ЖУРНАЛ СЕРВЕРА", 1007, 388, 166, 20, false, inside(mx, my, 1007, 388, 1173, 408));
        panel(g, 842, 412, 197, 177);
        text(g, "[19:43] Игрок присоединился к серверу.", 849, 421, 0xFF91EF9B);
        text(g, "[19:44] Настройки панели загружены.", 849, 436, 0xFF91EF9B);
        text(g, "[19:45] Выбрана: " + fit(PanelSettings.SUBMARINES.get(settings.submarine), 95), 849, 451, 0xFF91EF9B);
        panel(g, 1044, 412, 129, 177);
        text(g, name, 1052, 421, TEXT);
        g.fill(842, 594, 331 + 842, 613, 0xFF07100E);
        border(g, 842, 594, 331, 19, focus == Focus.CHAT ? ACCENT : BORDER);
        text(g, chat.isEmpty() ? "Сообщение…" : fit(chat, 300), 849, 599, chat.isEmpty() ? MUTED : BRIGHT);
        checkbox(g, "Автоматический перезапуск", 620, 645, settings.autoRestart, editable);
        checkbox(g, "Готовы начать", 842, 645, ready, true);
        button(g, "НАЧАТЬ", 1038, 641, 147, 25, ready, inside(mx, my, 1038, 641, 1185, 666));
        button(g, "ОТКЛЮЧИТЬСЯ", 15, 641, 120, 25, true, inside(mx, my, 15, 641, 135, 666));
    }

    private void drawProfessionIcon(GuiGraphics g, int x, int y, int selected) {
        String icon = switch (selected) {
            case 0 -> "⚡";
            case 1 -> "✚";
            case 2 -> "♥";
            case 3 -> "⚙";
            case 4 -> "◆";
            default -> "★";
        };
        g.fill(x, y, x + 70, y + 70, 0xFF17211F);
        centered(g, icon, x + 35, y + 29, selected == 2 ? DANGER : ACCENT);
        border(g, x, y, 70, 70, BORDER);
    }

    private void drawProfessionPopup(GuiGraphics g, double mx, double my) {
        g.fill(0, 0, CANVAS_W, CANVAS_H, 0x99000000);
        panel(g, 510, 195, 340, 260);
        heading(g, "ВЫБОР ПРОФЕССИИ", 620, 207);
        for (int i = 0; i < PROFESSIONS.length; i++) {
            int col = i % 3, row = i / 3;
            int x = 525 + col * 105, y = 235 + row * 102;
            if (profession == i) g.fill(x - 4, y - 4, x + 82, y + 88, 0x773F806C);
            drawProfessionIcon(g, x, y, i);
            centered(g, PROFESSIONS[i], x + 35, y + 76, profession == i ? ACCENT : TEXT);
        }
        button(g, "Закрыть", 730, 420, 100, 24, true, inside(mx, my, 730, 420, 830, 444));
    }

    private void drawLoadoutPopup(GuiGraphics g, double mx, double my) {
        g.fill(0, 0, CANVAS_W, CANVAS_H, 0x88000000);
        panel(g, 385, 130, 430, 150);
        centered(g, "КОМПЛЕКТ #1 — " + PROFESSIONS[profession], 600, 145, TEXT);
        String[] items = {"Броня", "Пояс", "Инструмент", "Оружие", "Медикаменты"};
        for (int i = 0; i < items.length; i++) {
            int x = 405 + i * 78;
            g.fill(x, 174, x + 62, 236, 0xFF1A2321);
            border(g, x, 174, 62, 62, BORDER);
            centered(g, switch (i) { case 0 -> "◆"; case 1 -> "▱"; case 2 -> "⚒"; case 3 -> "†"; default -> "✚"; },
                    x + 31, 198, i == 4 ? ACCENT : TEXT);
            centered(g, items[i], x + 31, 245, MUTED);
        }
        button(g, "Закрыть", 700, 250, 100, 22, true, inside(mx, my, 700, 250, 800, 272));
    }

    private void drawPhotoPopup(GuiGraphics g, double mx, double my) {
        g.fill(0, 0, CANVAS_W, CANVAS_H, 0xD0000000);
        panel(g, 250, 55, 700, 565);
        centered(g, settings.photoNames[settings.submarine], 600, 70, BRIGHT);
        photo(g, ClientPanelPhotos.texture(settings.submarine), 280, 92, 640, 480);
        button(g, "Закрыть", 800, 582, 120, 25, true, inside(mx, my, 800, 582, 920, 607));
    }

    private void field(GuiGraphics g, String label, String value, int x, int y, int w, Focus field) {
        text(g, label, x, y, TEXT);
        g.fill(x, y + 14, x + w, y + 33, 0xFF07100E);
        border(g, x, y + 14, w, 19, focus == field ? ACCENT : BORDER);
        text(g, fit(value, w - 12) + (focus == field && System.currentTimeMillis() / 400 % 2 == 0 ? "_" : ""),
                x + 5, y + 19, BRIGHT);
    }

    private List<Integer> filteredSubmarines() {
        List<Integer> result = new ArrayList<>();
        String needle = search.toLowerCase(Locale.ROOT).strip();
        for (int i = 0; i < PanelSettings.SUBMARINES.size(); i++) {
            if (needle.isEmpty() || PanelSettings.SUBMARINES.get(i).toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(i);
            }
        }
        return result;
    }

    private String submarineType(int index) {
        return switch (index % 3) {
            case 0 -> "ТРАНСПОРТНАЯ (УРОВЕНЬ I)";
            case 1 -> "БОЕВАЯ (УРОВЕНЬ II)";
            default -> "РАЗВЕДЫВАТЕЛЬНАЯ (УРОВЕНЬ I)";
        };
    }

    private String zoneName() {
        return choice(settings.naturalZone, "Случайно", "Холодные пещеры", "Европейский хребет", "Бездна", "Руины");
    }

    private String difficultyName() {
        return choice(settings.difficulty / 34, "Обычный", "Сложный", "Экстремальный");
    }

    private static String choice(int index, String... values) {
        return values[Mth.clamp(index, 0, values.length - 1)];
    }

    private void changed() {
        ClientPanelState.apply(settings);
        if (editable) ModNetworking.CHANNEL.sendToServer(new PanelPackets.ServerboundSettings(settings.toTag()));
    }

    private void edit(Runnable action) {
        if (!editable) {
            notifyUser("Недостаточно прав для изменения настроек");
            return;
        }
        action.run();
        settings.sanitize();
        changed();
    }

    private void notifyUser(String message) {
        notice = message;
        noticeUntil = System.currentTimeMillis() + 2500;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = vx(mouseX), y = vy(mouseY);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (enlargedPhoto) {
            if (inside(x, y, 800, 582, 920, 607) || !inside(x, y, 250, 55, 950, 620)) enlargedPhoto = false;
            return true;
        }
        if (professionPopup) {
            for (int i = 0; i < PROFESSIONS.length; i++) {
                int px = 525 + (i % 3) * 105, py = 235 + (i / 3) * 102;
                if (inside(x, y, px, py, px + 82, py + 90)) {
                    profession = i;
                    professionPopup = false;
                    loadoutPopup = true;
                    return true;
                }
            }
            if (inside(x, y, 730, 420, 830, 444)) professionPopup = false;
            return true;
        }
        if (loadoutPopup) {
            if (inside(x, y, 700, 250, 800, 272) || !inside(x, y, 385, 130, 815, 280)) loadoutPopup = false;
            return true;
        }
        for (int i = 0; i < MODES.length; i++) {
            int row = i - modeScroll;
            if (row >= 0 && row < 4 && inside(x, y, 21, 117 + row * 54, 253, 165 + row * 54)) {
                final int mode = i; edit(() -> settings.gameMode = mode); return true;
            }
        }
        if (inside(x, y, 276, 116, 534, 134)) { focus = Focus.SEARCH; return true; }
        List<Integer> filtered = filteredSubmarines();
        for (int row = 0; row < 8 && submarineScroll + row < filtered.size(); row++) {
            if (inside(x, y, 276, 138 + row * 24, 531, 162 + row * 24)) {
                int selected = filtered.get(submarineScroll + row);
                edit(() -> settings.submarine = selected); return true;
            }
        }
        if (inside(x, y, 782, 201, 804, 222)) { enlargedPhoto = true; return true; }
        if (settings.gameMode == 3) {
            if (inside(x, y, 24, 429, 249, 448)) { focus = Focus.SAVE; return true; }
            if (inside(x, y, 24, 465, 249, 484)) { focus = Focus.SEED; return true; }
            if (inside(x, y, 132, 483, 248, 503)) { edit(() -> settings.difficulty = (settings.difficulty + 34) % 102); return true; }
            if (inside(x, y, 25, 517, 252, 533)) { edit(() -> settings.radiation = !settings.radiation); return true; }
            if (inside(x, y, 142, 540, 237, 560)) { edit(() -> settings.startingSupplies = (settings.startingSupplies + 1) % 3); return true; }
            if (inside(x, y, 142, 566, 237, 586)) { edit(() -> settings.startingBalance = (settings.startingBalance + 1) % 3); return true; }
            if (inside(x, y, 140, 593, 170, 625)) { edit(() -> settings.maxMissionsPerRound--); return true; }
            if (inside(x, y, 220, 593, 250, 625)) { edit(() -> settings.maxMissionsPerRound++); return true; }
        } else {
            if (inside(x, y, 21, 383, 133, 403)) { edit(() -> java.util.Arrays.fill(settings.missionEnabled, true)); return true; }
            if (inside(x, y, 138, 383, 257, 403)) { edit(() -> java.util.Arrays.fill(settings.missionEnabled, false)); return true; }
            for (int row = 0; row < 13 && missionScroll + row < settings.missionEnabled.length; row++) {
                if (inside(x, y, 21, 408 + row * 16, 252, 424 + row * 16)) {
                    int index = missionScroll + row;
                    edit(() -> settings.missionEnabled[index] = !settings.missionEnabled[index]); return true;
                }
            }
        }
        if (inside(x, y, 386, 380, 529, 400)) { edit(() -> settings.naturalZone = (settings.naturalZone + 1) % 5); return true; }
        if (inside(x, y, 279, 423, 529, 442)) { focus = Focus.SEED; return true; }
        if (inside(x, y, 279, 456, 429, 473)) { draggingSlider = "difficulty"; setSlider(x, 279, 150); return true; }
        if (inside(x, y, 405, 489, 438, 514)) { edit(() -> settings.botCount--); return true; }
        if (inside(x, y, 493, 489, 528, 514)) { edit(() -> settings.botCount++); return true; }
        if (inside(x, y, 430, 514, 529, 534)) { edit(() -> settings.botMode = (settings.botMode + 1) % 3); return true; }
        if (inside(x, y, 279, 576, 429, 590)) { draggingSlider = "betrayal"; setSlider(x, 279, 150); return true; }
        if (inside(x, y, 405, 586, 438, 611)) { edit(() -> settings.maxDanger--); return true; }
        if (inside(x, y, 493, 586, 528, 611)) { edit(() -> settings.maxDanger++); return true; }
        if (inside(x, y, 405, 610, 438, 634)) { edit(() -> settings.minimumPlayers--); return true; }
        if (inside(x, y, 493, 610, 528, 634)) { edit(() -> settings.minimumPlayers++); return true; }
        if (inside(x, y, 548, 356, 685, 376)) { respawnTab = true; return true; }
        if (inside(x, y, 690, 356, 815, 376)) { respawnTab = false; return true; }
        if (respawnTab) {
            if (inside(x, y, 673, 380, 803, 400)) { edit(() -> settings.respawnMode = (settings.respawnMode + 1) % 3); return true; }
            if (inside(x, y, 557, 408, 790, 430)) { edit(() -> settings.respawnShuttle = !settings.respawnShuttle); return true; }
            if (sliderClick(x, y, "respawn_interval", 557, 449, 140)) return true;
            if (sliderClick(x, y, "respawn_threshold", 557, 488, 140)) return true;
            if (sliderClick(x, y, "respawn_window", 557, 527, 140)) return true;
            if (sliderClick(x, y, "skill_death", 557, 566, 140)) return true;
            if (sliderClick(x, y, "skill_immediate", 557, 605, 140)) return true;
        } else {
            if (sliderClick(x, y, "replacement", 557, 426, 140)) return true;
            if (inside(x, y, 557, 456, 800, 478)) { edit(() -> settings.botControl = !settings.botControl); return true; }
            if (inside(x, y, 557, 483, 800, 505)) { edit(() -> settings.ironMan = !settings.ironMan); return true; }
        }
        if (inside(x, y, 843, 28, 980, 50)) { watch = !watch; return true; }
        if (inside(x, y, 1020, 28, 1150, 50)) { afk = !afk; return true; }
        if (inside(x, y, 842, 104, 1173, 354)) { professionPopup = true; return true; }
        for (int i = 0; i < 3; i++) if (inside(x, y, 842 + i * 110, 358, 952 + i * 110, 378)) { teamPreference = i; return true; }
        if (inside(x, y, 842, 594, 1173, 613)) { focus = Focus.CHAT; return true; }
        if (inside(x, y, 620, 640, 820, 667)) { edit(() -> settings.autoRestart = !settings.autoRestart); return true; }
        if (inside(x, y, 842, 640, 1015, 667)) { ready = !ready; return true; }
        if (inside(x, y, 1038, 641, 1185, 666)) {
            if (!ready) notifyUser("Сначала отметьте «Готовы начать»");
            else ModNetworking.CHANNEL.sendToServer(new PanelPackets.ServerboundStartSession());
            return true;
        }
        if (inside(x, y, 15, 641, 135, 666)) { onClose(); return true; }
        focus = Focus.NONE;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean sliderClick(double x, double y, String name, int left, int top, int width) {
        if (!inside(x, y, left, top - 4, left + width, top + 10)) return false;
        draggingSlider = name;
        setSlider(x, left, width);
        return true;
    }

    private void setSlider(double x, int left, int width) {
        int percentage = Mth.clamp((int) Math.round((x - left) * 100.0 / width), 0, 100);
        edit(() -> {
            switch (draggingSlider) {
                case "difficulty" -> settings.difficulty = percentage;
                case "betrayal" -> settings.betrayalChance = percentage;
                case "respawn_interval" -> settings.respawnInterval = 10 + percentage * 290 / 100;
                case "respawn_threshold" -> settings.respawnThreshold = percentage;
                case "respawn_window" -> settings.respawnWindow = 1 + percentage * 29 / 100;
                case "skill_death" -> settings.skillLossDeath = percentage;
                case "skill_immediate" -> settings.skillLossImmediate = percentage;
                case "replacement" -> settings.replacementCost = percentage;
            }
        });
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && !draggingSlider.isEmpty()) {
            double x = vx(mouseX);
            int left = draggingSlider.equals("difficulty") || draggingSlider.equals("betrayal") ? 279 : 557;
            setSlider(x, left, draggingSlider.equals("difficulty") || draggingSlider.equals("betrayal") ? 150 : 140);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = "";
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = vx(mouseX), y = vy(mouseY);
        int direction = delta > 0 ? -1 : 1;
        if (inside(x, y, 270, 110, 540, 350)) {
            submarineScroll = Mth.clamp(submarineScroll + direction, 0, Math.max(0, filteredSubmarines().size() - 8));
            return true;
        }
        if (inside(x, y, 15, 376, 265, 634) && settings.gameMode != 3) {
            missionScroll = Mth.clamp(missionScroll + direction, 0, Math.max(0, PanelSettings.MISSIONS.size() - 13));
            return true;
        }
        if (inside(x, y, 15, 110, 265, 350)) {
            modeScroll = Mth.clamp(modeScroll + direction, 0, Math.max(0, MODES.length - 4));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focus == Focus.NONE || Character.isISOControl(codePoint)) return super.charTyped(codePoint, modifiers);
        String value = focusedValue();
        int max = focus == Focus.CHAT ? 160 : focus == Focus.SEARCH ? 40 : 32;
        if (value.length() < max) setFocusedValue(value + codePoint);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focus != Focus.NONE) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String value = focusedValue();
                if (!value.isEmpty()) setFocusedValue(value.substring(0, value.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == Focus.CHAT && !chat.isBlank()) {
                    notifyUser("Сообщение отправлено локально: " + fit(chat, 180));
                    chat = "";
                } else if (focus == Focus.SEED || focus == Focus.SAVE) changed();
                focus = Focus.NONE;
                return true;
            }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                setFocusedValue((focusedValue() + clipboard).substring(0, Math.min(160, focusedValue().length() + clipboard.length())));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String focusedValue() {
        return switch (focus) {
            case SEARCH -> search;
            case SEED -> settings.levelSeed;
            case SAVE -> settings.saveName;
            case CHAT -> chat;
            default -> "";
        };
    }

    private void setFocusedValue(String value) {
        switch (focus) {
            case SEARCH -> { search = value; submarineScroll = 0; }
            case SEED -> { if (editable) settings.levelSeed = value; }
            case SAVE -> { if (editable) settings.saveName = value; }
            case CHAT -> chat = value;
        }
    }
}
