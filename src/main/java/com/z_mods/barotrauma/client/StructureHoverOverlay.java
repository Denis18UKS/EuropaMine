package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.blocks.StructureConfigBlockEntity;
import com.z_mods.barotrauma.structure.StructureSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT)
public final class StructureHoverOverlay {
    private static int tickCounter;

    private StructureHoverOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.hitResult == null) {
            return;
        }

        if (++tickCounter % 5 != 0 || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        if (!(minecraft.level.getBlockEntity(pos) instanceof StructureConfigBlockEntity blockEntity) || !blockEntity.hasConfig()) {
            return;
        }

        BlockState state = minecraft.level.getBlockState(pos);
        minecraft.player.displayClientMessage(Component.literal(getTypeName(blockEntity.getConfig()))
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(state.getBlock().getName().copy().withStyle(ChatFormatting.WHITE)), true);
    }

    private static String getTypeName(StructureSettings.Config config) {
        if (config.kind() == StructureSettings.Kind.PLATFORM) {
            return "\u041f\u043b\u0430\u0442\u0444\u043e\u0440\u043c\u0430";
        }
        return config.wallType() == StructureSettings.WallType.INTERNAL
                ? "\u0412\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u044f\u044f \u0441\u0442\u0435\u043d\u0430"
                : "\u0412\u043d\u0435\u0448\u043d\u044f\u044f \u0441\u0442\u0435\u043d\u0430";
    }
}
