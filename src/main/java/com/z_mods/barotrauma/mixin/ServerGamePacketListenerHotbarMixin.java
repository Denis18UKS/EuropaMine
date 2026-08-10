package com.z_mods.barotrauma.mixin;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerHotbarMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void barotrauma$acceptExtraHotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        int slot = packet.getSlot();
        int max = ExtraHotbar.VANILLA_HOTBAR + ExtraHotbar.getAppliedCount(player);
        if (slot >= ExtraHotbar.VANILLA_HOTBAR && slot < max) {
            player.getInventory().selected = slot;
            player.resetLastActionTime();
            ci.cancel();
        }
    }
}
