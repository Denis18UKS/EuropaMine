package com.z_mods.barotrauma.client;

import com.mojang.authlib.GameProfile;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.NavigationPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Barotrauma navigation terminal recreated from the supplied reference states.
 * All mutable controls are rendered and handled separately from the immutable panel skin.
 */
public final class NavigationTerminalScreen extends Screen {
    private static final ResourceLocation FRAME = new ResourceLocation("barotrauma", "textures/gui/navigation_terminal_frame.png");
    private static final int W = 1640;
    private static final int H = 774;

    private static final int SONAR_CX = 912;
    private static final int SONAR_CY = 352;
    private static final int SONAR_RADIUS = 289;

    private BlockPos terminalPos;
    private CompoundTag state;
    private int requestTicks;
    private float pulse;
    private float displayedForward;
    private float displayedVertical;
    private boolean draggingZoom;
    private boolean draggingBeam;

    private NavigationTerminalScreen(BlockPos terminalPos, CompoundTag state) {
        super(Component.literal("Навигационный терминал"));
        this.terminalPos = terminalPos;
        this.state = state.copy();
        this.displayedForward = (float) state.getDouble("ForwardSpeedKmh");
        this.displayedVertical = (float) state.getDouble("VerticalSpeedKmh");
    }

    public static void open(BlockPos terminalPos, CompoundTag state) {
        Minecraft.getInstance().setScreen(new NavigationTerminalScreen(terminalPos, state));
    }

    public static void applyState(BlockPos terminalPos, CompoundTag state) {
        if (Minecraft.getInstance().screen instanceof NavigationTerminalScreen screen) {
            screen.terminalPos = terminalPos;
            screen.state = state.copy();
        }
    }

    private float scale() {
        return Math.min(1.0F, Math.min(width / (float) W, height / (float) H));
    }

    private float left() {
        return (width - W * scale()) * 0.5F;
    }

    private float top() {
        return (height - H * scale()) * 0.5F;
    }

    @Override
    public void tick() {
        super.tick();
        pulse += state.getBoolean("ActiveSonar") ? 0.014F : 0.004F;
        if (pulse > 1.0F) pulse -= 1.0F;
        displayedForward += ((float) state.getDouble("ForwardSpeedKmh") - displayedForward) * 0.18F;
        displayedVertical += ((float) state.getDouble("VerticalSpeedKmh") - displayedVertical) * 0.18F;
        if (++requestTicks >= 8) {
            requestTicks = 0;
            send("request", 0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(left(), top(), 0.0F);
        graphics.pose().scale(scale(), scale(), 1.0F);
        graphics.blit(FRAME, 0, 0, 0, 0, W, H, W, H);

        renderStatusMonitor(graphics);
        renderSonar(graphics, mouseX, mouseY);
        renderSonarControls(graphics);
        renderSteeringControls(graphics);
        renderReadouts(graphics);
        renderPowerWarning(graphics);

        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatusMonitor(GuiGraphics g) {
        int x1 = 66;
        int y1 = 147;
        int x2 = 530;
        int y2 = 328;
        g.fill(x1, y1, x2, y2, 0xD9000403);

        byte[] cells = state.getByteArray("HullGrid");
        int columns = Math.max(1, state.getInt("HullColumns"));
        int rows = Math.max(1, state.getInt("HullRows"));
        if (columns <= 1 || rows <= 1 || cells.length < columns * rows) {
            columns = 32;
            rows = 12;
        }
        int cellW = Math.max(2, (x2 - x1 - 34) / columns);
        int cellH = Math.max(2, (y2 - y1 - 42) / rows);
        int originX = x1 + 17;
        int originY = y1 + 22;
        boolean hasHull = false;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= cells.length || cells[index] == 0) continue;
                hasHull = true;
                int px = originX + column * cellW;
                int py = originY + row * cellH;
                int color = cells[index] == 2 ? 0xD9E57B18 : 0xD900B58B;
                g.fill(px, py, px + Math.max(1, cellW - 1), py + Math.max(1, cellH - 1), color);
            }
        }
        if (!hasHull) {
            drawSubmarineOutline(g, 300, 235, 175, 0x8A2FC9A0);
        }

        ListTag crew = state.getList("Crew", Tag.TAG_COMPOUND);
        for (int i = 0; i < crew.size(); i++) {
            CompoundTag row = crew.getCompound(i);
            int px = Mth.floor(Mth.lerp(row.getFloat("X"), x1 + 15, x2 - 31));
            int py = Mth.floor(Mth.lerp(row.getFloat("Y"), y1 + 16, y2 - 32));
            ResourceLocation skin = skin(row);
            if (skin != null) {
                PlayerFaceRenderer.draw(g, skin, px, py, 16, true, false);
            } else {
                fillCircle(g, px + 8, py + 8, 8, 0xFF9FBAB1);
                String name = row.getString("Name");
                if (!name.isBlank()) g.drawCenteredString(font, name.substring(0, 1).toUpperCase(Locale.ROOT), px + 8, py + 4, 0xFF15201C);
            }
        }

        if (state.getBoolean("Docked")) {
            g.drawCenteredString(font, "ПОДКЛЮЧЕНО К СТАНЦИИ", (x1 + x2) / 2, 305, 0xFF9ACBC0);
        }
    }

