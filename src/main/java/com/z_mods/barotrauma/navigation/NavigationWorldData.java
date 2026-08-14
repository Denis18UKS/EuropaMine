package com.z_mods.barotrauma.navigation;

import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.entity.SubmarineContraptionEntity;
import com.z_mods.barotrauma.network.NavigationPackets;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent navigation terminals, movable vessels and campaign/mission markers. */
public final class NavigationWorldData extends SavedData {
    public static final String NAVIGATION_GUI = "navigation_terminal";
    private static final String DATA_NAME = "barotrauma_navigation_world";
    private static final int MAX_STRUCTURE_VOLUME = 32_768;
    private static final int SONAR_RAYS = 96;
    private static final int SONAR_REFRESH_TICKS = 10;
    private static final double SONAR_STEP = 3.0D;
    private static final int STATUS_COLUMNS = 64;
    private static final int STATUS_ROWS = 24;
    private static final int TEMPLATE_COLUMNS = 48;
    private static final int TEMPLATE_ROWS = 18;
    private static final double MAX_FORWARD_SPEED = 0.18D;
    private static final double SPEED_ACCELERATION = 0.0075D;
    private static final double SPEED_BRAKE = 0.012D;
    private static final float MAX_PITCH = 70.0F;
    private static final float BASE_YAW_RATE = 3.0F;
    private static final float BASE_PITCH_RATE = 2.35F;
    private static final float BASE_YAW_ACCELERATION = 0.24F;
    private static final float BASE_PITCH_ACCELERATION = 0.20F;
    // Aeronautics/Sable lets inertia naturally limit angular surface speed. Our 1.20.1
    // implementation has no Sable rigid body, so cap angular velocity by hull radius instead.
    // This is especially important for long submarines: a tiny angular step at the pivot can
    // otherwise move a player at the bow several blocks in one tick.
    private static final double MAX_ROTATIONAL_SURFACE_SPEED = 0.16D;
    private static final float YAW_STIFFNESS = 0.040F;
    private static final float PITCH_STIFFNESS = 0.045F;
    private static final float ANGULAR_DAMPING = 0.31F;
    private static final double MANUAL_DEADZONE = 0.035D;
    private static final int AUTOPILOT_LOOKAHEAD = 7;
    private static final TagKey<EntityType<?>> SONAR_HOSTILE = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation("barotrauma", "sonar_hostile"));

    private final Map<Long, TerminalState> terminals = new HashMap<>();
    private final Map<UUID, VesselState> vessels = new HashMap<>();
    private final Map<UUID, NavigationTarget> targets = new HashMap<>();
    private final Map<Long, Alias> aliases = new HashMap<>();
    private long ticks;

    public static NavigationWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(NavigationWorldData::load, NavigationWorldData::new, DATA_NAME);
    }

    public TerminalState terminalOrCreate(BlockPos rawPos) {
        BlockPos pos = resolveTerminalPos(rawPos);
        return terminals.computeIfAbsent(pos.asLong(), ignored -> new TerminalState());
    }

    public BlockPos resolveTerminalPos(BlockPos pos) {
        long key = pos.asLong();
        Set<Long> visited = new HashSet<>();
        while (aliases.containsKey(key) && visited.add(key)) {
            key = aliases.get(key).destination;
        }
        return BlockPos.of(key);
    }

    public VesselState vessel(UUID id) {
        return id == null ? null : vessels.get(id);
    }

    public VesselState vesselContaining(BlockPos pos) {
        for (VesselState vessel : vessels.values()) {
            if (vessel.contains(pos)) return vessel;
        }
        return null;
    }

    public VesselState registerSingle(BlockPos pos, String name) {
        VesselState existing = vesselContaining(pos);
        if (existing != null && existing.mode == VesselMode.SINGLE_BLOCK) return existing;
        VesselState vessel = new VesselState(UUID.randomUUID(), VesselMode.SINGLE_BLOCK, pos, pos,
                name == null || name.isBlank() ? "Одиночный управляемый блок" : name);
        vessels.put(vessel.id, vessel);
        setDirty();
        return vessel;
    }

    public VesselState registerMultiblock(BlockPos first, BlockPos second, String name) {
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume < 1 || volume > MAX_STRUCTURE_VOLUME) return null;
        VesselState vessel = new VesselState(UUID.randomUUID(), VesselMode.MULTIBLOCK, min, max,
                name == null || name.isBlank() ? "Подлодка" : name);
        vessels.put(vessel.id, vessel);
        setDirty();
        return vessel;
    }

    public boolean link(BlockPos terminalPos, UUID vesselId) {
        VesselState vessel = vessels.get(vesselId);
        if (vessel == null) return false;
        TerminalState terminal = terminalOrCreate(terminalPos);
        terminal.vesselId = vesselId;
        terminal.maintainPos = vessel.anchor();
        setDirty();
        return true;
    }

    public NavigationTarget addTarget(NavigationReferenceData.TargetType type,
                                      NavigationReferenceData.MissionType mission,
                                      BlockPos pos, String displayName) {
        NavigationTarget target = new NavigationTarget(UUID.randomUUID(), type, mission, pos,
                displayName == null || displayName.isBlank() ? type.russianName() : displayName);
        targets.put(target.id, target);
        setDirty();
        return target;
    }

    public boolean removeTarget(UUID id) {
        boolean removed = targets.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    public List<NavigationTarget> allTargets() {
        return targets.values().stream()
                .sorted(Comparator.comparing(target -> target.displayName.toLowerCase()))
                .toList();
    }

    public void tick(ServerLevel level) {
        ticks++;
        aliases.entrySet().removeIf(entry -> entry.getValue().expiresAt < ticks);

        boolean changed = false;
        Set<UUID> processed = new HashSet<>();
        for (Map.Entry<Long, TerminalState> entry : new ArrayList<>(terminals.entrySet())) {
            BlockPos terminalPos = BlockPos.of(entry.getKey());
            TerminalState terminal = entry.getValue();
            VesselState vessel = vessels.get(terminal.vesselId);
            if (vessel == null || !processed.add(vessel.id)) continue;

            SubmarineContraptionEntity contraption = getContraption(level, vessel);
            boolean powered = contraption != null ? vessel.detachedPower : NavigationSystem.hasPower(level, terminalPos);
            MotionCommand command = desiredCommand(level, terminal, vessel, contraption);
            double targetSpeed = powered ? command.speed : 0.0D;

            // Do not detach a stationary vessel merely because its terminal is open. Once motion or
            // a real turn is requested, capture the complete structure exactly once.
            if (contraption == null && powered
                    && (targetSpeed > MANUAL_DEADZONE
                    || Math.abs(angleDifference(vessel.yaw, command.yaw)) > 0.25F
                    || Math.abs(vessel.pitch - command.pitch) > 0.25F)) {
                contraption = activateContraption(level, vessel, terminalPos);
                if (contraption == null) {
                    vessel.speed = 0.0D;
                    continue;
                }
                changed = true;
            }

            if (contraption == null) {
                vessel.speed = approachLinear(vessel.speed, 0.0D, SPEED_BRAKE);
                continue;
            }

            // Simulated/Aeronautics does not directly overwrite a sub-level pose from controls.
            // Controls create forces/torques on a rigid body. Reproduce that behavior here with a
            // small critically-damped controller: angular velocity is integrated first, then the
            // linear velocity chases the hull's *current* forward direction. This removes the old
            // set-angle/set-position cadence that was visible as a periodic jerk.
            double yawRadius = Math.max(1.0D, Math.hypot(vessel.sizeX(), vessel.sizeZ()) * 0.5D);
            double pitchRadius = Math.max(1.0D, Math.hypot(vessel.sizeX(), vessel.sizeY()) * 0.5D);
            float maxYawRate = angularRateLimit(BASE_YAW_RATE, yawRadius);
            float maxPitchRate = angularRateLimit(BASE_PITCH_RATE, pitchRadius);
            float yawError = powered ? angleDifference(vessel.yaw, command.yaw) : 0.0F;
            float pitchTarget = powered ? Mth.clamp(command.pitch, -MAX_PITCH, MAX_PITCH) : vessel.pitch;
            float pitchError = pitchTarget - vessel.pitch;

            float yawAccelLimit = Math.min(BASE_YAW_ACCELERATION, Math.max(0.025F, maxYawRate * 0.20F));
            float pitchAccelLimit = Math.min(BASE_PITCH_ACCELERATION, Math.max(0.020F, maxPitchRate * 0.20F));
            float yawAccel = Mth.clamp(yawError * YAW_STIFFNESS - vessel.yawVelocity * ANGULAR_DAMPING,
                    -yawAccelLimit, yawAccelLimit);
            float pitchAccel = Mth.clamp(pitchError * PITCH_STIFFNESS - vessel.pitchVelocity * ANGULAR_DAMPING,
                    -pitchAccelLimit, pitchAccelLimit);
            vessel.yawVelocity = Mth.clamp(vessel.yawVelocity + yawAccel, -maxYawRate, maxYawRate);
            vessel.pitchVelocity = Mth.clamp(vessel.pitchVelocity + pitchAccel, -maxPitchRate, maxPitchRate);

            if (Math.abs(yawError) < 0.08F && Math.abs(vessel.yawVelocity) < 0.08F) vessel.yawVelocity = 0.0F;
            if (Math.abs(pitchError) < 0.08F && Math.abs(vessel.pitchVelocity) < 0.08F) vessel.pitchVelocity = 0.0F;

            float nextYaw = wrapDegrees(vessel.yaw + vessel.yawVelocity);
            float nextPitch = Mth.clamp(vessel.pitch + vessel.pitchVelocity, -MAX_PITCH, MAX_PITCH);
            if (nextPitch <= -MAX_PITCH + 0.001F || nextPitch >= MAX_PITCH - 0.001F) {
                vessel.pitchVelocity *= 0.35F;
            }

            Vec3 currentVelocity = new Vec3(vessel.velocityX, vessel.velocityY, vessel.velocityZ);
            Vec3 forward = SubmarineContraptionEntity.forward(nextYaw, nextPitch);
            Vec3 desiredVelocity = forward.scale(targetSpeed);
            Vec3 velocityError = desiredVelocity.subtract(currentVelocity);
            double acceleration = desiredVelocity.lengthSqr() < currentVelocity.lengthSqr()
                    ? SPEED_BRAKE : SPEED_ACCELERATION;
            if (velocityError.lengthSqr() > acceleration * acceleration) {
                velocityError = velocityError.normalize().scale(acceleration);
            }
            Vec3 nextVelocity = currentVelocity.add(velocityError);
            if (targetSpeed <= MANUAL_DEADZONE && nextVelocity.lengthSqr() < 1.0E-5D) nextVelocity = Vec3.ZERO;

            double nextX = vessel.posX + nextVelocity.x;
            double nextY = vessel.posY + nextVelocity.y;
            double nextZ = vessel.posZ + nextVelocity.z;

            // Rotation can make bow/stern sweep into an obstacle even with little translation.
            if (!isTransformClear(level, contraption, nextX, nextY, nextZ, nextYaw, nextPitch)) {
                vessel.velocityX *= 0.20D;
                vessel.velocityY *= 0.20D;
                vessel.velocityZ *= 0.20D;
                vessel.speed = Math.sqrt(vessel.velocityX * vessel.velocityX
                        + vessel.velocityY * vessel.velocityY + vessel.velocityZ * vessel.velocityZ);

                // A rigid body may still rotate away from an obstacle if the swept pose is clear.
                if (isTransformClear(level, contraption, vessel.posX, vessel.posY, vessel.posZ, nextYaw, nextPitch)) {
                    moveCarriedEntities(level, vessel, contraption, vessel.posX, vessel.posY, vessel.posZ,
                            vessel.yaw, vessel.pitch, vessel.posX, vessel.posY, vessel.posZ, nextYaw, nextPitch);
                    vessel.yaw = nextYaw;
                    vessel.pitch = nextPitch;
                    contraption.setYRot(nextYaw);
                    contraption.setXRot(nextPitch);
                } else {
                    vessel.yawVelocity *= 0.35F;
                    vessel.pitchVelocity *= 0.35F;
                }
                continue;
            }

            double oldX = vessel.posX, oldY = vessel.posY, oldZ = vessel.posZ;
            float oldYaw = vessel.yaw, oldPitch = vessel.pitch;
            moveCarriedEntities(level, vessel, contraption, oldX, oldY, oldZ, oldYaw, oldPitch,
                    nextX, nextY, nextZ, nextYaw, nextPitch);

            vessel.posX = nextX;
            vessel.posY = nextY;
            vessel.posZ = nextZ;
            vessel.yaw = nextYaw;
            vessel.pitch = nextPitch;
            vessel.velocityX = nextVelocity.x;
            vessel.velocityY = nextVelocity.y;
            vessel.velocityZ = nextVelocity.z;
            vessel.speed = nextVelocity.length();
            vessel.lastMoveTick = ticks;
            contraption.setPos(nextX, nextY, nextZ);
            contraption.setYRot(nextYaw);
            contraption.setXRot(nextPitch);
            updateVirtualTerminals(vessel, contraption);
            changed = true;
        }

        if (ticks % 20L == 0L || changed) setDirty();
    }

    private static double approachLinear(double current, double target, double step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return target;
    }

    private static float approachLinear(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return target;
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float delta = angleDifference(current, target);
        if (Math.abs(delta) <= maxStep) return wrapDegrees(target);
        return wrapDegrees(current + Math.copySign(maxStep, delta));
    }

    private static float angleDifference(float from, float to) {
        return Mth.wrapDegrees(to - from);
    }

    private static float angularRateLimit(float configuredRate, double radius) {
        double bySurfaceSpeed = Math.toDegrees(MAX_ROTATIONAL_SURFACE_SPEED / Math.max(1.0D, radius));
        return (float)Math.max(0.12D, Math.min(configuredRate, bySurfaceSpeed));
    }

    private static float wrapDegrees(float value) {
        return Mth.wrapDegrees(value);
    }

    private MotionCommand desiredCommand(ServerLevel level, TerminalState terminal, VesselState vessel,
                                         SubmarineContraptionEntity contraption) {
        if (!terminal.autopilot) {
            terminal.clearAvoidance();
            float yaw = terminal.manualHeadingSet ? terminal.manualTargetYaw : vessel.yaw;
            float pitch = terminal.manualPitchSet ? terminal.manualTargetPitch : vessel.pitch;
            return new MotionCommand(Mth.clamp(terminal.manualThrottle, 0.0F, 1.0F) * MAX_FORWARD_SPEED,
                    yaw, pitch);
        }

        Vec3 destination = null;
        if (terminal.selectedDestination == 0) {
            if (terminal.maintainPos == null) terminal.maintainPos = vessel.anchor();
            destination = Vec3.atCenterOf(terminal.maintainPos);
        } else {
            List<NavigationTarget> targetList = relevantTargets(level, vessel.anchor());
            int index = terminal.selectedDestination - 1;
            if (index >= 0 && index < targetList.size()) destination = Vec3.atCenterOf(targetList.get(index).pos);
        }
        if (destination == null) return new MotionCommand(0.0D, vessel.yaw, vessel.pitch);

        Vec3 center = new Vec3(vessel.posX, vessel.posY, vessel.posZ);
        Vec3 difference = destination.subtract(center);
        double distance = difference.length();
        if (distance < 1.25D) {
            terminal.clearAvoidance();
            return new MotionCommand(0.0D, vessel.yaw, vessel.pitch);
        }

        Vec3 desiredDirection = difference.normalize();
        float desiredYaw = yawFromDirection(desiredDirection);
        float desiredPitch = pitchFromDirection(desiredDirection);

        // A Create-like carrier follows a curved course. Test a family of yaw/pitch headings and
        // select the clear one that still makes the most progress towards the destination.
        if (contraption != null) {
            HeadingCandidate candidate = chooseHeading(level, contraption, vessel, desiredDirection, desiredYaw, desiredPitch);
            if (candidate != null) {
                desiredYaw = candidate.yaw;
                desiredPitch = candidate.pitch;
            } else {
                return new MotionCommand(0.0D, vessel.yaw, vessel.pitch);
            }
        }

        double slowdown = Mth.clamp(distance / 18.0D, 0.18D, 1.0D);
        return new MotionCommand(MAX_FORWARD_SPEED * slowdown, desiredYaw, desiredPitch);
    }

    private HeadingCandidate chooseHeading(ServerLevel level, SubmarineContraptionEntity contraption,
                                             VesselState vessel, Vec3 desiredDirection,
                                             float desiredYaw, float desiredPitch) {
        float[] yawOffsets = {0, -18, 18, -35, 35, -60, 60, -90, 90};
        float[] pitchOffsets = {0, 14, -14, 28, -28, 42, -42};
        HeadingCandidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (float yawOffset : yawOffsets) {
            for (float pitchOffset : pitchOffsets) {
                float yaw = wrapDegrees(desiredYaw + yawOffset);
                float pitch = Mth.clamp(desiredPitch + pitchOffset, -MAX_PITCH, MAX_PITCH);
                Vec3 direction = SubmarineContraptionEntity.forward(yaw, pitch);
                if (!corridorClear(level, contraption, vessel, direction, yaw, pitch, AUTOPILOT_LOOKAHEAD)) continue;
                double progress = direction.dot(desiredDirection);
                double turnPenalty = Math.abs(yawOffset) * 0.0025D + Math.abs(pitchOffset) * 0.0035D;
                double score = progress - turnPenalty;
                if (score > bestScore) {
                    bestScore = score;
                    best = new HeadingCandidate(yaw, pitch);
                }
            }
        }
        return best;
    }

    private boolean corridorClear(ServerLevel level, SubmarineContraptionEntity contraption,
                                  VesselState vessel, Vec3 direction, float yaw, float pitch, int blocks) {
        for (int i = 1; i <= blocks; i++) {
            double distance = i * 1.0D;
            if (!isTransformClear(level, contraption, vessel.posX + direction.x * distance,
                    vessel.posY + direction.y * distance, vessel.posZ + direction.z * distance, yaw, pitch)) return false;
        }
        return true;
    }

    private static float yawFromDirection(Vec3 direction) {
        return wrapDegrees((float)Math.toDegrees(Math.atan2(direction.z, direction.x)));
    }

    private static float pitchFromDirection(Vec3 direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return Mth.clamp((float)Math.toDegrees(Math.atan2(direction.y, horizontal)), -MAX_PITCH, MAX_PITCH);
    }

    private record MotionCommand(double speed, float yaw, float pitch) {}
    private record HeadingCandidate(float yaw, float pitch) {}

    private SubmarineContraptionEntity getContraption(ServerLevel level, VesselState vessel) {
        if (vessel.contraptionEntity == null) return null;
        Entity entity = level.getEntity(vessel.contraptionEntity);
        if (entity instanceof SubmarineContraptionEntity submarine && submarine.isAlive()) return submarine;
        return null;
    }

    private SubmarineContraptionEntity activateContraption(ServerLevel level, VesselState vessel, BlockPos terminalPos) {
        BlockPos oldMin = vessel.min;
        BlockPos oldMax = vessel.max;
        Direction initialFacing = facingAt(level, terminalPos);
        boolean power = NavigationSystem.hasPower(level, terminalPos);
        SubmarineContraptionEntity contraption = SubmarineContraptionEntity.capture(level, vessel.id, oldMin, oldMax);
        if (contraption == null) return null;

        vessel.contraptionEntity = contraption.getUUID();
        vessel.detached = true;
        vessel.detachedPower = power;
        vessel.posX = contraption.getX();
        vessel.posY = contraption.getY();
        vessel.posZ = contraption.getZ();
        vessel.yaw = yawForDirection(initialFacing);
        vessel.pitch = 0.0F;
        vessel.speed = 0.0D;
        vessel.velocityX = 0.0D;
        vessel.velocityY = 0.0D;
        vessel.velocityZ = 0.0D;
        vessel.yawVelocity = 0.0F;
        vessel.pitchVelocity = 0.0F;
        contraption.setYRot(vessel.yaw);
        contraption.setXRot(vessel.pitch);
        vessel.hullCache = null;
        vessel.hullCacheTick = Long.MIN_VALUE;

        for (Map.Entry<Long, TerminalState> entry : terminals.entrySet()) {
            TerminalState terminal = entry.getValue();
            if (!vessel.id.equals(terminal.vesselId)) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!inside(oldMin, oldMax, pos)) continue;
            terminal.virtualTerminal = true;
            terminal.virtualLocalX = pos.getX() + 0.5D - vessel.posX;
            terminal.virtualLocalY = pos.getY() + 0.5D - vessel.posY;
            terminal.virtualLocalZ = pos.getZ() + 0.5D - vessel.posZ;
            terminal.currentTerminalPos = pos;
            if (!terminal.manualHeadingSet) terminal.manualTargetYaw = vessel.yaw;
            if (!terminal.manualPitchSet) terminal.manualTargetPitch = vessel.pitch;
        }
        updateVirtualTerminals(vessel, contraption);
        return contraption;
    }

    private static boolean inside(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static float yawForDirection(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0F;
            case WEST -> 180.0F;
            case NORTH -> -90.0F;
            default -> 0.0F;
        };
    }

    private boolean isTransformClear(ServerLevel level, SubmarineContraptionEntity contraption,
                                     double x, double y, double z, float yaw, float pitch) {
        AABB bounds = contraption.transformedBounds(x, y, z, yaw, pitch).deflate(0.03D);
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        if (!level.isInWorldBounds(min) || !level.isInWorldBounds(max)) return false;
        // Never steer an active contraption into an unloaded area. Checking all four horizontal
        // corners catches a multi-chunk hull without forcing chunk generation from the navigation tick.
        if (!level.hasChunkAt(min) || !level.hasChunkAt(max)
                || !level.hasChunkAt(new BlockPos(min.getX(), min.getY(), max.getZ()))
                || !level.hasChunkAt(new BlockPos(max.getX(), min.getY(), min.getZ()))) return false;
        return !level.getBlockCollisions(contraption, bounds).iterator().hasNext();
    }

    private void moveCarriedEntities(ServerLevel level, VesselState vessel,
                                     SubmarineContraptionEntity contraption,
                                     double oldX, double oldY, double oldZ, float oldYaw, float oldPitch,
                                     double newX, double newY, double newZ, float newYaw, float newPitch) {
        AABB oldBounds = contraption.transformedBounds(oldX, oldY, oldZ, oldYaw, oldPitch);
        AABB newBounds = contraption.transformedBounds(newX, newY, newZ, newYaw, newPitch);
        AABB search = new AABB(Math.min(oldBounds.minX, newBounds.minX), Math.min(oldBounds.minY, newBounds.minY),
                Math.min(oldBounds.minZ, newBounds.minZ), Math.max(oldBounds.maxX, newBounds.maxX),
                Math.max(oldBounds.maxY, newBounds.maxY), Math.max(oldBounds.maxZ, newBounds.maxZ)).inflate(1.25D);
        AABB localBounds = contraption.localBounds().inflate(0.9D, 1.4D, 0.9D);
        List<Entity> carried = level.getEntities((Entity)null, search, entity -> entity.isAlive()
                && entity != contraption
                && !(entity instanceof net.minecraft.world.entity.decoration.HangingEntity)
                && !entity.isPassenger());
        for (Entity entity : carried) {
            // Create's ContraptionCollider deliberately leaves the local player to the client and
            // only mirrors its resulting motion to the server. Moving ServerPlayer here as well
            // makes two independent simulations fight over the same position and causes rubberbanding.
            if (entity instanceof ServerPlayer) continue;

            Vec3 local = contraption.worldToLocal(entity.position(), oldX, oldY, oldZ, oldYaw, oldPitch);
            if (!localBounds.contains(local)) continue;

            boolean supported = false;
            if (entity instanceof LivingEntity && entity.getDeltaMovement().y <= 0.0D) {
                double support = contraption.supportHeight(local.x, local.y, local.z);
                if (!Double.isNaN(support) && local.y < support + 0.20D && local.y > support - 0.70D) {
                    local = new Vec3(local.x, support + 0.002D, local.z);
                    supported = true;
                }
            }

            Vec3 target = contraption.localToWorld(local, newX, newY, newZ, newYaw, newPitch);
            Vec3 delta = target.subtract(entity.position());
            if (delta.lengthSqr() > 1.0E-10D) {
                // Use Minecraft's normal movement/collision path instead of setPos/teleport. This is
                // the important moving-reference-frame change: input remains additive and vanilla
                // collision correction can participate instead of fighting an absolute teleport.
                entity.move(MoverType.SELF, delta);
            }

            if (supported) {
                entity.setOnGround(true);
                entity.fallDistance = 0.0F;
                Vec3 velocity = entity.getDeltaMovement();
                if (velocity.y < 0.0D) entity.setDeltaMovement(velocity.x, 0.0D, velocity.z);
            }

            // Deliberately do NOT rotate player/entity look direction here. Sable keeps entities in
            // the moving sub-level while their own camera/input orientation remains independent.
            // The old absolute yaw/pitch correction was one of the sources of visible tug-of-war.
        }
    }

    private void updateVirtualTerminals(VesselState vessel, SubmarineContraptionEntity contraption) {
        Map<Long, TerminalState> updated = new HashMap<>();
        for (Map.Entry<Long, TerminalState> entry : terminals.entrySet()) {
            TerminalState terminal = entry.getValue();
            if (!vessel.id.equals(terminal.vesselId) || !terminal.virtualTerminal) {
                updated.put(entry.getKey(), terminal);
                continue;
            }
            BlockPos oldPos = BlockPos.of(entry.getKey());
            Vec3 local = new Vec3(terminal.virtualLocalX, terminal.virtualLocalY, terminal.virtualLocalZ);
            Vec3 world = contraption.localToWorld(local, vessel.posX, vessel.posY, vessel.posZ, vessel.yaw, vessel.pitch);
            BlockPos newPos = BlockPos.containing(world);
            terminal.currentTerminalPos = newPos;
            updated.put(newPos.asLong(), terminal);
            if (!oldPos.equals(newPos)) aliases.put(oldPos.asLong(), new Alias(newPos.asLong(), ticks + 1_200L));
        }
        terminals.clear();
        terminals.putAll(updated);
    }

    public boolean isVirtualNavigationTerminal(BlockPos rawPos) {
        BlockPos pos = resolveTerminalPos(rawPos);
        TerminalState terminal = terminals.get(pos.asLong());
        return terminal != null && terminal.virtualTerminal && terminal.vesselId != null;
    }

    public boolean virtualTerminalPowered(BlockPos rawPos) {
        BlockPos pos = resolveTerminalPos(rawPos);
        TerminalState terminal = terminals.get(pos.asLong());
        VesselState vessel = terminal == null ? null : vessels.get(terminal.vesselId);
        return vessel != null && vessel.detached && vessel.detachedPower;
    }

    private int[] chooseDetour(ServerLevel level, VesselState vessel, BlockPos destination,
                                int directX, int directY, int directZ) {
        BlockPos anchor = vessel.anchor();
        int[] best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (dx == directX && dy == directY && dz == directZ) continue;
                    if (!isCorridorClear(level, vessel, dx, dy, dz, AUTOPILOT_LOOKAHEAD)) continue;

                    double progress = dx * directX + dy * directY + dz * directZ;
                    BlockPos projected = anchor.offset(dx * AUTOPILOT_LOOKAHEAD,
                            dy * AUTOPILOT_LOOKAHEAD, dz * AUTOPILOT_LOOKAHEAD);
                    double score = projected.distSqr(destination);
                    if (progress < 0.0D) score += 400.0D;
                    if (dy != 0) score += 5.0D; // Prefer a horizontal bypass when equally safe.
                    if (dx != 0 && dz != 0) score += 1.5D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new int[]{dx, dy, dz};
                    }
                }
            }
        }
        return best;
    }

    private Vec3 worldStepToLocal(int dx, int dy, int dz, Direction facing, Direction right) {
        double forward = dx * facing.getStepX() + dz * facing.getStepZ();
        double lateral = dx * right.getStepX() + dz * right.getStepZ();
        double maximum = Math.max(1.0D, Math.max(Math.abs(forward), Math.max(Math.abs(dy), Math.abs(lateral))));
        return new Vec3(forward / maximum, dy / maximum, lateral / maximum);
    }

    private boolean isCorridorClear(ServerLevel level, VesselState vessel,
                                    int dx, int dy, int dz, int distance) {
        if (dx == 0 && dy == 0 && dz == 0) return true;
        int offsetX = 0;
        int offsetY = 0;
        int offsetZ = 0;
        for (int step = 0; step < distance; step++) {
            if (!isStepClear(level, vessel, offsetX, offsetY, offsetZ, dx, dy, dz)) return false;
            offsetX += dx;
            offsetY += dy;
            offsetZ += dz;
        }
        return true;
    }

    private boolean isStepClear(ServerLevel level, VesselState vessel,
                                int offsetX, int offsetY, int offsetZ,
                                int dx, int dy, int dz) {
        BlockPos oldMin = vessel.min.offset(offsetX, offsetY, offsetZ);
        BlockPos oldMax = vessel.max.offset(offsetX, offsetY, offsetZ);
        BlockPos newMin = oldMin.offset(dx, dy, dz);
        BlockPos newMax = oldMax.offset(dx, dy, dz);
        Set<Long> checked = new HashSet<>();

        if (dx != 0) {
            int x = dx > 0 ? newMax.getX() : newMin.getX();
            for (int y = newMin.getY(); y <= newMax.getY(); y++) {
                for (int z = newMin.getZ(); z <= newMax.getZ(); z++) {
                    if (!isDestinationFree(level, vessel, new BlockPos(x, y, z), checked)) return false;
                }
            }
        }
        if (dy != 0) {
            int y = dy > 0 ? newMax.getY() : newMin.getY();
            for (int x = newMin.getX(); x <= newMax.getX(); x++) {
                for (int z = newMin.getZ(); z <= newMax.getZ(); z++) {
                    if (!isDestinationFree(level, vessel, new BlockPos(x, y, z), checked)) return false;
                }
            }
        }
        if (dz != 0) {
            int z = dz > 0 ? newMax.getZ() : newMin.getZ();
            for (int x = newMin.getX(); x <= newMax.getX(); x++) {
                for (int y = newMin.getY(); y <= newMax.getY(); y++) {
                    if (!isDestinationFree(level, vessel, new BlockPos(x, y, z), checked)) return false;
                }
            }
        }
        return true;
    }

    private boolean isDestinationFree(ServerLevel level, VesselState vessel, BlockPos pos, Set<Long> checked) {
        if (!checked.add(pos.asLong()) || vessel.contains(pos)) return true;
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced();
    }

    private Direction facingAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.EAST;
    }

    private boolean translateVessel(ServerLevel level, VesselState vessel, int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return true;
        BlockPos oldMin = vessel.min;
        BlockPos oldMax = vessel.max;
        List<BlockSnapshot> snapshots = new ArrayList<>();
        Set<Long> sourcePositions = new HashSet<>();

        for (BlockPos cursor : BlockPos.betweenClosed(oldMin, oldMax)) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) continue;
            BlockPos immutable = cursor.immutable();
            sourcePositions.add(immutable.asLong());
            CompoundTag blockEntityTag = null;
            BlockEntity blockEntity = level.getBlockEntity(immutable);
            if (blockEntity != null) blockEntityTag = blockEntity.saveWithFullMetadata();
            snapshots.add(new BlockSnapshot(immutable, state, blockEntityTag));
        }
        if (snapshots.isEmpty()) return false;

        for (BlockSnapshot snapshot : snapshots) {
            BlockPos destination = snapshot.pos.offset(dx, dy, dz);
            if (sourcePositions.contains(destination.asLong())) continue;
            BlockState destinationState = level.getBlockState(destination);
            if (!destinationState.isAir() && destinationState.getFluidState().isEmpty() && !destinationState.canBeReplaced()) {
                return false;
            }
        }

        for (BlockSnapshot snapshot : snapshots) level.setBlock(snapshot.pos, Blocks.AIR.defaultBlockState(), 18);
        for (BlockSnapshot snapshot : snapshots) {
            BlockPos destination = snapshot.pos.offset(dx, dy, dz);
            level.setBlock(destination, snapshot.state, 18);
            if (snapshot.blockEntityTag != null) {
                BlockEntity newEntity = level.getBlockEntity(destination);
                if (newEntity != null) {
                    CompoundTag movedTag = snapshot.blockEntityTag.copy();
                    movedTag.putInt("x", destination.getX());
                    movedTag.putInt("y", destination.getY());
                    movedTag.putInt("z", destination.getZ());
                    newEntity.load(movedTag);
                    newEntity.setChanged();
                }
            }
        }

        PowerWorldData.get(level).moveRegion(oldMin, oldMax, dx, dy, dz);
        vessel.min = oldMin.offset(dx, dy, dz);
        vessel.max = oldMax.offset(dx, dy, dz);
        vessel.hullCache = null;
        vessel.hullCacheTick = Long.MIN_VALUE;
        return true;
    }


    private void translateTerminalsAndAliases(VesselState vessel, int dx, int dy, int dz) {
        Map<Long, TerminalState> translated = new HashMap<>();
        for (Map.Entry<Long, TerminalState> entry : terminals.entrySet()) {
            BlockPos oldPos = BlockPos.of(entry.getKey());
            TerminalState terminal = entry.getValue();
            if (vessel.id.equals(terminal.vesselId) && containsBeforeMove(vessel, oldPos, dx, dy, dz)) {
                BlockPos newPos = oldPos.offset(dx, dy, dz);
                terminal.currentTerminalPos = newPos;
                translated.put(newPos.asLong(), terminal);
                aliases.put(oldPos.asLong(), new Alias(newPos.asLong(), ticks + 1_200L));
            } else {
                translated.put(entry.getKey(), terminal);
            }
        }
        terminals.clear();
        terminals.putAll(translated);
    }

    private boolean containsBeforeMove(VesselState vessel, BlockPos pos, int dx, int dy, int dz) {
        BlockPos oldMin = vessel.min.offset(-dx, -dy, -dz);
        BlockPos oldMax = vessel.max.offset(-dx, -dy, -dz);
        return pos.getX() >= oldMin.getX() && pos.getX() <= oldMax.getX()
                && pos.getY() >= oldMin.getY() && pos.getY() <= oldMax.getY()
                && pos.getZ() >= oldMin.getZ() && pos.getZ() <= oldMax.getZ();
    }

    public CompoundTag stateTag(ServerLevel level, BlockPos requestedTerminalPos) {
        BlockPos terminalPos = resolveTerminalPos(requestedTerminalPos);
        TerminalState terminal = terminalOrCreate(terminalPos);
        terminal.currentTerminalPos = terminalPos;
        VesselState vessel = vessels.get(terminal.vesselId);

        CompoundTag tag = new CompoundTag();
        tag.putLong("TerminalPos", terminalPos.asLong());
        tag.putBoolean("Powered", vessel != null && vessel.detached
                ? vessel.detachedPower : NavigationSystem.hasPower(level, terminalPos));
        tag.putBoolean("ActiveSonar", terminal.activeSonar);
        tag.putBoolean("Directional", terminal.directional);
        tag.putBoolean("Autopilot", terminal.autopilot);
        tag.putInt("Zoom", terminal.zoom);
        tag.putInt("SelectedDestination", terminal.selectedDestination);
        tag.putFloat("ManualForward", terminal.manualForward);
        tag.putFloat("ManualVertical", terminal.manualVertical);
        tag.putFloat("ManualTargetYaw", terminal.manualTargetYaw);
        tag.putFloat("ManualTargetPitch", terminal.manualTargetPitch);
        tag.putFloat("ManualThrottle", terminal.manualThrottle);
        tag.putFloat("BeamAngle", terminal.beamAngle);
        tag.putBoolean("TemplateMode", terminal.templateMode);
        tag.putByteArray("SectionActions", terminal.sectionActions.clone());

        if (vessel == null) {
            tag.putBoolean("Linked", false);
            tag.putInt("Depth", Math.max(0, level.getSeaLevel() - terminalPos.getY()));
            tag.putIntArray("Sonar", new int[SONAR_RAYS]);
            tag.put("Contacts", new ListTag());
            tag.put("HandSonars", new ListTag());
            if (terminal.templateMode) {
                tag.putByteArray("HullGrid", templateHullGrid());
                tag.putInt("HullColumns", TEMPLATE_COLUMNS);
                tag.putInt("HullRows", TEMPLATE_ROWS);
            } else {
                tag.putByteArray("HullGrid", new byte[32 * 12]);
                tag.putInt("HullColumns", 32);
                tag.putInt("HullRows", 12);
            }
            putTargets(level, tag, terminal, terminalPos);
            return tag;
        }

        tag.putBoolean("Linked", true);
        tag.putUUID("Vessel", vessel.id);
        tag.putString("VesselName", vessel.name);
        tag.putString("VesselMode", vessel.mode.name());
        tag.putLong("VesselMin", vessel.min.asLong());
        tag.putLong("VesselMax", vessel.max.asLong());
        BlockPos anchor = vessel.anchor();
        tag.putLong("Anchor", anchor.asLong());
        tag.putDouble("AnchorX", vessel.posX);
        tag.putDouble("AnchorY", vessel.posY);
        tag.putDouble("AnchorZ", vessel.posZ);
        tag.putFloat("Yaw", vessel.yaw);
        tag.putFloat("Pitch", vessel.pitch);
        tag.putDouble("ThrottleSpeed", vessel.speed);
        Vec3 vesselForward = SubmarineContraptionEntity.forward(vessel.yaw, vessel.pitch).scale(vessel.speed);
        double horizontalSpeed = Math.sqrt(vesselForward.x * vesselForward.x + vesselForward.z * vesselForward.z);
        tag.putDouble("ForwardSpeedKmh", horizontalSpeed * 20.0D * 3.6D);
        tag.putDouble("VerticalSpeedKmh", -vesselForward.y * 20.0D * 3.6D);
        tag.putDouble("LateralSpeedKmh", 0.0D);
        tag.putInt("Depth", Math.max(0, Mth.floor(level.getSeaLevel() - vessel.posY)));
        tag.putBoolean("Docked", vessel.origin.distSqr(anchor) < 16.0D && vessel.speed < 0.005D);
        SonarSnapshot sonar = sonarSnapshot(level, terminalPos, terminal, vessel);
        tag.putIntArray("Sonar", sonar.obstacles);
        putSonarContacts(tag, sonar.contacts);
        putHandSonars(tag, sonar.handSonars);
        Direction.Axis hullAxis = vessel.detached ? Direction.Axis.X : facingAt(level, terminalPos).getAxis();
        tag.putByteArray("HullGrid", buildHullGridCached(level, terminalPos, vessel));
        tag.putInt("HullColumns", hullColumns(vessel, hullAxis));
        tag.putInt("HullRows", hullRows(vessel));
        putCrew(level, tag, terminalPos, vessel);
        putTargets(level, tag, terminal, anchor);
        return tag;
    }

    private SonarSnapshot sonarSnapshot(ServerLevel level, BlockPos terminalPos,
                                          TerminalState terminal, VesselState vessel) {
        boolean settingsChanged = terminal.sonarCache == null
                || terminal.sonarCacheZoom != terminal.zoom
                || terminal.sonarCacheActive != terminal.activeSonar
                || terminal.sonarCacheDirectional != terminal.directional
                || Math.abs(terminal.sonarCacheBeam - terminal.beamAngle) > 0.01F;
        if (!settingsChanged && ticks - terminal.sonarCacheTick < SONAR_REFRESH_TICKS) {
            return terminal.sonarCache;
        }

        double range = Mth.lerp(terminal.zoom / 100.0D, 220.0D, 72.0D);
        int[] obstacles = terminal.activeSonar
                ? scanSonarObstacles(level, terminal, vessel, range)
                : new int[SONAR_RAYS];
        List<SonarContact> contacts = collectSonarContacts(level, terminal, vessel, range);
        List<HandSonarContact> handSonars = collectHandSonars(level, terminal, vessel, range);

        SonarSnapshot snapshot = new SonarSnapshot(obstacles, contacts, handSonars);
        terminal.sonarCache = snapshot;
        terminal.sonarCacheTick = ticks;
        terminal.sonarCacheZoom = terminal.zoom;
        terminal.sonarCacheActive = terminal.activeSonar;
        terminal.sonarCacheDirectional = terminal.directional;
        terminal.sonarCacheBeam = terminal.beamAngle;
        return snapshot;
    }

    private int[] scanSonarObstacles(ServerLevel level, TerminalState terminal, VesselState vessel,
                                     double maxDistance) {
        int[] distances = new int[SONAR_RAYS];
        Vec3 origin = vessel.anchorVec();
        Vec3 forwardAxis = SubmarineContraptionEntity.forward(vessel.yaw, vessel.pitch);
        Vec3 upAxis = SubmarineContraptionEntity.rotateLocal(new Vec3(0.0D, 1.0D, 0.0D), vessel.yaw, vessel.pitch).normalize();
        for (int i = 0; i < SONAR_RAYS; i++) {
            double angle = Math.PI * 2.0D * i / SONAR_RAYS;
            if (terminal.directional && angularDifference((float) angle, terminal.beamAngle) > 0.30F) continue;
            Vec3 ray = forwardAxis.scale(Math.cos(angle)).add(upAxis.scale(Math.sin(angle))).normalize();
            for (double distance = 3.0D; distance <= maxDistance; distance += SONAR_STEP) {
                Vec3 samplePoint = origin.add(ray.scale(distance));
                BlockPos sample = BlockPos.containing(samplePoint);
                if (vessel.contains(sample)) continue;
                if (!level.hasChunkAt(sample)) break;
                BlockState state = level.getBlockState(sample);
                if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
                    distances[i] = Mth.clamp((int) Math.round(distance * 100.0D / maxDistance), 1, 100);
                    break;
                }
            }
        }
        return distances;
    }

    private List<SonarContact> collectSonarContacts(ServerLevel level, TerminalState terminal,
                                                     VesselState vessel, double range) {
        Vec3 center = vessel.anchorVec();
        AABB area = new AABB(center, center).inflate(range);
        List<SonarContact> contacts = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, area,
                entity -> entity.isAlive() && entity instanceof LivingEntity
                        && !(entity instanceof ServerPlayer)
                        && !vessel.contains(entity.blockPosition()))) {
            Vec3 delta = entity.position().subtract(center);
            SonarProjection projection = projectToSonar(delta, vessel, range);
            if (projection == null) continue;
            float angle = (float) Math.atan2(projection.y, projection.x);
            if (terminal.directional && angularDifference(angle, terminal.beamAngle) > 0.32F) continue;
            boolean hostile = isHostile(entity);
            if (!terminal.activeSonar && !hostile && entity.getDeltaMovement().lengthSqr() < 0.01D) continue;
            float strength = (float) Mth.clamp(entity.getDeltaMovement().length() * 3.0D + (hostile ? 0.55D : 0.25D),
                    0.2D, 1.0D);
            contacts.add(new SonarContact(projection.x, projection.y, strength,
                    entity.getDisplayName().getString(), hostile ? "ENEMY" : "CREATURE"));
            if (contacts.size() >= 64) break;
        }
        return contacts;
    }

    private List<HandSonarContact> collectHandSonars(ServerLevel level, TerminalState terminal,
                                                      VesselState vessel, double range) {
        Vec3 center = vessel.anchorVec();
        List<HandSonarContact> result = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            boolean active = player.getMainHandItem().is(ModItems.ACTIVE_HAND_SONAR.get())
                    || player.getOffhandItem().is(ModItems.ACTIVE_HAND_SONAR.get());
            if (!active) continue;
            Vec3 delta = player.position().subtract(center);
            SonarProjection projection = projectToSonar(delta, vessel, range);
            if (projection == null) continue;
            float angle = (float) Math.atan2(projection.y, projection.x);
            if (terminal.directional && angularDifference(angle, terminal.beamAngle) > 0.32F) continue;
            result.add(new HandSonarContact(projection.x, projection.y,
                    player.getGameProfile().getName(), player.getUUID()));
        }
        return result;
    }

    private SonarProjection projectToSonar(Vec3 delta, VesselState vessel, double range) {
        Vec3 local = SubmarineContraptionEntity.inverseRotate(delta, vessel.yaw, vessel.pitch);
        double horizontalMagnitude = Math.sqrt(local.x * local.x + local.z * local.z);
        double signedHorizontal = Math.copySign(horizontalMagnitude,
                Math.abs(local.x) > 0.001D ? local.x : local.z);
        if (Math.abs(signedHorizontal) > range || Math.abs(local.y) > range) return null;
        double radiusSquared = signedHorizontal * signedHorizontal + local.y * local.y;
        if (radiusSquared > range * range) return null;
        return new SonarProjection((float) (signedHorizontal / range), (float) (-local.y / range));
    }

    private boolean isHostile(Entity entity) {
        if (entity instanceof Enemy) return true;
        if (entity.getType().is(SONAR_HOSTILE) || entity.getTags().contains("barotrauma_sonar_hostile")) return true;
        if (entity instanceof Mob mob && mob.getTarget() instanceof ServerPlayer) return true;
        return false;
    }

    private void putSonarContacts(CompoundTag tag, List<SonarContact> contacts) {
        ListTag rows = new ListTag();
        for (SonarContact contact : contacts) {
            CompoundTag row = new CompoundTag();
            row.putFloat("X", contact.x);
            row.putFloat("Y", contact.y);
            row.putFloat("Strength", contact.strength);
            row.putString("Name", contact.name);
            row.putString("Kind", contact.kind);
            rows.add(row);
        }
        tag.put("Contacts", rows);
    }

    private void putHandSonars(CompoundTag tag, List<HandSonarContact> contacts) {
        ListTag rows = new ListTag();
        for (HandSonarContact contact : contacts) {
            CompoundTag row = new CompoundTag();
            row.putFloat("X", contact.x);
            row.putFloat("Y", contact.y);
            row.putString("Name", contact.name);
            row.putUUID("Uuid", contact.uuid);
            rows.add(row);
        }
        tag.put("HandSonars", rows);
    }

    private static float angularDifference(float a, float b) {
        float difference = (a - b) % ((float) Math.PI * 2.0F);
        if (difference > Math.PI) difference -= (float) Math.PI * 2.0F;
        if (difference < -Math.PI) difference += (float) Math.PI * 2.0F;
        return Math.abs(difference);
    }

    private byte[] buildHullGridCached(ServerLevel level, BlockPos terminalPos, VesselState vessel) {
        Direction.Axis axis = vessel.detached ? Direction.Axis.X : facingAt(level, terminalPos).getAxis();
        if (vessel.hullCache != null && vessel.hullCacheAxis == axis
                && ticks - vessel.hullCacheTick < 20L) {
            return vessel.hullCache.clone();
        }
        vessel.hullCache = buildHullGrid(level, terminalPos, vessel);
        vessel.hullCacheAxis = axis;
        vessel.hullCacheTick = ticks;
        return vessel.hullCache.clone();
    }

    private byte[] buildHullGrid(ServerLevel level, BlockPos terminalPos, VesselState vessel) {
        Direction.Axis axis = vessel.detached ? Direction.Axis.X : facingAt(level, terminalPos).getAxis();
        int columns = hullColumns(vessel, axis);
        int rows = hullRows(vessel);
        byte[] grid = new byte[columns * rows];

        if (vessel.detached) {
            SubmarineContraptionEntity contraption = getContraption(level, vessel);
            if (contraption == null) return grid;
            int horizontalSize = Math.max(1, contraption.sizeX());
            int verticalSize = Math.max(1, contraption.sizeY());
            for (SubmarineContraptionEntity.BlockSnapshot snapshot : contraption.blocks()) {
                if (snapshot.state().isAir()) continue;
                int column = Mth.clamp(snapshot.x() * columns / horizontalSize, 0, columns - 1);
                int row = Mth.clamp((contraption.sizeY() - 1 - snapshot.y()) * rows / verticalSize, 0, rows - 1);
                grid[row * columns + column] = 1;
            }
            return grid;
        }

        int horizontalMin = axis == Direction.Axis.X ? vessel.min.getX() : vessel.min.getZ();
        int horizontalMax = axis == Direction.Axis.X ? vessel.max.getX() : vessel.max.getZ();
        int horizontalSize = Math.max(1, horizontalMax - horizontalMin + 1);
        int verticalSize = Math.max(1, vessel.max.getY() - vessel.min.getY() + 1);
        for (BlockPos cursor : BlockPos.betweenClosed(vessel.min, vessel.max)) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() && state.getFluidState().isEmpty()) continue;
            int horizontal = axis == Direction.Axis.X ? cursor.getX() : cursor.getZ();
            int column = Mth.clamp((horizontal - horizontalMin) * columns / horizontalSize, 0, columns - 1);
            int row = Mth.clamp((vessel.max.getY() - cursor.getY()) * rows / verticalSize, 0, rows - 1);
            int index = row * columns + column;
            if (!state.getFluidState().isEmpty()) {
                if (grid[index] == 1) grid[index] = 2;
            } else {
                grid[index] = grid[index] == 2 ? (byte)2 : (byte)1;
            }
        }
        return grid;
    }

    private int hullColumns(VesselState vessel, Direction.Axis axis) {
        int size = vessel.detached ? vessel.sizeX() : (axis == Direction.Axis.X
                ? vessel.max.getX() - vessel.min.getX() + 1
                : vessel.max.getZ() - vessel.min.getZ() + 1);
        return Mth.clamp(size, 1, STATUS_COLUMNS);
    }

    private int hullRows(VesselState vessel) {
        return Mth.clamp(vessel.detached ? vessel.sizeY() : vessel.max.getY() - vessel.min.getY() + 1, 1, STATUS_ROWS);
    }

    private static byte[] templateHullGrid() {
        byte[] grid = new byte[TEMPLATE_COLUMNS * TEMPLATE_ROWS];
        int mid = TEMPLATE_ROWS / 2;
        for (int x = 3; x < TEMPLATE_COLUMNS - 3; x++) {
            int taper = Math.min(x - 3, TEMPLATE_COLUMNS - 4 - x);
            int half = Math.max(2, Math.min(6, 2 + taper / 4));
            for (int y = mid - half; y <= mid + half; y++) {
                if (y >= 0 && y < TEMPLATE_ROWS) grid[y * TEMPLATE_COLUMNS + x] = 1;
            }
        }
        for (int x = TEMPLATE_COLUMNS / 2 - 4; x <= TEMPLATE_COLUMNS / 2 + 4; x++) {
            int y = mid - 7;
            if (y >= 0) grid[y * TEMPLATE_COLUMNS + x] = 1;
        }
        return grid;
    }

    private void putCrew(ServerLevel level, CompoundTag tag, BlockPos terminalPos, VesselState vessel) {
        ListTag crew = new ListTag();
        if (vessel.detached) {
            SubmarineContraptionEntity contraption = getContraption(level, vessel);
            if (contraption != null) {
                AABB bounds = contraption.transformedBounds(vessel.posX, vessel.posY, vessel.posZ, vessel.yaw, vessel.pitch).inflate(0.5D);
                for (ServerPlayer player : level.players()) {
                    if (!bounds.contains(player.position())) continue;
                    Vec3 local = contraption.worldToLocal(player.position(), vessel.posX, vessel.posY, vessel.posZ, vessel.yaw, vessel.pitch);
                    AABB localBounds = contraption.localBounds();
                    if (!localBounds.inflate(0.75D).contains(local)) continue;
                    CompoundTag row = new CompoundTag();
                    row.putUUID("Uuid", player.getUUID());
                    row.putString("Name", player.getGameProfile().getName());
                    row.putFloat("X", (float)Mth.clamp((local.x - localBounds.minX) / Math.max(1.0D, localBounds.getXsize()), 0.0D, 1.0D));
                    row.putFloat("Y", (float)Mth.clamp((localBounds.maxY - local.y) / Math.max(1.0D, localBounds.getYsize()), 0.0D, 1.0D));
                    crew.add(row);
                }
            }
            tag.put("Crew", crew);
            return;
        }

        Direction facing = facingAt(level, terminalPos);
        int horizontalMin = facing.getAxis() == Direction.Axis.X ? vessel.min.getX() : vessel.min.getZ();
        int horizontalMax = facing.getAxis() == Direction.Axis.X ? vessel.max.getX() : vessel.max.getZ();
        double horizontalSize = Math.max(1.0D, horizontalMax - horizontalMin + 1.0D);
        double verticalSize = Math.max(1.0D, vessel.max.getY() - vessel.min.getY() + 1.0D);
        AABB bounds = new AABB(vessel.min, vessel.max.offset(1, 1, 1));
        for (ServerPlayer player : level.players()) {
            if (!bounds.contains(player.position())) continue;
            double horizontal = facing.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
            CompoundTag row = new CompoundTag();
            row.putUUID("Uuid", player.getUUID());
            row.putString("Name", player.getGameProfile().getName());
            row.putFloat("X", (float) Mth.clamp((horizontal - horizontalMin) / horizontalSize, 0.0D, 1.0D));
            row.putFloat("Y", (float) Mth.clamp((vessel.max.getY() + 1.0D - player.getY()) / verticalSize, 0.0D, 1.0D));
            crew.add(row);
        }
        tag.put("Crew", crew);
    }

    private void putTargets(ServerLevel level, CompoundTag tag, TerminalState terminal, BlockPos origin) {
        List<NavigationTarget> targetList = relevantTargets(level, origin);
        ListTag targetTags = new ListTag();
        for (NavigationTarget target : targetList) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", target.id);
            row.putString("Name", target.displayName);
            row.putString("Type", target.type.name());
            row.putString("TypeName", target.type.russianName());
            row.putString("Mission", target.mission == NavigationReferenceData.MissionType.CUSTOM ? "" : target.mission.russianName());
            row.putLong("Pos", target.pos.asLong());
            row.putInt("Distance", (int) Math.round(Math.sqrt(origin.distSqr(target.pos))));
            targetTags.add(row);
        }
        tag.put("Targets", targetTags);
        int maxSelection = Math.min(3, targetList.size());
        terminal.selectedDestination = Mth.clamp(terminal.selectedDestination, 0, maxSelection);
    }

    private List<NavigationTarget> relevantTargets(ServerLevel level, BlockPos origin) {
        List<NavigationTarget> result = new ArrayList<>(targets.values());
        BlockPos spawn = level.getSharedSpawnPos();
        boolean hasStart = result.stream().anyMatch(target -> target.type == NavigationReferenceData.TargetType.START);
        if (!hasStart) {
            result.add(new NavigationTarget(new UUID(0L, 1L), NavigationReferenceData.TargetType.START,
                    NavigationReferenceData.MissionType.CUSTOM, spawn, "Аванпост у точки появления"));
        }
        result.sort(Comparator.comparingDouble(target -> origin.distSqr(target.pos)));
        return result.size() > 12 ? result.subList(0, 12) : result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag terminalTags = new ListTag();
        for (Map.Entry<Long, TerminalState> entry : terminals.entrySet()) {
            CompoundTag row = entry.getValue().toTag();
            row.putLong("Pos", entry.getKey());
            terminalTags.add(row);
        }
        tag.put("Terminals", terminalTags);

        ListTag vesselTags = new ListTag();
        for (VesselState vessel : vessels.values()) vesselTags.add(vessel.toTag());
        tag.put("Vessels", vesselTags);

        ListTag targetTags = new ListTag();
        for (NavigationTarget target : targets.values()) targetTags.add(target.toTag());
        tag.put("Targets", targetTags);
        tag.putLong("Ticks", ticks);
        return tag;
    }

    public static NavigationWorldData load(CompoundTag tag) {
        NavigationWorldData data = new NavigationWorldData();
        data.ticks = tag.getLong("Ticks");
        ListTag terminalTags = tag.getList("Terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < terminalTags.size(); i++) {
            CompoundTag row = terminalTags.getCompound(i);
            data.terminals.put(row.getLong("Pos"), TerminalState.fromTag(row));
        }
        ListTag vesselTags = tag.getList("Vessels", Tag.TAG_COMPOUND);
        for (int i = 0; i < vesselTags.size(); i++) {
            VesselState vessel = VesselState.fromTag(vesselTags.getCompound(i));
            data.vessels.put(vessel.id, vessel);
        }
        ListTag targetTags = tag.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < targetTags.size(); i++) {
            NavigationTarget target = NavigationTarget.fromTag(targetTags.getCompound(i));
            data.targets.put(target.id, target);
        }
        return data;
    }

    public enum VesselMode {
        SINGLE_BLOCK,
        MULTIBLOCK
    }

    public static final class TerminalState {
        private UUID vesselId;
        private boolean activeSonar;
        private boolean directional;
        private boolean autopilot = true;
        private int zoom = 35;
        private int selectedDestination;
        private float manualForward; // legacy save migration
        private float manualVertical; // legacy save migration
        private float manualTargetYaw;
        private float manualTargetPitch;
        private float manualThrottle;
        private boolean manualHeadingSet;
        private boolean manualPitchSet;
        private boolean virtualTerminal;
        private double virtualLocalX;
        private double virtualLocalY;
        private double virtualLocalZ;
        private float beamAngle;
        private BlockPos maintainPos;
        private boolean templateMode;
        private byte[] sectionActions = new byte[STATUS_COLUMNS * STATUS_ROWS];
        private transient BlockPos currentTerminalPos;
        private transient int avoidanceX;
        private transient int avoidanceY;
        private transient int avoidanceZ;
        private transient int avoidanceTicks;
        private transient SonarSnapshot sonarCache;
        private transient long sonarCacheTick = Long.MIN_VALUE;
        private transient int sonarCacheZoom = -1;
        private transient boolean sonarCacheActive;
        private transient boolean sonarCacheDirectional;
        private transient float sonarCacheBeam;

        public UUID vesselId() { return vesselId; }
        public boolean activeSonar() { return activeSonar; }
        public boolean directional() { return directional; }
        public boolean autopilot() { return autopilot; }
        public int zoom() { return zoom; }
        public int selectedDestination() { return selectedDestination; }
        public float beamAngle() { return beamAngle; }

        public void toggleSonar() {
            activeSonar = !activeSonar;
            invalidateSonar();
        }
        public void toggleDirectional() {
            directional = !directional;
            invalidateSonar();
        }
        public void toggleAutopilot() {
            autopilot = !autopilot;
            clearAvoidance();
            if (!autopilot) selectedDestination = 0;
        }
        public void setZoom(int value) {
            zoom = Mth.clamp(value, 0, 100);
            invalidateSonar();
        }
        public void selectDestination(int value) {
            selectedDestination = Mth.clamp(value, 0, 12);
            clearAvoidance();
        }
        public void setManual(float forward, float vertical) {
            // Compatibility with pre-rigid-motion clients. Treat the old vector as a throttle and
            // vertical pitch request instead of lateral block-grid movement.
            manualForward = Mth.clamp(forward, -1.0F, 1.0F);
            manualVertical = Mth.clamp(vertical, -1.0F, 1.0F);
            manualThrottle = Mth.clamp((float)Math.sqrt(forward * forward + vertical * vertical), 0.0F, 1.0F);
            manualTargetPitch = Mth.clamp(vertical * MAX_PITCH, -MAX_PITCH, MAX_PITCH);
            manualPitchSet = true;
        }
        public void setManualHeading(float yaw, float throttle) {
            manualTargetYaw = wrapDegrees(yaw);
            manualThrottle = Mth.clamp(throttle, 0.0F, 1.0F);
            manualHeadingSet = true;
        }
        public void setManualPitch(float pitch) {
            manualTargetPitch = Mth.clamp(pitch, -MAX_PITCH, MAX_PITCH);
            manualPitchSet = true;
        }
        public void initialiseManual(float yaw, float pitch) {
            manualTargetYaw = wrapDegrees(yaw);
            manualTargetPitch = Mth.clamp(pitch, -MAX_PITCH, MAX_PITCH);
            manualHeadingSet = true;
            manualPitchSet = true;
        }
        public void setBeamAngle(float angle) {
            beamAngle = angle;
            invalidateSonar();
        }
        public void setMaintainPos(BlockPos pos) {
            maintainPos = pos;
            clearAvoidance();
        }
        public void toggleTemplate() { templateMode = !templateMode; }
        public boolean templateMode() { return templateMode; }
        public void setSectionAction(int column, int row, int action) {
            if (column < 0 || column >= STATUS_COLUMNS || row < 0 || row >= STATUS_ROWS) return;
            int index = row * STATUS_COLUMNS + column;
            byte encoded = (byte)(Mth.clamp(action, 0, 5) + 1);
            sectionActions[index] = sectionActions[index] == encoded ? 0 : encoded;
        }
        private void clearAvoidance() {
            avoidanceX = 0;
            avoidanceY = 0;
            avoidanceZ = 0;
            avoidanceTicks = 0;
        }
        private void invalidateSonar() {
            sonarCache = null;
            sonarCacheTick = Long.MIN_VALUE;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            if (vesselId != null) tag.putUUID("Vessel", vesselId);
            tag.putBoolean("ActiveSonar", activeSonar);
            tag.putBoolean("Directional", directional);
            tag.putBoolean("Autopilot", autopilot);
            tag.putInt("Zoom", zoom);
            tag.putInt("SelectedDestination", selectedDestination);
            tag.putFloat("ManualForward", manualForward);
            tag.putFloat("ManualVertical", manualVertical);
            tag.putFloat("ManualTargetYaw", manualTargetYaw);
            tag.putFloat("ManualTargetPitch", manualTargetPitch);
            tag.putFloat("ManualThrottle", manualThrottle);
            tag.putBoolean("ManualHeadingSet", manualHeadingSet);
            tag.putBoolean("ManualPitchSet", manualPitchSet);
            tag.putBoolean("VirtualTerminal", virtualTerminal);
            tag.putDouble("VirtualLocalX", virtualLocalX);
            tag.putDouble("VirtualLocalY", virtualLocalY);
            tag.putDouble("VirtualLocalZ", virtualLocalZ);
            tag.putFloat("BeamAngle", beamAngle);
            tag.putBoolean("TemplateMode", templateMode);
            tag.putByteArray("SectionActions", sectionActions);
            if (maintainPos != null) tag.putLong("MaintainPos", maintainPos.asLong());
            return tag;
        }

        static TerminalState fromTag(CompoundTag tag) {
            TerminalState state = new TerminalState();
            if (tag.hasUUID("Vessel")) state.vesselId = tag.getUUID("Vessel");
            state.activeSonar = tag.getBoolean("ActiveSonar");
            state.directional = tag.getBoolean("Directional");
            state.autopilot = !tag.contains("Autopilot") || tag.getBoolean("Autopilot");
            state.zoom = Mth.clamp(tag.getInt("Zoom"), 0, 100);
            state.selectedDestination = Math.max(0, tag.getInt("SelectedDestination"));
            state.manualForward = tag.getFloat("ManualForward");
            state.manualVertical = tag.getFloat("ManualVertical");
            state.manualTargetYaw = tag.getFloat("ManualTargetYaw");
            state.manualTargetPitch = tag.getFloat("ManualTargetPitch");
            state.manualThrottle = tag.contains("ManualThrottle") ? tag.getFloat("ManualThrottle")
                    : Mth.clamp((float)Math.sqrt(state.manualForward * state.manualForward + state.manualVertical * state.manualVertical), 0.0F, 1.0F);
            state.manualHeadingSet = tag.getBoolean("ManualHeadingSet");
            state.manualPitchSet = tag.getBoolean("ManualPitchSet");
            state.virtualTerminal = tag.getBoolean("VirtualTerminal");
            state.virtualLocalX = tag.getDouble("VirtualLocalX");
            state.virtualLocalY = tag.getDouble("VirtualLocalY");
            state.virtualLocalZ = tag.getDouble("VirtualLocalZ");
            state.beamAngle = tag.getFloat("BeamAngle");
            state.templateMode = tag.getBoolean("TemplateMode");
            byte[] actions = tag.getByteArray("SectionActions");
            if (actions.length > 0) System.arraycopy(actions, 0, state.sectionActions, 0, Math.min(actions.length, state.sectionActions.length));
            if (tag.contains("MaintainPos")) state.maintainPos = BlockPos.of(tag.getLong("MaintainPos"));
            return state;
        }
    }

    public static final class VesselState {
        private final UUID id;
        private final VesselMode mode;
        private BlockPos min;
        private BlockPos max;
        private final BlockPos origin;
        private final String name;
        private double forwardVelocity; // legacy persisted field
        private double verticalVelocity; // legacy persisted field
        private double lateralVelocity; // legacy persisted field
        private double forwardAccumulator; // legacy persisted field
        private double verticalAccumulator; // legacy persisted field
        private double lateralAccumulator; // legacy persisted field
        private double posX;
        private double posY;
        private double posZ;
        private float yaw;
        private float pitch;
        private double speed;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private float yawVelocity;
        private float pitchVelocity;
        private boolean detached;
        private boolean detachedPower;
        private UUID contraptionEntity;
        private long lastMoveTick;
        private transient byte[] hullCache;
        private transient Direction.Axis hullCacheAxis;
        private transient long hullCacheTick = Long.MIN_VALUE;

        VesselState(UUID id, VesselMode mode, BlockPos min, BlockPos max, String name) {
            this(id, mode, min, max, min, name);
        }

        VesselState(UUID id, VesselMode mode, BlockPos min, BlockPos max, BlockPos origin, String name) {
            this.id = id;
            this.mode = mode;
            this.min = min;
            this.max = max;
            this.origin = origin;
            this.name = name;
            this.posX = (min.getX() + max.getX() + 1.0D) * 0.5D;
            this.posY = (min.getY() + max.getY() + 1.0D) * 0.5D;
            this.posZ = (min.getZ() + max.getZ() + 1.0D) * 0.5D;
        }

        public UUID id() { return id; }
        public VesselMode mode() { return mode; }
        public BlockPos min() { return min; }
        public BlockPos max() { return max; }
        public String name() { return name; }
        public BlockPos anchor() {
            return BlockPos.containing(posX, posY, posZ);
        }
        public Vec3 anchorVec() { return new Vec3(posX, posY, posZ); }
        public int sizeX() { return max.getX() - min.getX() + 1; }
        public int sizeY() { return max.getY() - min.getY() + 1; }
        public int sizeZ() { return max.getZ() - min.getZ() + 1; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public double speed() { return speed; }
        public boolean detached() { return detached; }
        public boolean contains(BlockPos pos) {
            if (!detached) return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
            Vec3 local = SubmarineContraptionEntity.inverseRotate(
                    Vec3.atCenterOf(pos).subtract(posX, posY, posZ), yaw, pitch);
            double hx = sizeX() * 0.5D, hy = sizeY() * 0.5D, hz = sizeZ() * 0.5D;
            return local.x >= -hx && local.x <= hx && local.y >= -hy && local.y <= hy
                    && local.z >= -hz && local.z <= hz;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Mode", mode.name());
            tag.putLong("Min", min.asLong());
            tag.putLong("Max", max.asLong());
            tag.putLong("Origin", origin.asLong());
            tag.putString("Name", name);
            tag.putDouble("ForwardVelocity", forwardVelocity);
            tag.putDouble("VerticalVelocity", verticalVelocity);
            tag.putDouble("LateralVelocity", lateralVelocity);
            tag.putDouble("ForwardAccumulator", forwardAccumulator);
            tag.putDouble("VerticalAccumulator", verticalAccumulator);
            tag.putDouble("LateralAccumulator", lateralAccumulator);
            tag.putDouble("PosX", posX);
            tag.putDouble("PosY", posY);
            tag.putDouble("PosZ", posZ);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            tag.putDouble("Speed", speed);
            tag.putDouble("VelocityX", velocityX);
            tag.putDouble("VelocityY", velocityY);
            tag.putDouble("VelocityZ", velocityZ);
            tag.putFloat("YawVelocity", yawVelocity);
            tag.putFloat("PitchVelocity", pitchVelocity);
            tag.putBoolean("Detached", detached);
            tag.putBoolean("DetachedPower", detachedPower);
            if (contraptionEntity != null) tag.putUUID("ContraptionEntity", contraptionEntity);
            tag.putLong("LastMoveTick", lastMoveTick);
            return tag;
        }

        static VesselState fromTag(CompoundTag tag) {
            VesselMode mode;
            try { mode = VesselMode.valueOf(tag.getString("Mode")); }
            catch (IllegalArgumentException ignored) { mode = VesselMode.MULTIBLOCK; }
            VesselState vessel = new VesselState(tag.getUUID("Id"), mode,
                    BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")),
                    BlockPos.of(tag.getLong("Origin")), tag.getString("Name"));
            vessel.forwardVelocity = tag.getDouble("ForwardVelocity");
            vessel.verticalVelocity = tag.getDouble("VerticalVelocity");
            vessel.lateralVelocity = tag.getDouble("LateralVelocity");
            vessel.forwardAccumulator = tag.getDouble("ForwardAccumulator");
            vessel.verticalAccumulator = tag.getDouble("VerticalAccumulator");
            vessel.lateralAccumulator = tag.getDouble("LateralAccumulator");
            if (tag.contains("PosX")) {
                vessel.posX = tag.getDouble("PosX");
                vessel.posY = tag.getDouble("PosY");
                vessel.posZ = tag.getDouble("PosZ");
            }
            vessel.yaw = tag.getFloat("Yaw");
            vessel.pitch = tag.getFloat("Pitch");
            vessel.speed = tag.contains("Speed") ? tag.getDouble("Speed") : Math.abs(vessel.forwardVelocity);
            if (tag.contains("VelocityX")) {
                vessel.velocityX = tag.getDouble("VelocityX");
                vessel.velocityY = tag.getDouble("VelocityY");
                vessel.velocityZ = tag.getDouble("VelocityZ");
            } else if (vessel.speed > 0.0D) {
                Vec3 legacyForward = SubmarineContraptionEntity.forward(vessel.yaw, vessel.pitch);
                vessel.velocityX = legacyForward.x * vessel.speed;
                vessel.velocityY = legacyForward.y * vessel.speed;
                vessel.velocityZ = legacyForward.z * vessel.speed;
            }
            vessel.yawVelocity = tag.getFloat("YawVelocity");
            vessel.pitchVelocity = tag.getFloat("PitchVelocity");
            vessel.detached = tag.getBoolean("Detached");
            vessel.detachedPower = tag.getBoolean("DetachedPower");
            if (tag.hasUUID("ContraptionEntity")) vessel.contraptionEntity = tag.getUUID("ContraptionEntity");
            vessel.lastMoveTick = tag.getLong("LastMoveTick");
            return vessel;
        }
    }

    public record NavigationTarget(UUID id, NavigationReferenceData.TargetType type,
                                   NavigationReferenceData.MissionType mission,
                                   BlockPos pos, String displayName) {
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Type", type.name());
            tag.putString("Mission", mission.name());
            tag.putLong("Pos", pos.asLong());
            tag.putString("Name", displayName);
            return tag;
        }

        static NavigationTarget fromTag(CompoundTag tag) {
            return new NavigationTarget(tag.getUUID("Id"),
                    NavigationReferenceData.TargetType.parse(tag.getString("Type")),
                    NavigationReferenceData.MissionType.parse(tag.getString("Mission")),
                    BlockPos.of(tag.getLong("Pos")), tag.getString("Name"));
        }
    }

    private record SonarSnapshot(int[] obstacles, List<SonarContact> contacts,
                                 List<HandSonarContact> handSonars) {}
    private record SonarContact(float x, float y, float strength, String name, String kind) {}
    private record HandSonarContact(float x, float y, String name, UUID uuid) {}
    private record SonarProjection(float x, float y) {}
    private record BlockSnapshot(BlockPos pos, BlockState state, CompoundTag blockEntityTag) {}
    private record Alias(long destination, long expiresAt) {}
}
