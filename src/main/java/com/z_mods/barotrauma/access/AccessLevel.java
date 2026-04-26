package com.z_mods.barotrauma.access;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.util.Locale;

public enum AccessLevel {
    NONE("none", "None"),
    MEDIC("medic", "Medic", "\u043c\u0435\u0434\u0438\u043a", "medik"),
    MECHANIC("mechanic", "Mechanic", "\u043c\u0435\u0445\u0430\u043d\u0438\u043a"),
    HELPER("helper", "Helper", "\u043f\u043e\u043c\u043e\u0449\u043d\u0438\u043a"),
    GSB("gsb", "GSB", "\u0433\u0441\u0431", "security"),
    CAPTAIN("captain", "Captain", "\u043a\u0430\u043f\u0438\u0442\u0430\u043d"),
    ENGINEER("engineer", "Engineer", "\u0438\u043d\u0436\u0435\u043d\u0435\u0440");

    private final String id;
    private final String label;
    private final String[] aliases;

    AccessLevel(String id, String label, String... aliases) {
        this.id = id;
        this.label = label;
        this.aliases = aliases;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable("access.barotrauma." + id);
    }

    public AccessLevel next() {
        AccessLevel[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean allows(Player player) {
        if (this == NONE || player.getAbilities().instabuild) {
            return true;
        }

        Team team = player.getTeam();
        return team != null && matches(team.getName());
    }

    public static AccessLevel byId(String id) {
        if (id != null) {
            for (AccessLevel level : values()) {
                if (level.id.equals(normalize(id))) {
                    return level;
                }
            }
        }
        return NONE;
    }

    public static AccessLevel fromText(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return NONE;
        }

        for (AccessLevel level : values()) {
            if (level.matches(normalized)) {
                return level;
            }
        }
        return NONE;
    }

    private boolean matches(String text) {
        String normalized = normalize(text);
        if (id.equals(normalized) || normalize(label).equals(normalized)) {
            return true;
        }

        for (String alias : aliases) {
            if (normalize(alias).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }
}
