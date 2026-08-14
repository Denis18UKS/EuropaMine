package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.entity.SubmarineContraptionEntity;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.NavigationPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Client-owned movement of a player inside/on a detached submarine.
 *
 * Vanilla collision cannot see snapshot blocks after the hull has been detached from the Level.
 * The player is therefore carried by the moving reference frame first and then his relative local
 * movement is resolved against the contraption's cached BlockState collision shapes.
 */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SubmarineClientMotion {
    private static UUID lastShip;
    private static Vec3 lastLocalFeet;

    private SubmarineClientMotion() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isPassenger() || player.isSpectator()) {
            resetTracking();
            return;
        }

        // The EntityType box is intentionally tiny; the real carrier bounds are tested by
        // canCarryClient after the broad phase search.
        List<SubmarineContraptionEntity> candidates = minecraft.level.getEntitiesOfClass(
                SubmarineContraptionEntity.class, player.getBoundingBox().inflate(192.0D),
                ship -> ship.isAlive() && ship.canCarryClient(player.position()));
        if (candidates.isEmpty()) {
            resetTracking();
            return;
        }

        SubmarineContraptionEntity ship = candidates.stream()
                .min(Comparator.comparingDouble(s -> s.distanceToSqr(player)))
                .orElse(null);
        if (ship == null) {
            resetTracking();
            return;
        }

        // First apply only the delta of the moving reference frame. This keeps the same local point
        // under the player while the carrier translates/rotates, without a server-side teleport.
        Vec3 frameDelta = ship.clientCarryDelta(player.position());
        if (frameDelta.lengthSqr() > 1.0E-10D) player.move(MoverType.SELF, frameDelta);

        UUID shipId = ship.getUUID();
        Vec3 desiredLocal = ship.worldToLocal(player.position(), ship.getX(), ship.getY(), ship.getZ(),
                ship.getYRot(), ship.getXRot());

        if (!shipId.equals(lastShip) || lastLocalFeet == null
                || desiredLocal.distanceToSqr(lastLocalFeet) > 16.0D) {
            // First frame after capture/reload: settle a player who started a few centimetres inside
            // the old deck, then establish the safe local reference point for swept collisions.
            Vec3 correction = ship.clientDeckCorrection(player.position());
            if (correction.lengthSqr() > 1.0E-10D) player.move(MoverType.SELF, correction);
            lastShip = shipId;
            lastLocalFeet = ship.worldToLocal(player.position(), ship.getX(), ship.getY(), ship.getZ(),
                    ship.getYRot(), ship.getXRot());
        } else {
            SubmarineCollisionResolver.Result collision = SubmarineCollisionResolver.resolve(
                    ship, lastLocalFeet, desiredLocal, player.getBbWidth(), player.getBbHeight());

            Vec3 resolvedWorld = ship.localToWorld(collision.feet(), ship.getX(), ship.getY(), ship.getZ(),
                    ship.getYRot(), ship.getXRot());
            Vec3 correction = resolvedWorld.subtract(player.position());
            if (correction.lengthSqr() > 1.0E-12D) player.move(MoverType.SELF, correction);

            // Stop only the velocity components that actually hit the submarine. This is the part
            // missing from the previous deck-only implementation: walls and ceilings now behave as
            // physical collision surfaces as well as the floor.
            Vec3 localVelocity = SubmarineContraptionEntity.inverseRotate(player.getDeltaMovement(),
                    ship.getYRot(), ship.getXRot());
            double vx = collision.blockedX() ? 0.0D : localVelocity.x;
            double vy = collision.blockedY() ? 0.0D : localVelocity.y;
            double vz = collision.blockedZ() ? 0.0D : localVelocity.z;
            player.setDeltaMovement(SubmarineContraptionEntity.rotateLocal(new Vec3(vx, vy, vz),
                    ship.getYRot(), ship.getXRot()));

            if (collision.grounded()) {
                player.setOnGround(true);
                player.fallDistance = 0.0F;
            }

            lastLocalFeet = ship.worldToLocal(player.position(), ship.getX(), ship.getY(), ship.getZ(),
                    ship.getYRot(), ship.getXRot());
        }

        Vec3 motion = player.getDeltaMovement();
        ModNetworking.CHANNEL.sendToServer(new NavigationPackets.ServerboundSubmarinePlayerMotion(
                ship.getId(), (float)motion.x, (float)motion.y, (float)motion.z, player.onGround()));
    }

    private static void resetTracking() {
        lastShip = null;
        lastLocalFeet = null;
    }
}
