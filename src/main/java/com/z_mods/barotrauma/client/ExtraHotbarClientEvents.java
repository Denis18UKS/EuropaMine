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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
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
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
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
        public static void renderHotbarPre(RenderGuiOverlayEvent.Pre event) {
            if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || ExtraHotbar.getClientSelectedExtra() < 0) return;

            event.setCanceled(true);
            GuiGraphics g = event.getGuiGraphics();
            drawVanillaBaseWithoutSelection(g, mc, event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
            drawExtraSlots(g, mc, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());

            PlayerProfileOverlay.drawLayoutSlots(g, mc);
            PlayerProfileOverlay.drawProfilePanel(g, mc, event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(), false);
        }

        @SubscribeEvent
        public static void renderHotbar(RenderGuiOverlayEvent.Post event) {
            if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || ExtraHotbar.getClientSelectedExtra() >= 0) return;
            drawExtraSlots(event.getGuiGraphics(), mc, event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
        }

        private static void drawVanillaBaseWithoutSelection(GuiGraphics g, Minecraft mc, int screenWidth, int screenHeight) {
            int left = screenWidth / 2 - 91;
            int y = screenHeight - 22;
            g.blit(WIDGETS, left, y, 0, 0, 182, 22);
            for (int i = 0; i < ExtraHotbar.VANILLA_HOTBAR; i++) {
                ItemStack stack = mc.player.getInventory().items.get(i);
                if (stack.isEmpty()) continue;
                int itemX = left + 3 + i * 20;
                int itemY = y + 3;
                g.renderItem(stack, itemX, itemY);
                g.renderItemDecorations(mc.font, stack, itemX, itemY);
            }

            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty()) {
                HumanoidArm offArm = mc.player.getMainArm().getOpposite();
                int slotX;
                if (offArm == HumanoidArm.LEFT) {
                    slotX = left - 29;
                    g.blit(WIDGETS, slotX, y - 1, 24, 22, 29, 24);
                } else {
                    slotX = left + 182;
                    g.blit(WIDGETS, slotX, y - 1, 53, 22, 29, 24);
                }
                g.renderItem(offhand, slotX + 3, y + 3);
                g.renderItemDecorations(mc.font, offhand, slotX + 3, y + 3);
            }
        }

        private static void drawExtraSlots(GuiGraphics g, Minecraft mc, int screenWidth, int screenHeight) {
            int vanillaLeft = screenWidth / 2 - 91;
            int baseY = screenHeight - 22;
            int count = HotbarLayoutSettings.displayedCount();
            int extraBaseX = vanillaLeft + 9 * 20;
            int appliedCount = ExtraHotbar.getClientAppliedCount();
            for (int i = 0; i < count; i++) {
                int x = extraBaseX + HotbarLayoutSettings.x(i);
                int y = baseY + HotbarLayoutSettings.y(i);
                boolean realSlot = i < appliedCount;
                boolean selected = realSlot && ExtraHotbar.getClientSelectedExtra() == i;
                drawVanillaSlot(g, mc, i, x, y, selected, realSlot);
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
            int panelX = left + 176;
            int panelY = top + 4;
            int panelH = Math.max(26, count * 18 + 10);
            g.fill(panelX, panelY, panelX + 58, panelY + panelH, 0xD0101514);
            g.renderOutline(panelX, panelY, 58, panelH, 0xFF65746F);
            for (int i = 0; i < count; i++) {
                int x = left + 180;
                int y = top + 8 + i * 18;
                // Vanilla slot texture; redraw the stack above it because this hook runs after the vanilla screen.
                g.blit(WIDGETS, x - 2, y - 2, 20, 0, 22, 22);
                ItemStack stack = ExtraHotbar.getStack(mc.player, i);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x, y);
                    g.renderItemDecorations(mc.font, stack, x, y);
                }
                g.drawString(mc.font, Integer.toString(10 + i), x + 21, y + 5, 0xFFE6E6E6, true);
            }
        }

        private static void drawVanillaSlot(GuiGraphics g, Minecraft mc, int extraIndex,
                                            int x, int y, boolean selected, boolean renderItem) {
            g.blit(WIDGETS, x, y, 20, 0, 22, 22);
            if (selected) g.blit(WIDGETS, x - 1, y - 1, 0, 22, 24, 24);
            if (!renderItem) return;
            ItemStack stack = ExtraHotbar.getStack(mc.player, extraIndex);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 3, y + 3);
                g.renderItemDecorations(mc.font, stack, x + 3, y + 3);
            }
        }
    }
}
