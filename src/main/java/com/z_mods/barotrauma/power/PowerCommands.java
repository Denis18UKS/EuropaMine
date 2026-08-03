package com.z_mods.barotrauma.power;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Administrative commands for the skill and traitor checks used by electrical panels. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PowerCommands {
    private PowerCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("barpower")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("skill")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    player.getPersistentData().putInt("barotrauma_electronics", value);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "Навык Электроника игрока " + player.getScoreboardName()
                                                    + " установлен на " + value + "."), true);
                                    return value;
                                }))));

        root.then(Commands.literal("traitor")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    player.getPersistentData().putBoolean("barotrauma_traitor", enabled);
                                    if (enabled) player.addTag("barotrauma_traitor");
                                    else player.removeTag("barotrauma_traitor");
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "Роль предателя для " + player.getScoreboardName()
                                                    + ": " + (enabled ? "включена" : "выключена") + "."), true);
                                    return enabled ? 1 : 0;
                                }))));

        event.getDispatcher().register(root);
    }
}
