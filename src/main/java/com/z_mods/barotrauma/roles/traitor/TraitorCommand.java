package com.z_mods.barotrauma.roles.traitor;

import com.z_mods.barotrauma.roles.RoleDefinition;

public final class TraitorCommand {
    private TraitorCommand() {
    }

    public static RoleDefinition role() {
        return Traitor.ROLE;
    }
}
