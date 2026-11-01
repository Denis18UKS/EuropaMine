package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.hotbar.ExtraHotbarStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client-only endpoints for common hotbar network packets. */
public final class HotbarClientPacketHandlers {
    private HotbarClientPacketHandlers() {}

    public static void showLayoutNotice() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    "Настройка слотов выполняется прямо на настенной панели. Отдельный GUI больше не открывается."), true);
        }
    }

    public static void handlePanelClick(float canvasX, float canvasY) {
        HotbarLayoutSettings.handlePanelClick(canvasX, canvasY);
    }

    public static void applySync(int count, int selectedExtra, ItemStack[] stacks) {
        ExtraHotbar.setClientAppliedCount(count);
        ExtraHotbar.setClientSelectedExtra(selectedExtra);
        if (Minecraft.getInstance().player == null) return;
        ExtraHotbarStorage storage = ExtraHotbar.storage(Minecraft.getInstance().player);
        if (storage == null) return;
        for (int i = 0; i < ExtraHotbar.MAX_EXTRA; i++) {
            ItemStack stack = i < stacks.length ? stacks[i] : ItemStack.EMPTY;
            storage.setStackInSlot(i, stack.copy());
        }
    }
}
