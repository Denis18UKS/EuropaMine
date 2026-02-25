package com.z_mods.barotrauma.roles;

import com.z_mods.barotrauma.roles.Captain.Captain;
import com.z_mods.barotrauma.roles.Engineer.Engineer;
import com.z_mods.barotrauma.roles.Helper.Helper;
import com.z_mods.barotrauma.roles.Mechanic.Mechanic;
import com.z_mods.barotrauma.roles.Medic.Medic;
import com.z_mods.barotrauma.roles.Securiy_Office_GSB.GSB;
import com.z_mods.barotrauma.roles.traitor.Traitor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RoleRegistry {
    private static final Map<String, RoleDefinition> ROLES = new LinkedHashMap<>();

    static {
        register(Captain.ROLE);
        register(Engineer.ROLE);
        register(Helper.ROLE);
        register(Mechanic.ROLE);
        register(Medic.ROLE);
        register(GSB.ROLE);
        register(Traitor.ROLE);
    }

    private RoleRegistry() {
    }

    private static void register(RoleDefinition role) {
        ROLES.put(role.id(), role);
    }

    public static RoleDefinition get(String roleId) {
        if (roleId == null) {
            return null;
        }

        return ROLES.get(roleId.toLowerCase(Locale.ROOT));
    }

    public static Collection<RoleDefinition> all() {
        return ROLES.values();
    }

    public static Iterable<String> ids() {
        return ROLES.keySet();
    }
}
