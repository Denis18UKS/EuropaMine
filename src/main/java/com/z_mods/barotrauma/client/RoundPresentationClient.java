package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.item.ChargedSupplyItem;
import com.z_mods.barotrauma.item.OxygenMaskItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Round-start loading presentation, mission card and explicit selected-slot indicator. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RoundPresentationClient {
    private static final String[] TIPS = {
            "Совет: реактор без топлива не производит энергию.",
            "Совет: лишние топливные стержни могут привести к перегреву.",
            "Совет: сварочный аппарат заправляется только сварочным топливом.",
            "Совет: резак и кислородная маска используют кислородные баллоны.",
            "Совет: три инструмента зон разделяют внешние стены, внутренние стены и пол."
    };
    private static int loadingTicks;
    private static int loadingTotal;
    private static int missionTicks;
    private static String mission = "Задание не выбрано";

    private RoundPresentationClient() {}

    public static void start(String missionText, int ticks) {
        mission = missionText == null || missionText.isBlank() ? "Задание не выбрано" : missionText;
        loadingTicks = Math.max(20, ticks);
        loadingTotal = loadingTicks;
        missionTicks = 0;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (loadingTicks > 0) {
            loadingTicks--;
            if (loadingTicks == 0) missionTicks = 180;
        } else if (missionTicks > 0) missionTicks--;
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        drawSelectedIndicator(g, mc, w, h);
        if (loadingTicks > 0) drawLoading(g, mc, w, h);
        else if (missionTicks > 0) drawMission(g, mc, w, h);
    }

    private static void drawLoading(GuiGraphics g, Minecraft mc, int w, int h) {
        g.fill(0, 0, w, h, 0xF3080D0C);
        int boxW = Math.min(560, w - 40);
        int left = (w - boxW) / 2;
        int y = h / 2 - 48;
        g.fill(left, y, left + boxW, y + 98, 0xF20C1714);
        g.renderOutline(left, y, boxW, 98, 0xFF4D7466);
        g.fill(left, y, left + boxW, y + 3, 0xFF8EB89F);
        g.drawCenteredString(mc.font, "ЗАГРУЗКА…", w / 2, y + 19, 0xFFF0E4BA);
        int elapsed = Math.max(0, loadingTotal - loadingTicks);
        int tipIndex = (elapsed / 50) % TIPS.length;
        g.drawCenteredString(mc.font, TIPS[tipIndex], w / 2, y + 46, 0xFF9FB4AC);
        int barLeft = left + 32, barRight = left + boxW - 32;
        g.fill(barLeft, y + 75, barRight, y + 81, 0xFF17241F);
        int fill = loadingTotal <= 0 ? 0 : (int)((barRight - barLeft) * (elapsed / (double)loadingTotal));
        g.fill(barLeft, y + 75, barLeft + fill, y + 81, 0xFF77C49E);
    }

    private static void drawMission(GuiGraphics g, Minecraft mc, int w, int h) {
        int age = 180 - missionTicks;
        float alpha = Math.min(1.0F, age / 18.0F) * Math.min(1.0F, missionTicks / 25.0F);
        int a = Math.max(0, Math.min(255, (int)(220 * alpha)));
        int bw = Math.min(590, w - 30);
        int left = (w - bw) / 2;
        int y = h - 92;
        g.fill(left, y, left + bw, y + 45, (a << 24) | 0x0B1713);
        g.fill(left, y, left + 4, y + 45, (Math.min(255, a + 25) << 24) | 0x70B592);
        g.drawString(mc.font, "МИССИЯ", left + 15, y + 8, 0xFFF1DFA5, false);
        String text = mc.font.plainSubstrByWidth(mission, bw - 30);
        g.drawCenteredString(mc.font, text, w / 2, y + 25, 0xFFE9F1EC);
    }

    private static void drawSelectedIndicator(GuiGraphics g, Minecraft mc, int w, int h) {
        int extra = ExtraHotbar.getClientSelectedExtra();
        int x;
        int y;
        ItemStack selected;
        if (extra >= 0) {
            int vanillaLeft = w / 2 - 91;
            x = vanillaLeft + 9 * 20 + HotbarLayoutSettings.x(extra) + 3;
            y = h - 27 + HotbarLayoutSettings.y(extra);
            selected = ExtraHotbar.getStack(mc.player, extra);
        } else {
            int slot = Math.max(0, Math.min(8, mc.player.getInventory().selected));
            x = w / 2 - 91 + 3 + slot * 20;
            y = h - 27;
            selected = mc.player.getInventory().items.get(slot);
        }
        g.fill(x + 2, y, x + 14, y + 2, 0xFF5BEE8A);
        g.fill(x + 5, y - 2, x + 11, y, 0xFFB6FFD0);

        int charge = -1, max = 1;
        if (selected.getItem() instanceof ChargedSupplyItem) {
            charge = ChargedSupplyItem.charge(selected); max = ChargedSupplyItem.MAX_CHARGE;
        } else if (selected.getItem() instanceof OxygenMaskItem) {
            charge = OxygenMaskItem.charge(selected); max = OxygenMaskItem.MAX_CHARGE;
        }
        if (charge >= 0) {
            int bx = w / 2 - 55, by = h - 48;
            g.fill(bx, by, bx + 110, by + 8, 0xCC09110E);
            g.fill(bx + 2, by + 2, bx + 2 + (int)(106 * charge / (double)max), by + 6, 0xFF25E66F);
        }
    }
}
