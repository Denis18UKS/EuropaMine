package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.PowerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Reference-style electrical panel driven by server-authoritative reactor output and panel demand. */
public final class ElectricalPanelRealScreen extends Screen {
    private static final int W = 760;
    private static final int H = 440;
    private final BlockPos pos;
    private final boolean technical;
    private final boolean traitor;
    private final int electronicsSkill;
    private CompoundTag state;
    private int refresh;

    private ElectricalPanelRealScreen(BlockPos pos, CompoundTag state, boolean technical, boolean traitor, int electronicsSkill) {
        super(Component.literal("Электрощиток"));
        this.pos = pos;
        this.state = state.copy();
        this.technical = technical;
        this.traitor = traitor;
        this.electronicsSkill = electronicsSkill;
    }

    public static void open(BlockPos pos, CompoundTag state, boolean technical, boolean traitor, int electronicsSkill) {
        Minecraft.getInstance().setScreen(new ElectricalPanelRealScreen(pos, state, technical, traitor, electronicsSkill));
    }

    public static void applyState(BlockPos pos, CompoundTag state) {
        if (Minecraft.getInstance().screen instanceof ElectricalPanelRealScreen screen && screen.pos.equals(pos)) {
            screen.state = state.copy();
        }
    }

    @Override public void tick() {
        super.tick();
        if (++refresh >= 10) {
            refresh = 0;
            ModNetworking.CHANNEL.sendToServer(new PowerPackets.ServerboundMachineAction(pos, "request", 0));
        }
    }

    private float scale() { return Math.min(width / (float) W, height / (float) H); }
    private float left() { return (width - W * scale()) * .5F; }
    private float top() { return (height - H * scale()) * .5F; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.pose().pushPose();
        g.pose().translate(left(), top(), 0);
        g.pose().scale(scale(), scale(), 1);
        panel(g);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void panel(GuiGraphics g) {
        double output = Math.max(0, state.getDouble("NetworkOutput"));
        double demand = Math.max(0, state.getDouble("Demand"));
        int health = Mth.clamp(state.getInt("Health"), 0, 100);
        int connections = state.getInt("Connections");
        boolean powered = state.getBoolean("Powered") && output > 0.01D;
        double ratio = demand <= 0.01D ? (output > 0 ? 1 : 0) : output / demand;
        boolean over = powered && ratio > 1.20D;
        boolean low = demand > 0.01D && output < demand * .90D;

        g.fill(8, 10, W - 8, H - 10, 0xFF07100D);
        g.fill(15, 17, W - 15, H - 17, 0xFF020504);
        g.fill(15, 17, W - 15, 21, 0xFF4D6D61);
        g.drawCenteredString(font, technical ? "ЭЛЕКТРОЩИТОК • СХЕМА" : "ЭЛЕКТРОЩИТОК", W / 2, 32, 0xFFF0DEA5);

        meter(g, 42, 70, 320, 104, "Энергия", (int)Math.round(output) + " кВт", powered ? 0xFF7BD8A0 : 0xFF9A4D48);
        meter(g, 42, 116, 320, 150, "Загрузка", (int)Math.round(demand) + " кВт", 0xFFD4B85E);

        indicator(g, 354, 71, "Есть питание", powered, 0xFF77D99A);
        indicator(g, 354, 109, "Перенапряжение", over, 0xFFFFB13B);
        indicator(g, 354, 147, "Низк. напряжение", low, 0xFFFF5548);

        g.fill(42, 188, W - 42, 323, 0xFF0B1311);
        g.renderOutline(42, 188, W - 84, 135, 0xFF334D44);
        g.drawString(font, "СЕТЬ", 58, 201, 0xFFE9DCA8, false);
        g.drawString(font, "Подключений: " + connections, 58, 224, 0xFFB7CBC3, false);
        g.drawString(font, "Исправность панели: " + health + "%", 58, 244, health < 50 ? 0xFFFF6459 : 0xFF91D6A7, false);
        g.drawString(font, "Баланс: " + (int)Math.round(output - demand) + " кВт", 58, 264, output >= demand ? 0xFF93E0B0 : 0xFFFF826E, false);
        g.drawString(font, "Реактор выключен → сеть получает 0 кВт.", 58, 291, 0xFF71877F, false);

        if (technical) {
            g.drawString(font, "Технический режим • навык Электроника: " + electronicsSkill, 390, 224, 0xFFB8D5E7, false);
            g.drawString(font, "Значения приходят с сервера и обновляются каждые 0.5 с.", 390, 247, 0xFF809A91, false);
        } else {
            g.drawString(font, "Для технической схемы используйте отвёртку.", 390, 224, 0xFF809A91, false);
            if (traitor) g.drawString(font, "Доступна функция саботажа в сервисном режиме.", 390, 247, 0xFFB85F55, false);
        }
        g.drawCenteredString(font, "ВЫХОД " + (int)Math.round(output) + " кВт   /   НАГРУЗКА " + (int)Math.round(demand) + " кВт", W / 2, 360, 0xFFE6D39C);
    }

    private void meter(GuiGraphics g, int x1, int y1, int x2, int y2, String label, String value, int accent) {
        g.fill(x1, y1, x2, y2, 0xFF111B18);
        g.fill(x1, y1, x1 + 4, y2, accent);
        g.drawString(font, label, x1 + 14, y1 + 7, 0xFF9DB2AA, false);
        g.drawString(font, value, x2 - 16 - font.width(value), y1 + 7, 0xFFF0E5B7, false);
    }

    private void indicator(GuiGraphics g, int x, int y, String label, boolean active, int color) {
        g.fill(x, y, x + 310, y + 28, 0xFF101815);
        g.fill(x + 8, y + 7, x + 22, y + 21, active ? color : 0xFF303833);
        g.drawString(font, label, x + 34, y + 10, active ? 0xFFE6F5ED : 0xFF78857F, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
