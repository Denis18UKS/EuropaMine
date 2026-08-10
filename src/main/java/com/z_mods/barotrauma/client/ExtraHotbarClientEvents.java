package com.z_mods.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.network.HotbarPackets;
import com.z_mods.barotrauma.network.ModNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ExtraHotbarClientEvents {
    public static final KeyMapping SLOT_TEN = new KeyMapping("key.barotrauma.hotbar_10",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_0, "key.categories.barotrauma");

    private ExtraHotbarClientEvents() {}

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(SLOT_TEN); }
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Repair a client player copied from the unsafe build where selected could be 9..17.
            if (mc.player.getInventory().selected >= ExtraHotbar.VANILLA_HOTBAR) {
                int extra = mc.player.getInventory().selected - ExtraHotbar.VANILLA_HOTBAR;
                mc.player.getInventory().selected = 0;
                if (extra >= 0 && extra < ExtraHotbar.getClientAppliedCount()) selectExtra(extra);
            } else if (mc.player.getInventory().selected < 0) {
                mc.player.getInventory().selected = 0;
            }

            if (mc.screen != null) return;
            while (SLOT_TEN.consumeClick()) selectExtra(0);
        }

        @SubscribeEvent
        public static void keyInput(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || ExtraHotbar.getClientSelectedExtra() < 0) return;
            for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
                if (mc.options.keyHotbarSlots[i].matches(event.getKey(), event.getScanCode())) {
                    ExtraHotbar.setClientSelectedExtra(-1);
                    ModNetworking.CHANNEL.sendToServer(new HotbarPackets.ServerboundSelectExtra(-1));
                    return;
                }
            }
        }

        @SubscribeEvent
        public static void mouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || event.getScrollDelta() == 0.0D) return;
            int total = ExtraHotbar.VANILLA_HOTBAR + ExtraHotbar.getClientAppliedCount();
            if (total <= ExtraHotbar.VANILLA_HOTBAR) return;

            int extraSelected = ExtraHotbar.getClientSelectedExtra();
            int current = extraSelected >= 0
                    ? ExtraHotbar.VANILLA_HOTBAR + extraSelected
                    : mc.player.getInventory().selected;
            if (current < 0 || current >= total) current = 0;
            int delta = event.getScrollDelta() > 0 ? -1 : 1;
            int target = Math.floorMod(current + delta, total);
            if (target < ExtraHotbar.VANILLA_HOTBAR) selectVanilla(mc, target);
            else selectExtra(target - ExtraHotbar.VANILLA_HOTBAR);
            event.setCanceled(true);
        }

        private static void selectVanilla(Minecraft mc, int slot) {
            if (mc.player == null || slot < 0 || slot >= ExtraHotbar.VANILLA_HOTBAR) return;
            ExtraHotbar.setClientSelectedExtra(-1);
            mc.player.getInventory().selected = slot;
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }

        private static void selectExtra(int extraIndex) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || extraIndex < 0 || extraIndex >= ExtraHotbar.getClientAppliedCount()) return;
            ExtraHotbar.setClientSelectedExtra(extraIndex);
            ModNetworking.CHANNEL.sendToServer(new HotbarPackets.ServerboundSelectExtra(extraIndex));
        }

        @SubscribeEvent
        public static void renderHotbar(RenderGuiOverlayEvent.Post event) {
            if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;
            GuiGraphics g = event.getGuiGraphics();
            int count = ExtraHotbar.getClientAppliedCount();
            int baseX = event.getWindow().getGuiScaledWidth() / 2 - 91 + 9 * 20;
            int baseY = event.getWindow().getGuiScaledHeight() - 22;
            for (int i = 0; i < count; i++) {
                int x = baseX + HotbarLayoutSettings.x(i);
                int y = baseY + HotbarLayoutSettings.y(i);
                boolean selected = ExtraHotbar.getClientSelectedExtra() == i;
                drawRealSlot(g, mc, i, x, y, selected);
            }
        }

        @SubscribeEvent
        public static void renderInventoryExtraRow(ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof InventoryScreen)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            GuiGraphics g = event.getGuiGraphics();
            int left = (event.getScreen().width - 176) / 2;
            int top = (event.getScreen().height - 166) / 2;
            int count = ExtraHotbar.getClientAppliedCount();
            for (int i = 0; i < count; i++) {
                int x = left + 7 + i * 18;
                int y = top + 165;
                g.renderOutline(x, y, 18, 18, 0xFF5B766E);
                g.drawString(mc.font, i == 0 ? "0" : Integer.toString(10 + i), x + 5, y + 20, 0xFFB7C8C2, false);
            }
        }

        private static void drawRealSlot(GuiGraphics g, Minecraft mc, int extraIndex, int x, int y, boolean selected) {
            g.fill(x, y, x + 20, y + 20, 0xD0111716);
            g.renderOutline(x, y, 20, 20, selected ? 0xFFF3D680 : 0xFF50665F);
            ItemStack stack = ExtraHotbar.getStack(mc.player, extraIndex);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 2, y + 2);
                g.renderItemDecorations(mc.font, stack, x + 2, y + 2);
            }
            if (selected) g.renderOutline(x - 2, y - 2, 24, 24, 0xFFFFFFFF);
            if (extraIndex == 0) g.drawString(mc.font, "0", x + 12, y + 2, 0xFFA9B4B0, false);
        }
    }
}
