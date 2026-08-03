package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.panel.PanelSettings;

public final class ClientPanelState {
    private static PanelSettings settings = new PanelSettings();
    private static boolean editable;

    private ClientPanelState() {
    }

    public static PanelSettings settings() {
        return settings;
    }

    public static void apply(PanelSettings value) {
        settings = value.copy();
    }

    public static boolean editable() {
        return editable;
    }

    public static void setEditable(boolean value) {
        editable = value;
    }
}