    private ResourceLocation skin(CompoundTag row) {
        try {
            UUID uuid = row.hasUUID("Uuid") ? row.getUUID("Uuid") : UUID.nameUUIDFromBytes(row.getString("Name").getBytes());
            String name = row.getString("Name");
            return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(new GameProfile(uuid, name));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderSonar(GuiGraphics g, int mouseX, int mouseY) {
        boolean powered = state.getBoolean("Powered");
        boolean active = state.getBoolean("ActiveSonar");
        boolean directional = state.getBoolean("Directional");

        fillCircle(g, SONAR_CX, SONAR_CY, SONAR_RADIUS - 7, 0x71010000);
        for (int radius = 72; radius < SONAR_RADIUS; radius += 72) {
            circle(g, SONAR_CX, SONAR_CY, radius, 0x183F554C, 1);
        }
        line(g, SONAR_CX - SONAR_RADIUS + 10, SONAR_CY, SONAR_CX + SONAR_RADIUS - 10, SONAR_CY, 0x183F554C, 1);
        line(g, SONAR_CX, SONAR_CY - SONAR_RADIUS + 10, SONAR_CX, SONAR_CY + SONAR_RADIUS - 10, 0x183F554C, 1);

        if (powered && active) {
            float radius = 15.0F + pulse * (SONAR_RADIUS - 23.0F);
            int alpha = Mth.clamp((int) ((1.0F - pulse) * 155.0F), 15, 155);
            circle(g, SONAR_CX, SONAR_CY, Math.round(radius), alpha << 24 | 0x00DAECFF, 3);
        }

        float beamAngle = state.getFloat("BeamAngle");
        if (powered && active && directional) {
            drawWedge(g, beamAngle, 0.24F, 0x3FE6E4C9);
        }

        int[] sonar = state.getIntArray("Sonar");
        if (powered && active && sonar.length > 0) {
            for (int i = 0; i < sonar.length; i++) {
                if (sonar[i] <= 0) continue;
                double angle = Math.PI * 2.0D * i / sonar.length;
                if (directional && angularDifference((float) angle, beamAngle) > 0.28F) continue;
                double radius = sonar[i] / 100.0D * (SONAR_RADIUS - 19);
                int x = SONAR_CX + (int) Math.round(Math.cos(angle) * radius);
                int y = SONAR_CY + (int) Math.round(Math.sin(angle) * radius);
                int seed = i * 7349 + (int) (pulse * 1000);
                for (int p = 0; p < 5; p++) {
                    int ox = ((seed >> (p + 1)) & 7) - 3;
                    int oy = ((seed >> (p + 4)) & 7) - 3;
                    fillCircle(g, x + ox, y + oy, 2, 0xB9268DFF);
                }
            }
        }

        ListTag contacts = state.getList("Contacts", Tag.TAG_COMPOUND);
        for (int i = 0; i < contacts.size(); i++) {
            CompoundTag contact = contacts.getCompound(i);
            int x = SONAR_CX + Math.round(contact.getFloat("X") * (SONAR_RADIUS - 25));
            int y = SONAR_CY + Math.round(contact.getFloat("Y") * (SONAR_RADIUS - 25));
            int alpha = Mth.clamp((int) (contact.getFloat("Strength") * 220), 50, 220);
            fillCircle(g, x, y, active ? 3 : 2, alpha << 24 | (active ? 0x002DA9FF : 0x007F9D8A));
        }

        // Deterministic interference in flooded or unpowered states, matching the orange disruption layer.
        byte[] hull = state.getByteArray("HullGrid");
        int flooded = 0;
        for (byte value : hull) if (value == 2) flooded++;
        if (flooded > 0 || (!powered && active)) {
            int amount = Math.min(75, 12 + flooded * 2);
            for (int i = 0; i < amount; i++) {
                double angle = i * 2.399963229728653D + pulse * 5.0D;
                double radius = 65.0D + ((i * 37) % 185);
                int x = SONAR_CX + (int) (Math.cos(angle) * radius);
                int y = SONAR_CY + (int) (Math.sin(angle) * radius);
                fillCircle(g, x, y, 2 + i % 3, 0x90F08A22);
            }
        }

        drawSubmarineOutline(g, SONAR_CX, SONAR_CY, 66, 0xB66D8A8A);
        g.fill(SONAR_CX - 14, SONAR_CY - 5, SONAR_CX + 14, SONAR_CY + 5, 0x720B171B);
        fillCircle(g, SONAR_CX - 37, SONAR_CY, 4, 0xFF178FE3);
        fillCircle(g, SONAR_CX - 27, SONAR_CY - 3, 3, 0xC91F62C6);
        g.fill(SONAR_CX + 10, SONAR_CY - 9, SONAR_CX + 20, SONAR_CY - 6, 0xFF28B05C);

        renderTargets(g);

        if (!state.getBoolean("Autopilot")) {
            float forward = state.getFloat("ManualForward");
            float vertical = state.getFloat("ManualVertical");
            int tx = SONAR_CX + Math.round(forward * 190.0F);
            int ty = SONAR_CY - Math.round(vertical * 190.0F);
            line(g, SONAR_CX, SONAR_CY, tx, ty, 0xBFC9D5D5, 3);
            fillCircle(g, tx, ty, 7, 0xFFD1D7D5);
        }
    }

    private void renderTargets(GuiGraphics g) {
        ListTag targets = state.getList("Targets", Tag.TAG_COMPOUND);
        BlockPos anchor = state.contains("Anchor") ? BlockPos.of(state.getLong("Anchor")) : terminalPos;
        int visible = Math.min(5, targets.size());
        for (int i = 0; i < visible; i++) {
            CompoundTag target = targets.getCompound(i);
            BlockPos pos = BlockPos.of(target.getLong("Pos"));
            double dx = pos.getX() - anchor.getX();
            double dy = pos.getY() - anchor.getY();
            double dz = pos.getZ() - anchor.getZ();
            double horizontal = Math.copySign(Math.sqrt(dx * dx + dz * dz), dx + dz == 0 ? 1 : dx + dz);
            double maximum = Math.max(1.0D, Math.max(Math.abs(horizontal), Math.abs(dy)));
            int x = SONAR_CX + (int) Math.round(horizontal / maximum * (SONAR_RADIUS - 55));
            int y = SONAR_CY - (int) Math.round(dy / maximum * (SONAR_RADIUS - 55));
            x = Mth.clamp(x, SONAR_CX - SONAR_RADIUS + 45, SONAR_CX + SONAR_RADIUS - 45);
            y = Mth.clamp(y, SONAR_CY - SONAR_RADIUS + 45, SONAR_CY + SONAR_RADIUS - 45);
            fillCircle(g, x, y, 4, 0xFFC7C6AE);
            String label = target.getString("Name");
            int distance = target.getInt("Distance");
            int width = Math.min(145, Math.max(75, font.width(label) + 8));
            int lx = x + 6;
            if (lx + width > SONAR_CX + SONAR_RADIUS) lx = x - width - 6;
            g.fill(lx, y - 21, lx + width, y + 13, 0xB5000000);
            g.drawString(font, trim(label, 20), lx + 3, y - 18, 0xFF9CC8DB, false);
            String mission = target.getString("Mission");
            if (!mission.isBlank()) g.drawString(font, trim(mission, 20), lx + 3, y - 8, 0xFF728D97, false);
            g.drawString(font, distance + " м", lx + 3, y + 2, 0xFF9CC8DB, false);
        }
    }

    private void renderSonarControls(GuiGraphics g) {
        boolean active = state.getBoolean("ActiveSonar");
        boolean directional = state.getBoolean("Directional");
        int zoom = Mth.clamp(state.getInt("Zoom"), 0, 100);

        drawVerticalSwitch(g, 1285, 59, active);
        drawIndicator(g, 1334, 74, !active);
        drawIndicator(g, 1334, 105, active);

        g.fill(1366, 143, 1521, 168, 0xDA050706);
        g.fill(1370, 149, 1515, 162, 0xFF272525);
        int knobX = 1370 + Math.round(zoom / 100.0F * 130.0F);
        g.fill(1370, 149, knobX, 162, 0xFF9B8B73);
        g.fill(knobX - 5, 145, knobX + 7, 166, 0xFFD8D3C8);

        drawHorizontalSwitch(g, 1284, 190, directional);
    }

    private void renderSteeringControls(GuiGraphics g) {
        boolean autopilot = state.getBoolean("Autopilot");
        int selected = state.getInt("SelectedDestination");
        drawVerticalSwitch(g, 1310, 285, autopilot);
        drawIndicator(g, 1361, 287, !autopilot);
        drawIndicator(g, 1361, 319, autopilot);

        List<String> labels = new ArrayList<>();
        labels.add("Сохранять положение");
        ListTag targets = state.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(2, targets.size()); i++) labels.add(targets.getCompound(i).getString("Name"));
        while (labels.size() < 3) labels.add("—");

        for (int i = 0; i < 3; i++) {
            int y = 367 + i * 32;
            circle(g, 1350, y, 12, autopilot ? 0xFF66CDBB : 0xFF263D38, 3);
            if (selected == i) fillCircle(g, 1350, y, 6, autopilot ? 0xFF66CDBB : 0xFF263D38);
            g.drawString(font, trim(labels.get(i), 24), 1375, y - 5, autopilot ? 0xFFE7D9A1 : 0xFF4A4E49, false);
        }

        String tip = autopilot
                ? "АВТОПИЛОТ: ВКЛ.\nСохранение позиции. Выберите направление на экране."
                : "РУЧНОЕ УПРАВЛЕНИЕ\nНажмите ЛКМ внутри сонарного экрана.";
        g.fill(731, 704, 1090, 770, 0xD4080A08);
        drawCenteredMultiline(g, tip, 910, 716, 0xFFE8D99C);
    }

    private void renderReadouts(GuiGraphics g) {
        drawDigital(g, 1450, 516, displayedVertical);
        drawDigital(g, 1450, 574, displayedForward);
        drawDigital(g, 1450, 632, state.getInt("Depth"));
    }

    private void renderPowerWarning(GuiGraphics g) {
        if (state.getBoolean("Powered")) return;
        g.fill(115, 210, 505, 263, 0xA5000000);
        g.drawCenteredString(font, "ВНИМАНИЕ: НЕДОСТАТОЧНО ЭНЕРГИИ", 310, 229, 0xFFFFA000);
    }

    private void drawDigital(GuiGraphics g, int x, int y, double value) {
        g.fill(x, y, x + 113, y + 36, 0xFF0A100A);
        g.fill(x + 5, y + 4, x + 108, y + 31, 0xFFB7C99C);
        String text = Math.abs(value) >= 100.0D ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f", value);
        int textWidth = font.width(text);
        g.drawString(font, text, x + 101 - textWidth, y + 13, 0xFF18221A, false);
    }

    private void drawVerticalSwitch(GuiGraphics g, int x, int y, boolean bottom) {
        g.fill(x, y, x + 23, y + 62, 0xFF020403);
        roundedFrame(g, x, y, 23, 62, 0xFFE5E5DD);
        int knobY = bottom ? y + 36 : y + 2;
        fillCircle(g, x + 11, knobY + 10, 11, 0xFFE4E4DE);
    }

    private void drawHorizontalSwitch(GuiGraphics g, int x, int y, boolean on) {
        g.fill(x, y, x + 45, y + 23, on ? 0xFF4C956F : 0xFF9A9D99);
        roundedFrame(g, x, y, 45, 23, 0xFFE5E5DD);
        fillCircle(g, on ? x + 33 : x + 12, y + 11, 10, 0xFFD8D8D4);
    }

    private void drawIndicator(GuiGraphics g, int x, int y, boolean active) {
        fillCircle(g, x, y, 11, 0xFF5C5B50);
        fillCircle(g, x, y, 7, active ? 0xFFFF351F : 0xFF292924);
        if (active) fillCircle(g, x - 2, y - 2, 3, 0xFFFFB135);
    }

    private void drawWedge(GuiGraphics g, float angle, float halfWidth, int color) {
        for (int radius = 30; radius < SONAR_RADIUS - 10; radius += 4) {
            double start = angle - halfWidth;
            double end = angle + halfWidth;
            int x1 = SONAR_CX + (int) (Math.cos(start) * radius);
            int y1 = SONAR_CY + (int) (Math.sin(start) * radius);
            int x2 = SONAR_CX + (int) (Math.cos(end) * radius);
            int y2 = SONAR_CY + (int) (Math.sin(end) * radius);
            line(g, x1, y1, x2, y2, color, 2);
        }
        line(g, SONAR_CX, SONAR_CY, SONAR_CX + Math.cos(angle - halfWidth) * (SONAR_RADIUS - 10),
                SONAR_CY + Math.sin(angle - halfWidth) * (SONAR_RADIUS - 10), 0x90DCDAC5, 2);
        line(g, SONAR_CX, SONAR_CY, SONAR_CX + Math.cos(angle + halfWidth) * (SONAR_RADIUS - 10),
                SONAR_CY + Math.sin(angle + halfWidth) * (SONAR_RADIUS - 10), 0x90DCDAC5, 2);
    }

    private void drawSubmarineOutline(GuiGraphics g, int cx, int cy, int radius, int color) {
        line(g, cx - radius, cy, cx - radius + 20, cy - 17, color, 3);
        line(g, cx - radius + 20, cy - 17, cx + radius - 14, cy - 12, color, 3);
        line(g, cx + radius - 14, cy - 12, cx + radius, cy - 2, color, 3);
        line(g, cx + radius, cy - 2, cx + radius - 12, cy + 14, color, 3);
        line(g, cx + radius - 12, cy + 14, cx - radius + 20, cy + 18, color, 3);
        line(g, cx - radius + 20, cy + 18, cx - radius, cy, color, 3);
    }

    private static float angularDifference(float a, float b) {
        float difference = (a - b) % ((float) Math.PI * 2.0F);
        if (difference > Math.PI) difference -= (float) Math.PI * 2.0F;
        if (difference < -Math.PI) difference += (float) Math.PI * 2.0F;
        return Math.abs(difference);
    }

    private String trim(String value, int maximum) {
        if (value == null) return "";
        if (value.length() <= maximum) return value;
        return value.substring(0, Math.max(1, maximum - 1)) + "…";
    }

    private void drawCenteredMultiline(GuiGraphics g, String value, int x, int y, int color) {
        String[] lines = value.split("\\n");
        for (int i = 0; i < lines.length; i++) g.drawCenteredString(font, lines[i], x, y + i * 12, color);
    }

    private void send(String action, int value) {
        ModNetworking.CHANNEL.sendToServer(new NavigationPackets.ServerboundNavigationAction(terminalPos, action, value));
    }

    private void send(String action, float x, float y) {
        ModNetworking.CHANNEL.sendToServer(new NavigationPackets.ServerboundNavigationAction(terminalPos, action, 0, x, y));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = (mouseX - left()) / scale();
        double y = (mouseY - top()) / scale();
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (inside(x, y, 1270, 50, 1355, 135)) {
            send("toggle_sonar", 0);
            return true;
        }
        if (inside(x, y, 1280, 182, 1535, 222)) {
            send("toggle_directional", 0);
            return true;
        }
        if (inside(x, y, 1360, 139, 1525, 172)) {
            draggingZoom = true;
            updateZoom(x);
            return true;
        }
        if (inside(x, y, 1280, 275, 1430, 340)) {
            send("toggle_autopilot", 0);
            return true;
        }
        if (inside(x, y, 1328, 350, 1555, 450) && state.getBoolean("Autopilot")) {
            int index = Mth.clamp((int) ((y - 350) / 32.0D), 0, 2);
            send("select", index);
            return true;
        }
        if (inside(x, y, 188, 478, 411, 543)) {
            send("shutdown_reactor", 0);
            return true;
        }

        double dx = x - SONAR_CX;
        double dy = y - SONAR_CY;
        if (dx * dx + dy * dy <= (SONAR_RADIUS - 8.0D) * (SONAR_RADIUS - 8.0D)) {
            if (!state.getBoolean("Autopilot")) {
                float forward = (float) Mth.clamp(dx / 190.0D, -1.0D, 1.0D);
                float vertical = (float) Mth.clamp(-dy / 190.0D, -1.0D, 1.0D);
                send("manual", forward, vertical);
            } else if (state.getBoolean("Directional")) {
                float angle = (float) Math.atan2(dy, dx);
                send("beam", angle, 0.0F);
                draggingBeam = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double x = (mouseX - left()) / scale();
        double y = (mouseY - top()) / scale();
        if (button == 0 && draggingZoom) {
            updateZoom(x);
            return true;
        }
        if (button == 0 && draggingBeam) {
            send("beam", (float) Math.atan2(y - SONAR_CY, x - SONAR_CX), 0.0F);
            return true;
        }
        if (button == 0 && !state.getBoolean("Autopilot")) {
            double dx = x - SONAR_CX;
            double dy = y - SONAR_CY;
            if (dx * dx + dy * dy <= SONAR_RADIUS * SONAR_RADIUS) {
                send("manual", (float) Mth.clamp(dx / 190.0D, -1.0D, 1.0D),
                        (float) Mth.clamp(-dy / 190.0D, -1.0D, 1.0D));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingZoom = false;
        draggingBeam = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateZoom(double logicalX) {
        int zoom = Mth.clamp((int) Math.round((logicalX - 1370.0D) / 130.0D * 100.0D), 0, 100);
        send("zoom", zoom);
    }

    private static boolean inside(double x, double y, double x1, double y1, double x2, double y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void roundedFrame(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x + 3, y, x + width - 3, y + 1, color);
        g.fill(x + 3, y + height - 1, x + width - 3, y + height, color);
        g.fill(x, y + 3, x + 1, y + height - 3, color);
        g.fill(x + width - 1, y + 3, x + width, y + height - 3, color);
    }

    private static void fillCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - y * y));
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }

    private static void circle(GuiGraphics g, int cx, int cy, int radius, int color, int thickness) {
        int steps = Math.max(48, radius * 5);
        double previousX = cx + radius;
        double previousY = cy;
        for (int i = 1; i <= steps; i++) {
            double angle = i * Math.PI * 2.0D / steps;
            double x = cx + Math.cos(angle) * radius;
            double y = cy + Math.sin(angle) * radius;
            line(g, previousX, previousY, x, y, color, thickness);
            previousX = x;
            previousY = y;
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
