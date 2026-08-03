package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.PowerPackets;
import com.z_mods.barotrauma.power.PowerSystem;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Client-only screens for the GUI binder, reactor and electrical panel. */
public final class PowerScreens {
    private PowerScreens() {
    }

    public static void openBinder(String selected) {
        Minecraft.getInstance().setScreen(new GuiBinderScreen(selected));
    }

    public static void openMachine(BlockPos pos, String guiId, boolean technical, CompoundTag state,
                                   boolean traitor, int electronicsSkill) {
        Screen screen = PowerWorldData.REACTOR_GUI.equals(guiId)
                ? new ReactorScreen(pos, state)
                : new ElectricalPanelScreen(pos, state, technical, traitor, electronicsSkill);
        Minecraft.getInstance().setScreen(screen);
    }

    public static void applyState(BlockPos pos, CompoundTag state) {
        if (Minecraft.getInstance().screen instanceof MachineScreen machine && machine.pos.equals(pos)) {
            machine.applyState(state);
        }
    }

    private static final class GuiBinderScreen extends Screen {
        private EditBox search;
        private String selected;
        private int scroll;
        private List<PowerSystem.GuiEntry> visible = List.of();

        private GuiBinderScreen(String selected) {
            super(Component.literal("Привязка GUI"));
            this.selected = selected;
        }

        @Override
        protected void init() {
            int panelWidth = Math.min(620, width - 30);
            int left = (width - panelWidth) / 2;
            search = new EditBox(font, left + 18, 45, panelWidth - 36, 22, Component.literal("Поиск GUI"));
            search.setHint(Component.literal("Поиск по названию или идентификатору…"));
            search.setResponder(value -> {
                scroll = 0;
                rebuild();
            });
            addRenderableWidget(search);
            addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
                    .bounds(width / 2 - 55, height - 42, 110, 22).build());
            rebuild();
        }

        private void rebuild() {
            String needle = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
            List<PowerSystem.GuiEntry> filtered = new ArrayList<>();
            for (PowerSystem.GuiEntry entry : PowerSystem.GUI_CATALOG) {
                if (needle.isEmpty() || entry.id().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.title().toLowerCase(Locale.ROOT).contains(needle)) filtered.add(entry);
            }
            visible = List.copyOf(filtered);
            scroll = Mth.clamp(scroll, 0, Math.max(0, visible.size() - rowsPerPage()));
        }

