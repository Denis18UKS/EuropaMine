package com.z_mods.barotrauma.roles;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

public final class RoleCommand {
    private RoleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("role")
                .then(Commands.literal("status")
                        .executes(RoleCommand::statusRole))
                .then(Commands.literal("reset")
                        .executes(RoleCommand::resetRole))
                .then(Commands.argument("role", StringArgumentType.word())
                        .suggests(RoleCommand::suggestRoles)
                        .executes(RoleCommand::setRole)));
    }

    private static CompletableFuture<Suggestions> suggestRoles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RoleRegistry.ids(), builder);
    }

    private static int setRole(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String roleId = StringArgumentType.getString(context, "role");
        RoleDefinition role = RoleRegistry.get(roleId);

        if (role == null) {
            context.getSource().sendFailure(Component.literal("Неизвестная роль: " + roleId));
            return 0;
        }

        PlayerRoleStorage.setRoleId(player, role.id());
        context.getSource().sendSuccess(() -> Component.literal("Роль установлена: " + role.displayName()), false);
        return 1;
    }

    private static int resetRole(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerRoleStorage.clearRole(player);
        context.getSource().sendSuccess(() -> Component.literal("Роль сброшена."), false);
        return 1;
    }

    private static int statusRole(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String roleId = PlayerRoleStorage.getRoleId(player);
        RoleDefinition role = RoleRegistry.get(roleId);

        if (role == null) {
            context.getSource().sendSuccess(() -> Component.literal("Роль не выбрана."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("Текущая роль: " + role.displayName() + " (" + role.id() + ")"), false);
        return 1;
    }
}
