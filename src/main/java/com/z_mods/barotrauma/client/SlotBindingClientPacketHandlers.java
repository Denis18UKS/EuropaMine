package com.z_mods.barotrauma.client;

import net.minecraft.client.Minecraft;

/** Client-only endpoint for slot-binding network packets. */
public final class SlotBindingClientPacketHandlers {
    private SlotBindingClientPacketHandlers() {}

    public static void open(String[] bindings) {
        Minecraft.getInstance().setScreen(new SlotBindingConfiguratorScreen(bindings));
    }
}