        private int rowsPerPage() {
            return Math.max(1, (height - 150) / 29);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            int panelWidth = Math.min(620, width - 30);
            int left = (width - panelWidth) / 2;
            graphics.fill(left, 18, left + panelWidth, height - 55, 0xF20A1210);
            graphics.fill(left, 18, left + panelWidth, 20, 0xFF6EA996);
            graphics.drawCenteredString(font, "ИНСТРУМЕНТ ПРИВЯЗКИ GUI", width / 2, 27, 0xFFF2E5B3);
            graphics.drawString(font, "Выберите интерфейс, затем кликните инструментом по нужному блоку.",
                    left + 18, 72, 0xFF9EB3AC, false);

            int firstY = 92;
            int rows = rowsPerPage();
            for (int row = 0; row < rows; row++) {
                int index = scroll + row;
                if (index >= visible.size()) break;
                PowerSystem.GuiEntry entry = visible.get(index);
                int y = firstY + row * 29;
                boolean active = entry.id().equals(selected);
                boolean hovered = mouseX >= left + 18 && mouseX < left + panelWidth - 18 && mouseY >= y && mouseY < y + 24;
                graphics.fill(left + 18, y, left + panelWidth - 18, y + 24,
                        active ? 0xFF34594D : hovered ? 0xFF20372F : 0xFF111D19);
                graphics.fill(left + 18, y, left + 21, y + 24, active ? 0xFF7FD3B5 : 0xFF38564C);
                graphics.drawString(font, entry.title(), left + 30, y + 4, active ? 0xFFFFFFFF : 0xFFE2D9B3, false);
                graphics.drawString(font, entry.id(), left + 30, y + 14, 0xFF789188, false);
                if (active) graphics.drawString(font, "ВЫБРАНО", left + panelWidth - 82, y + 8, 0xFF8CF0C7, false);
            }
            if (visible.isEmpty()) {
                graphics.drawCenteredString(font, "Ничего не найдено", width / 2, firstY + 20, 0xFFB9AAA0);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            int panelWidth = Math.min(620, width - 30);
            int left = (width - panelWidth) / 2;
            int firstY = 92;
            if (mouseX >= left + 18 && mouseX < left + panelWidth - 18 && mouseY >= firstY) {
                int row = (int) ((mouseY - firstY) / 29.0D);
                int index = scroll + row;
                if (row >= 0 && row < rowsPerPage() && index >= 0 && index < visible.size()) {
                    selected = visible.get(index).id();
                    ModNetworking.CHANNEL.sendToServer(new PowerPackets.ServerboundSelectGui(selected));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, visible.size() - rowsPerPage()));
            return true;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private abstract static class MachineScreen extends Screen {
        protected final BlockPos pos;
        protected CompoundTag state;
        private int refreshTicks;

        protected MachineScreen(Component title, BlockPos pos, CompoundTag state) {
            super(title);
            this.pos = pos;
            this.state = state.copy();
        }

        protected void applyState(CompoundTag state) {
            this.state = state.copy();
        }

        @Override
        public void tick() {
            super.tick();
            if (++refreshTicks >= 10) {
                refreshTicks = 0;
                send("request", 0);
            }
        }

        protected void send(String action, int value) {
            ModNetworking.CHANNEL.sendToServer(new PowerPackets.ServerboundMachineAction(pos, action, value));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final class ReactorScreen extends MachineScreen {
        private static final int W = 1080;
        private static final int H = 512;
        private final Deque<Double> outputHistory = new ArrayDeque<>();
        private final Deque<Double> loadHistory = new ArrayDeque<>();
        private double displayedFission;
        private double displayedOutput;

        private ReactorScreen(BlockPos pos, CompoundTag state) {
            super(Component.literal("Ядерный реактор"), pos, state);
        }

        @Override
        protected void applyState(CompoundTag state) {
            super.applyState(state);
            outputHistory.addLast(state.getDouble("Output"));
            loadHistory.addLast(state.getDouble("Load"));
            while (outputHistory.size() > 80) outputHistory.removeFirst();
            while (loadHistory.size() > 80) loadHistory.removeFirst();
        }

        private float scale() {
            return Math.min(width / (float) W, height / (float) H);
        }

        private float left() {
            return (width - W * scale()) * 0.5F;
        }

        private float top() {
            return (height - H * scale()) * 0.5F;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            displayedFission += (state.getDouble("Fission") - displayedFission) * 0.18D;
            displayedOutput += (state.getDouble("Output") - displayedOutput) * 0.18D;
            graphics.pose().pushPose();
            graphics.pose().translate(left(), top(), 0);
            graphics.pose().scale(scale(), scale(), 1.0F);
            renderPanel(graphics);
            graphics.pose().popPose();
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void renderPanel(GuiGraphics g) {
            long time = System.currentTimeMillis();
            boolean blink = (time / 350L) % 2L == 0L;
            boolean enabled = state.getBoolean("Enabled");
            boolean automatic = state.getBoolean("Automatic");
            double temperature = state.getDouble("Temperature");
            double output = state.getDouble("Output");
            double load = state.getDouble("Load");
            int target = state.getInt("Target");
            int overfuel = state.getInt("Overfuel");
            int meltdown = state.getInt("Meltdown");

            g.fill(0, 0, W, H, 0xFF020606);
            g.fill(8, 8, W - 8, H - 8, 0xFF0B1713);
            g.fill(13, 13, W - 13, H - 13, 0xFF010403);
            g.fill(118, 20, W - 18, 478, 0xEE020403);
            g.fill(118, 20, W - 18, 23, 0xFF365249);
            g.fill(118, 475, W - 18, 478, 0xFF203B32);

            renderFuelRail(g, blink, overfuel > 0);
            renderTopIndicators(g, blink, temperature, output, enabled, automatic);
            renderGauge(g, 310, 303, 132, displayedFission, "СКОРОСТЬ РАСЩЕПЛЕНИЯ");
            renderGauge(g, 535, 303, 132, displayedOutput / 30.0D, "ВЫРАБОТКА ЭЛЕКТРОЭНЕРГИИ");
            renderSlider(g, target, enabled, blink);
            renderGraph(g, output, load);
            renderAlarmButtons(g, blink, enabled, automatic, temperature, output, load, overfuel, meltdown);

            g.drawString(font, "АВТОМ.", 765, 65, automatic ? 0xFFFFE49B : 0xFFB4AB91, false);
            g.drawString(font, "УПРАВЛ-Е", 765, 80, automatic ? 0xFFFFE49B : 0xFFB4AB91, false);
            g.fill(690, 50, 716, 112, 0xFF17201E);
            int knobY = automatic ? 52 : 88;
            g.fill(692, knobY, 714, knobY + 22, automatic ? 0xFFF3E7C7 : 0xFF8F9894);
            g.fill(728, 60, 752, 84, automatic ? 0xFFFFB300 : 0xFF3B3B35);

            g.fill(955, 37, 1034, 119, 0xFF07100D);
            g.fill(973, 57, 1017, 103, enabled ? 0xFFFF2E21 : 0xFF641A18);
            g.drawString(font, "ЭНЕРГИЯ", 975, 42, 0xFFE9DFAE, false);

            int loadText = (int) Math.round(load);
            int outputText = (int) Math.round(output);
            g.drawString(font, "ЗАГРУЗКА: " + loadText + " КВТ", 730, 139, 0xFFB9D5EA, false);
            g.drawString(font, "ВЫХОД: " + outputText + " КВТ", 730, 449,
                    outputText > 0 ? 0xFFFFC400 : 0xFFE0A500, false);
            g.drawString(font, "+", 684, 131, 0xFF1C2C28, false);
            g.drawString(font, "−", 684, 447, 0xFF1C2C28, false);
        }

        private void renderFuelRail(GuiGraphics g, boolean blink, boolean danger) {
            g.fill(18, 18, 104, 420, 0xFF0A1713);
            g.fill(26, 55, 96, 405, 0xFF020605);
            g.drawString(font, "ТОПЛИВО", 34, 35, 0xFFE6D9A4, false);
            for (int i = 0; i < 4; i++) {
                int y = 72 + i * 79;
                int fuel = state.getInt("Fuel" + i);
                boolean occupied = fuel > 0;
                g.fill(37, y, 86, y + 55, danger && blink ? 0xFF6A1611 : 0xFF16241F);
                g.fill(42, y + 5, 81, y + 50, occupied ? 0xFF315E4E : 0xFF0A0F0D);
                if (occupied) {
                    int h = Mth.clamp((int) Math.round(40.0D * fuel / 24000.0D), 1, 40);
                    g.fill(49, y + 46 - h, 74, y + 46, 0xFF8DBE57);
                    g.drawCenteredString(font, (fuel * 100 / 24000) + "%", 61, y + 22, 0xFFFFFFFF);
                } else {
                    g.drawCenteredString(font, "ПУСТО", 61, y + 24, 0xFF64716C);
                }
            }
        }

        private void renderTopIndicators(GuiGraphics g, boolean blink, double temperature, double output,
                                         boolean enabled, boolean automatic) {
            String[] labels = {"КРИТИЧЕСКИЙ\nПЕРЕГРЕВ", "КРИТИЧЕСКИЙ\nУРОВЕНЬ ВЫРАБОТКИ", "КРИТИЧЕСКОЕ\nПЕРЕОХЛАЖДЕНИЕ"};
            boolean[] active = {temperature > 100, output > 2700, enabled && !automatic};
            for (int i = 0; i < 3; i++) {
                int x = 154 + i * 155;
                int color = active[i] && blink ? 0xFFFF382C : 0xFF3B413D;
                g.fill(x, 61, x + 34, 95, 0xFF161C19);
                g.fill(x + 7, 68, x + 27, 88, color);
                String[] parts = labels[i].split("\\n");
                g.drawString(font, parts[0], x + 45, 68, 0xFFDCCF9C, false);
                g.drawString(font, parts[1], x + 45, 79, 0xFFDCCF9C, false);
            }
        }

        private void renderGauge(GuiGraphics g, int cx, int cy, int radius, double value, String label) {
            g.drawCenteredString(font, label, cx, 145, 0xFFE7D79F);
            g.fill(cx - radius - 8, cy - 112, cx + radius + 8, cy + 18, 0xFF221F1B);
            g.fill(cx - radius, cy - 104, cx + radius, cy + 10, 0xFF080A09);
            for (int i = 0; i <= 100; i += 2) {
                double angle = Math.PI + Math.PI * i / 100.0D;
                int inner = radius - (i % 10 == 0 ? 25 : 15);
                int color = i < 35 ? 0xFFCF3022 : i < 65 ? 0xFF5EB96E : i < 82 ? 0xFFE0A321 : 0xFFCF3022;
                line(g, cx + Math.cos(angle) * inner, cy + Math.sin(angle) * inner,
                        cx + Math.cos(angle) * (radius - 4), cy + Math.sin(angle) * (radius - 4), color, 2);
                if (i % 10 == 0) {
                    int tx = (int) Math.round(cx + Math.cos(angle) * (radius - 42));
                    int ty = (int) Math.round(cy + Math.sin(angle) * (radius - 42));
                    g.drawCenteredString(font, Integer.toString(i), tx, ty - 4, 0xFFE8E0CD);
                }
            }
            double clamped = Mth.clamp(value, 0.0D, 100.0D);
            double needleAngle = Math.PI + Math.PI * clamped / 100.0D;
            line(g, cx, cy, cx + Math.cos(needleAngle) * (radius - 24), cy + Math.sin(needleAngle) * (radius - 24),
                    0xFFF06A61, 4);
            g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFFE1D1B5);
        }

        private void renderSlider(GuiGraphics g, int target, boolean enabled, boolean blink) {
            g.fill(675, 150, 720, 438, 0xFF35403D);
            g.fill(682, 158, 713, 430, 0xFF080B0A);
            int lowerArrowY = 355;
            if (!enabled && blink) g.fill(683, lowerArrowY, 712, 429, 0xFFB40000);
            int markerY = 422 - (int) Math.round(target * 2.55D);
            g.fill(684, markerY - 5, 711, markerY + 5, enabled ? 0xFF3347DB : 0xFF7F1614);
            g.fill(661, markerY - 4, 674, markerY + 4, 0xFFE1372B);
            g.fill(721, markerY - 4, 734, markerY + 4, 0xFFE1372B);
            g.fill(670, 126, 708, 164, 0xFFBFD1CB);
            g.fill(670, 438, 708, 476, 0xFFBFD1CB);
            g.drawCenteredString(font, "+", 689, 137, 0xFF17201D);
            g.drawCenteredString(font, "−", 689, 449, 0xFF17201D);
        }

        private void renderGraph(GuiGraphics g, double output, double load) {
            int x1 = 742;
            int y1 = 175;
            int x2 = 1048;
            int y2 = 421;
            g.fill(x1, y1, x2, y2, 0xFF2A090A);
            for (int i = 1; i < 4; i++) g.fill(x1, y1 + i * 61, x2, y1 + i * 61 + 1, 0xFF4C1A1C);
            drawHistory(g, outputHistory, x1 + 10, y1 + 10, x2 - 10, y2 - 10, 3000.0D, 0xFFBDD5EA);
            drawHistory(g, loadHistory, x1 + 10, y1 + 10, x2 - 10, y2 - 10, 3000.0D, 0xFFE0B800);
        }

        private void drawHistory(GuiGraphics g, Deque<Double> history, int x1, int y1, int x2, int y2,
                                 double maximum, int color) {
            if (history.size() < 2) return;
            List<Double> values = new ArrayList<>(history);
            for (int i = 1; i < values.size(); i++) {
                double px1 = Mth.lerp((i - 1) / (double) (values.size() - 1), x1, x2);
                double px2 = Mth.lerp(i / (double) (values.size() - 1), x1, x2);
                double py1 = y2 - Mth.clamp(values.get(i - 1) / maximum, 0, 1) * (y2 - y1);
                double py2 = y2 - Mth.clamp(values.get(i) / maximum, 0, 1) * (y2 - y1);
                line(g, px1, py1, px2, py2, color, 2);
            }
        }

        private void renderAlarmButtons(GuiGraphics g, boolean blink, boolean enabled, boolean automatic,
                                        double temperature, double output, double load, int overfuel, int meltdown) {
            String[] labels = {"НИЗКАЯ ТЕМПЕРАТУРА", "НИЗКАЯ ВЫРАБОТКА", "НЕХВАТКА ТОПЛИВА", "ПЛАВЛЕНИЕ",
                    "ПЕРЕГРЕВ", "ВЫСОКАЯ ВЫРАБОТКА", "ТОПЛИВО ЗАКОНЧИЛОСЬ", "АВАРИЙНАЯ ОСТАНОВКА"};
            boolean[] active = {temperature < 5, enabled && output < 200, enabled && !hasAnyFuel(), meltdown > 0,
                    temperature > 85, output > 2500, !hasAnyFuel(), overfuel > 0 || meltdown > 130};
            for (int i = 0; i < labels.length; i++) {
                int col = i % 4;
                int row = i / 4;
                int x = 145 + col * 126;
                int y = 380 + row * 43;
                int color = active[i] && blink ? 0xFFB94731 : enabled && !automatic && blink ? 0xFF6A5E3A : 0xFF403B31;
                g.fill(x, y, x + 116, y + 34, 0xFF181A17);
                g.fill(x + 4, y + 4, x + 112, y + 30, color);
                g.drawCenteredString(font, labels[i], x + 58, y + 13, 0xFF1A1B17);
            }
        }

        private boolean hasAnyFuel() {
            for (int i = 0; i < 4; i++) if (state.getInt("Fuel" + i) > 0) return true;
            return false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            double x = (mouseX - left()) / scale();
            double y = (mouseY - top()) / scale();
            for (int i = 0; i < 4; i++) {
                int slotY = 72 + i * 79;
                if (x >= 37 && x < 86 && y >= slotY && y < slotY + 55) {
                    int fuel = state.getInt("Fuel" + i);
                    if (fuel > 0) send(button == 1 ? "fuel_overload" : "fuel_extract", i);
                    else send("fuel_insert", i);
                    return true;
                }
            }
            if (x >= 680 && x <= 760 && y >= 45 && y <= 120) {
                send("auto", 0);
                return true;
            }
            if (x >= 950 && x <= 1038 && y >= 32 && y <= 124) {
                send("toggle", 0);
                return true;
            }
            if (x >= 665 && x <= 720 && y >= 125 && y <= 170) {
                send("plus", 0);
                return true;
            }
            if (x >= 665 && x <= 720 && y >= 434 && y <= 480) {
                send("minus", 0);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static final class ElectricalPanelScreen extends MachineScreen {
        private static final int W = 720;
        private static final int H = 430;
        private final boolean technical;
        private final boolean traitor;
        private final int electronicsSkill;

        private ElectricalPanelScreen(BlockPos pos, CompoundTag state, boolean technical, boolean traitor,
                                      int electronicsSkill) {
            super(Component.literal("Электрощиток"), pos, state);
            this.technical = technical;
            this.traitor = traitor;
            this.electronicsSkill = electronicsSkill;
        }

        private float scale() {
            return Math.min(width / (float) W, height / (float) H);
        }

        private float left() {
            return (width - W * scale()) * 0.5F;
        }

        private float top() {
            return (height - H * scale()) * 0.5F;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.pose().pushPose();
            graphics.pose().translate(left(), top(), 0);
            graphics.pose().scale(scale(), scale(), 1);
            gPanel(graphics);
            graphics.pose().popPose();
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void gPanel(GuiGraphics g) {
            g.fill(10, 15, W - 10, H - 15, 0xFF07110E);
            g.fill(15, 20, W - 15, H - 20, 0xEE020504);
            g.fill(15, 20, W - 15, 23, 0xFF49685D);
            g.drawString(font, "⚙", 28, 31, 0xFFADC3BA, false);
            if (technical) renderTechnical(g); else renderMaintenance(g);
        }

        private void renderTechnical(GuiGraphics g) {
            g.drawCenteredString(font, "ЭЛЕКТРОЩИТОК: СХЕМА ПОДКЛЮЧЕНИЙ", W / 2, 35, 0xFFF0DEA5);
            g.fill(45, 70, 675, 365, 0xFF020403);
            String[] names = {"ЭНЕРГИЯ", "СИГНАЛ_0", "СИГНАЛ_1", "СИГНАЛ_2", "СИГНАЛ_3",
                    "ИСПРАВНОСТЬ_ИЗ", "ЗНАЧЕНИЕ_ЭНЕРГИИ_ИЗ", "ЗНАЧЕНИЕ_ЗАГРУЗКИ_ИЗ"};
            int connections = state.getInt("Connections");
            int health = state.getInt("Health");
            int demand = (int) Math.round(1494.0D * (0.35D + 0.65D * health / 100.0D));
            for (int i = 0; i < names.length; i++) {
                int y = 82 + i * 34;
                int labelColor = i == 0 ? 0xFFFFCDD0 : 0xFFC8E5F5;
                g.fill(255 - font.width(names[i]), y, 266, y + 18, i == 0 ? 0xFF9C3947 : 0xFF315D78);
                g.drawString(font, names[i], 260 - font.width(names[i]), y + 5, labelColor, false);
                g.fill(280, y - 1, 305, y + 24, 0xFF6A6557);
                g.fill(287, y + 6, 298, y + 17, i < connections ? 0xFFFFB329 : 0xFF101412);
                int lineColor = i == 0 ? 0xFFE53A30 : i < 5 ? 0xFF2C7FD5 : 0xFFAA332B;
                line(g, 305, y + 11, 580, 90 + i * 18, lineColor, 4);
            }
            g.drawString(font, "Подключений: " + connections, 475, 325, 0xFFD9CE9F, false);
            g.drawString(font, "Исправность: " + health + "%", 475, 340, health < 55 ? 0xFFFF6253 : 0xFF91E3A9, false);
            g.drawString(font, "Загрузка: " + demand + " кВт", 475, 355, 0xFFB9D9F1, false);
            g.drawString(font, "Технический режим открыт отвёрткой / в творческом режиме", 45, 385, 0xFF839990, false);
        }

        private void renderMaintenance(GuiGraphics g) {
            int health = state.getInt("Health");
            g.drawCenteredString(font, "Электроника", W / 2, 58, 0xFFF4E8B9);
            g.drawString(font, "НЕОБХОДИМЫЕ НАВЫКИ:", 92, 90, 0xFFF4E8B9, false);
            g.drawString(font, "- Электрика: 55", 110, 110,
                    electronicsSkill >= 55 ? 0xFF8CD79C : 0xFFFF6761, false);
            g.drawString(font, "Ваш навык: " + electronicsSkill, 110, 126, 0xFFC9D9D3, false);

            g.fill(92, 165, 455, 207, 0xFF18211E);
            g.fill(100, 173, 447, 199, 0xFF050806);
            int barWidth = (int) Math.round(343.0D * health / 100.0D);
            g.fill(102, 175, 102 + barWidth, 197, health < 35 ? 0xFFB83B32 : health < 70 ? 0xFFC1B95D : 0xFF87B68C);
            g.drawCenteredString(font, "ИСПРАВНОСТЬ: " + health + "%", 274, 181, 0xFF17201D);

            g.fill(475, 165, 625, 207, health < 100 ? 0xFF8AA19A : 0xFF3F4D48);
            g.drawCenteredString(font, "РЕМОНТ", 550, 181, 0xFF23312D);
            g.fill(92, 223, 625, 255, 0xFF30423B);
            g.drawCenteredString(font, health < 100 ? "ПОВРЕЖДЕНИЕ ОБНАРУЖЕНО" : "САБОТАЖ НЕ ОБНАРУЖЕН",
                    358, 234, health < 100 ? 0xFFFFD6A0 : 0xFFC4D8CF);

            g.drawString(font, "Значение энергии: " + (health > 0 ? "есть" : "нет"), 92, 285, 0xFFBBD7E8, false);
            g.drawString(font, "Расчётная загрузка: " + (int) Math.round(1494.0D * (0.35D + 0.65D * health / 100.0D)) + " кВт",
                    92, 304, 0xFFBBD7E8, false);
            g.drawString(font, "Подключений: " + state.getInt("Connections"), 92, 323, 0xFFBBD7E8, false);

            if (traitor) {
                g.fill(475, 285, 625, 327, 0xFF72211D);
                g.drawCenteredString(font, "САБОТАЖ", 550, 301, 0xFFFFD1C7);
            }
            g.drawString(font, "Нажмите назначенный кейбинд, глядя на щиток. С отвёрткой откроется схема.",
                    92, 370, 0xFF7F958C, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            double x = (mouseX - left()) / scale();
            double y = (mouseY - top()) / scale();
            if (!technical && x >= 475 && x <= 625 && y >= 165 && y <= 207) {
                send("repair", 0);
                return true;
            }
            if (!technical && traitor && x >= 475 && x <= 625 && y >= 285 && y <= 327) {
                send("sabotage", 0);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thickness) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        int half = Math.max(0, thickness / 2);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x1 + dx * t);
            int y = (int) Math.round(y1 + dy * t);
            g.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }
}
