package com.z_mods.barotrauma.navigation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.network.NavigationPackets;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.UUID;

/** Server-side navigation routing, world ticking and map-maker commands. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NavigationSystem {
    private static final double REQUIRED_OUTPUT = 25.0D;

    private NavigationSystem() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            NavigationWorldData.get(level).tick(level);
        }
    }

    public static boolean hasPower(ServerLevel level, BlockPos terminalPos) {
        return PowerWorldData.get(level).availableOutput(terminalPos) >= REQUIRED_OUTPUT;
    }

    public static int shutdownReactors(ServerLevel level, BlockPos terminalPos) {
        return PowerWorldData.get(level).shutdownReactors(terminalPos);
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos terminalPos) {
        NavigationWorldData data = NavigationWorldData.get(level);
        BlockPos resolved = data.resolveTerminalPos(terminalPos);
        data.terminalOrCreate(resolved);
        NavigationPackets.sendOpen(player, resolved);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("baronav")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("target")
                        .then(Commands.literal("add")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("mission", StringArgumentType.word())
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                .executes(context -> {
                                                                    ServerLevel level = context.getSource().getLevel();
                                                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                                                                    String name = StringArgumentType.getString(context, "name");
                                                                    NavigationReferenceData.TargetType type = NavigationReferenceData.TargetType.parse(
                                                                            StringArgumentType.getString(context, "type"));
                                                                    NavigationReferenceData.MissionType mission = NavigationReferenceData.MissionType.parse(
                                                                            StringArgumentType.getString(context, "mission"));
                                                                    NavigationWorldData.NavigationTarget target = NavigationWorldData.get(level)
                                                                            .addTarget(type, mission, pos, name);
                                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                                            "Добавлена навигационная метка " + target.displayName()
                                                                                    + " [" + target.id() + "]"), true);
                                                                    return 1;
                                                                }))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .executes(context -> {
                                            try {
                                                UUID id = UUID.fromString(StringArgumentType.getString(context, "uuid"));
                                                boolean removed = NavigationWorldData.get(context.getSource().getLevel()).removeTarget(id);
                                                context.getSource().sendSuccess(() -> Component.literal(removed
                                                        ? "Навигационная метка удалена."
                                                        : "Метка с таким UUID не найдена."), true);
                                                return removed ? 1 : 0;
                                            } catch (IllegalArgumentException ex) {
                                                context.getSource().sendFailure(Component.literal("Некорректный UUID."));
                                                return 0;
                                            }
                                        })))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    var targets = NavigationWorldData.get(context.getSource().getLevel()).allTargets();
                                    if (targets.isEmpty()) {
                                        context.getSource().sendSuccess(() -> Component.literal("Пользовательских навигационных меток нет."), false);
                                    } else {
                                        for (var target : targets) {
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    target.id() + " | " + target.type().russianName() + " | "
                                                            + target.displayName() + " | " + target.pos().toShortString()), false);
                                        }
                                    }
                                    return targets.size();
                                })))
                .then(Commands.literal("types")
                        .executes(context -> {
                            StringBuilder targetTypes = new StringBuilder("Типы целей: ");
                            for (var type : NavigationReferenceData.TargetType.values()) {
                                if (targetTypes.length() > 12) targetTypes.append(", ");
                                targetTypes.append(type.name().toLowerCase(Locale.ROOT));
                            }
                            context.getSource().sendSuccess(() -> Component.literal(targetTypes.toString()), false);
                            StringBuilder missionTypes = new StringBuilder("Типы миссий: ");
                            for (var type : NavigationReferenceData.MissionType.values()) {
                                if (missionTypes.length() > 13) missionTypes.append(", ");
                                missionTypes.append(type.name().toLowerCase(Locale.ROOT));
                            }
                            context.getSource().sendSuccess(() -> Component.literal(missionTypes.toString()), false);
                            return 1;
                        })));
    }
}
