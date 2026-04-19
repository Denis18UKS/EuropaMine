package com.z_mods.barotrauma.client;

import com.mojang.brigadier.CommandDispatcher;
import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
public final class HudClientCommands {
    private HudClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("barohud")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new HudConfigScreen()));
                    return 1;
                }));
    }
}
