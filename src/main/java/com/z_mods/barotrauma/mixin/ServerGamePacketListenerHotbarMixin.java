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

/** Vanilla carried-item packets are only ever allowed to select slots 0..8. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerHotbarMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void barotrauma$clearVirtualSelection(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        ExtraHotbar.clearSelectedExtra(player);
        ExtraHotbar.sanitizeVanillaSelected(player);
    }
}
