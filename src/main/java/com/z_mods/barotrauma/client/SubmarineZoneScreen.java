package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.SubmarineZonePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Dark industrial library/search screen for persistent submarine construction zones. */
public final class SubmarineZoneScreen extends Screen {
    private final List<SubmarineZonePackets.ZoneView> all;
    private List<SubmarineZonePackets.ZoneView> visible = List.of();
    private EditBox search;
    private UUID selected;
    private int scroll;

    private SubmarineZoneScreen(List<SubmarineZonePackets.ZoneView> zones) {
        super(Component.literal("Зоны строительства подлодки"));
        this.all = List.copyOf(zones);
    }

    public static void open(List<SubmarineZonePackets.ZoneView> zones) {
        Minecraft.getInstance().setScreen(new SubmarineZoneScreen(zones));
    }

    @Override
    protected void init() {
        int left = panelLeft();
        search = new EditBox(font, left + 20, 51, panelWidth() - 40, 22, Component.literal("Поиск"));
        search.setHint(Component.literal("Поиск по названию или типу зоны…"));
        search.setResponder(value -> { scroll = 0; rebuild(); });
        addRenderableWidget(search);

        int buttonY = height - 58;
        addRenderableWidget(Button.builder(Component.literal("Телепорт"), b -> teleport())
                .bounds(left + 20, buttonY, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> deleteSelected())
                .bounds(left + 118, buttonY, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Внешняя"), b -> give("external_wall"))
                .bounds(left + 215, buttonY, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Внутренняя"), b -> give("internal_wall"))
                .bounds(left + 313, buttonY, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Пол"), b -> give("floor"))
                .bounds(left + 417, buttonY, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(left + panelWidth() - 96, buttonY, 76, 20).build());
        rebuild();
    }

    private int panelWidth() { return Math.min(680, width - 24); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int rows() { return Math.max(1, (height - 165) / 34); }

    private void rebuild() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<SubmarineZonePackets.ZoneView> filtered = new ArrayList<>();
        for (SubmarineZonePackets.ZoneView zone : all) {
            if (q.isEmpty() || zone.name().toLowerCase(Locale.ROOT).contains(q)
                    || zone.kindTitle().toLowerCase(Locale.ROOT).contains(q)
                    || zone.kindId().toLowerCase(Locale.ROOT).contains(q)) filtered.add(zone);
        }
        visible = List.copyOf(filtered);
        scroll = Mth.clamp(scroll, 0, Math.max(0, visible.size() - rows()));
        if (selected != null && visible.stream().noneMatch(z -> z.id().equals(selected))) selected = null;
    }

    private SubmarineZonePackets.ZoneView selectedZone() {
        if (selected == null) return null;
        for (SubmarineZonePackets.ZoneView z : visible) if (z.id().equals(selected)) return z;
        return null;
    }

    private void teleport() {
        SubmarineZonePackets.ZoneView zone = selectedZone();
        if (zone != null) ModNetworking.CHANNEL.sendToServer(new SubmarineZonePackets.ServerboundTeleportZone(zone.id()));
    }
    private void deleteSelected() {
        SubmarineZonePackets.ZoneView zone = selectedZone();
        if (zone != null) ModNetworking.CHANNEL.sendToServer(new SubmarineZonePackets.ServerboundDeleteZone(zone.id()));
    }
    private void give(String kind) {
        ModNetworking.CHANNEL.sendToServer(new SubmarineZonePackets.ServerboundGiveZoneTool(kind));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = panelLeft(), right = left + panelWidth();
        g.fill(left, 18, right, height - 72, 0xF5081110);
        g.renderOutline(left, 18, panelWidth(), height - 90, 0xFF496A5E);
        g.fill(left, 18, right, 21, 0xFF6EA996);
        g.drawCenteredString(font, "БИБЛИОТЕКА 3D-ЗОН ПОДЛОДКИ", width / 2, 28, 0xFFF3E3AD);
        g.drawString(font, "Две точки формируют объём. Кнопки + выдают отдельный инструмент нужного типа.",
                left + 20, 80, 0xFF8FA69E, false);

        int firstY = 101;
        for (int row = 0; row < rows(); row++) {
            int index = scroll + row;
            if (index >= visible.size()) break;
            SubmarineZonePackets.ZoneView zone = visible.get(index);
            int y = firstY + row * 34;
            boolean active = zone.id().equals(selected);
            boolean hover = mouseX >= left + 20 && mouseX <= right - 20 && mouseY >= y && mouseY < y + 29;
            g.fill(left + 20, y, right - 20, y + 29, active ? 0xFF244D40 : hover ? 0xFF182F29 : 0xFF101B18);
            g.fill(left + 20, y, left + 24, y + 29, active ? 0xFF87DDBA : 0xFF3C6657);
            g.drawString(font, zone.name(), left + 31, y + 5, active ? 0xFFFFFFFF : 0xFFE8DCB4, false);
            String bounds = zone.kindTitle() + "  •  " + zone.min().toShortString() + " → " + zone.max().toShortString();
            g.drawString(font, bounds, left + 31, y + 17, 0xFF819991, false);
        }
        if (visible.isEmpty()) g.drawCenteredString(font, "Зон пока нет", width / 2, firstY + 25, 0xFF9AADA6);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = panelLeft(), firstY = 101;
        if (mouseX >= left + 20 && mouseX <= left + panelWidth() - 20 && mouseY >= firstY) {
            int row = (int)((mouseY - firstY) / 34.0D);
            int index = scroll + row;
            if (row >= 0 && row < rows() && index >= 0 && index < visible.size()) {
                selected = visible.get(index).id();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int)Math.signum(delta), 0, Math.max(0, visible.size() - rows()));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
