package com.z_mods.barotrauma.access;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.blocks.VentDecoIntEntity;
import com.z_mods.barotrauma.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID)
public final class AccessEvents {
    private AccessEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getLevel().isClientSide || player.getItemInHand(event.getHand()).is(ModItems.ACCESS_CONFIGURATOR.get())) {
            return;
        }

        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (!(blockEntity instanceof Container) || blockEntity instanceof VentDecoIntEntity) {
            return;
        }

        AccessLevel accessLevel = AccessStorage.get(blockEntity);
        if (accessLevel.allows(player)) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.barotrauma.access_denied")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }
}
