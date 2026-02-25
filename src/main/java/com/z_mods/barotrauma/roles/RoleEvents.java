package com.z_mods.barotrauma.roles;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoleEvents {
    private RoleEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RoleCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        RoleDefinition role = RoleRegistry.get(PlayerRoleStorage.getRoleId(event.getEntity()));
        if (role == null) {
            return;
        }

        event.setDisplayname(Component.literal(role.prefix()).append(event.getDisplayname()));
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        RoleDefinition role = RoleRegistry.get(PlayerRoleStorage.getRoleId(event.getEntity()));
        if (role == null) {
            return;
        }

        Component baseName = event.getDisplayName() != null ? event.getDisplayName() : event.getEntity().getName();
        event.setDisplayName(Component.literal(role.prefix()).append(baseName));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        String roleId = PlayerRoleStorage.getRoleId(event.getOriginal());
        if (roleId != null) {
            PlayerRoleStorage.setRoleId((net.minecraft.server.level.ServerPlayer) event.getEntity(), roleId);
        }
    }
}
