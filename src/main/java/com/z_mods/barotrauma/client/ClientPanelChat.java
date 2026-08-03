package com.z_mods.barotrauma.client;

import java.util.ArrayList;
import java.util.List;

/** Synchronized chat history shared by the settings screen and the in-world panel renderer. */
public final class ClientPanelChat {
    private static final int MAX_LINES = 50;
    private static final List<String> LINES = new ArrayList<>();

    private ClientPanelChat() {
    }

    public static void apply(String line, boolean clear) {
        if (clear) LINES.clear();
        if (line != null && !line.isBlank()) LINES.add(line);
        while (LINES.size() > MAX_LINES) LINES.remove(0);
    }

    public static List<String> lines() {
        return List.copyOf(LINES);
    }
}
