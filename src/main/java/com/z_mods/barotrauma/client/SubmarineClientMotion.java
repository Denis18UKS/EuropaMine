package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.entity.SubmarineContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.NavigationPackets;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/**
 * Client prediction for a player standing/walking on a detached submarine.
 *
 * Simulated/Aeronautics delegates this job to Sable's sub-level entity tracking. EuropaMine is
 * Forge 1.20.1, where that 1.21.1 NeoForge stack cannot be used directly, so the local player is
 * advanced by the same moving reference-frame delta as the interpolated hull. The server performs
 * the same frame transform. There is intentionally no camera rotation here: the player's look
 * remains player-controlled, avoiding the old server/client fight that produced visible shaking.
 */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SubmarineClientMotion {
    private SubmarineClientMotion() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isPassenger() || player.isSpectator()) return;

        // The carrier itself has a small EntityType box, therefore search generously around the
        // player and then use the contraption's real transformed local bounds for the final test.
        List<SubmarineContraptionEntity> candidates = minecraft.level.getEntitiesOfClass(
                SubmarineContraptionEntity.class, player.getBoundingBox().inflate(192.0D),
                ship -> ship.isAlive() && ship.canCarryClient(player.position()));
        if (candidates.isEmpty()) return;

        SubmarineContraptionEntity ship = candidates.stream()
                .min(Comparator.comparingDouble(s -> s.distanceToSqr(player)))
                .orElse(null);
        if (ship == null) return;

        Vec3 frameDelta = ship.clientCarryDelta(player.position());
        if (frameDelta.lengthSqr() > 1.0E-10D) {
            player.move(MoverType.SELF, frameDelta);
        }

        if (player.getDeltaMovement().y <= 0.0D) {
            Vec3 correction = ship.clientDeckCorrection(player.position());
            if (correction.lengthSqr() > 1.0E-10D) {
                player.move(MoverType.SELF, correction);
                player.setOnGround(true);
                player.fallDistance = 0.0F;
            }
        }

        // Same ownership model as Create's moving contraptions: the local client resolves its own
        // movement against the moving frame and only mirrors motion/onGround to the server. The
        // vanilla player-position packet then carries the actual position, so there is no second
        // server-side teleport competing with this prediction.
        Vec3 motion = player.getDeltaMovement();
        ModNetworking.CHANNEL.sendToServer(new NavigationPackets.ServerboundSubmarinePlayerMotion(
                ship.getId(), (float)motion.x, (float)motion.y, (float)motion.z, player.onGround()));
    }
}
