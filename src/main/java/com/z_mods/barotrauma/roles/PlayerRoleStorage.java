package com.z_mods.barotrauma.roles;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

public final class PlayerRoleStorage {
    private static final String ROLE_TAG = "barotrauma_role";

    private PlayerRoleStorage() {
    }

    public static String getRoleId(Player player) {
        if (!player.getPersistentData().contains(ROLE_TAG)) {
            return null;
        }

        String roleId = player.getPersistentData().getString(ROLE_TAG);
        if (roleId.isBlank()) {
            return null;
        }

        return roleId.toLowerCase(Locale.ROOT);
    }

    public static void setRoleId(ServerPlayer player, String roleId) {
        player.getPersistentData().putString(ROLE_TAG, roleId.toLowerCase(Locale.ROOT));
    }

    public static void clearRole(ServerPlayer player) {
        player.getPersistentData().remove(ROLE_TAG);
    }
}
