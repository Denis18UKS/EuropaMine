package com.z_mods.barotrauma.navigation;

import com.z_mods.barotrauma.init.ModItems;
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
    private static final int STATUS_COLUMNS = 32;
    private static final int STATUS_ROWS = 12;
    private static final double MAX_FORWARD_SPEED = 0.16D;
    private static final double MAX_LATERAL_SPEED = 0.13D;
    private static final double MAX_VERTICAL_SPEED = 0.11D;
    private static final double VELOCITY_RESPONSE = 0.075D;
    private static final int AUTOPILOT_LOOKAHEAD = 4;
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

            boolean powered = NavigationSystem.hasPower(level, terminalPos);
            Vec3 input = desiredInput(level, terminal, vessel);
            double targetForward = powered ? input.x * MAX_FORWARD_SPEED : 0.0D;
            double targetVertical = powered ? input.y * MAX_VERTICAL_SPEED : 0.0D;
            double targetLateral = powered ? input.z * MAX_LATERAL_SPEED : 0.0D;

            vessel.forwardVelocity = approach(vessel.forwardVelocity, targetForward, VELOCITY_RESPONSE);
            vessel.verticalVelocity = approach(vessel.verticalVelocity, targetVertical, VELOCITY_RESPONSE);
            vessel.lateralVelocity = approach(vessel.lateralVelocity, targetLateral, VELOCITY_RESPONSE);
            if (!powered) {
                vessel.forwardVelocity *= 0.82D;
                vessel.verticalVelocity *= 0.82D;
                vessel.lateralVelocity *= 0.82D;
            }

            Direction facing = facingAt(level, terminalPos);
            Direction right = facing.getClockWise();
            Vec3 continuousMotion = new Vec3(
                    facing.getStepX() * vessel.forwardVelocity + right.getStepX() * vessel.lateralVelocity,
                    vessel.verticalVelocity,
                    facing.getStepZ() * vessel.forwardVelocity + right.getStepZ() * vessel.lateralVelocity);

            double nextForwardAccumulator = vessel.forwardAccumulator + vessel.forwardVelocity;
            double nextVerticalAccumulator = vessel.verticalAccumulator + vessel.verticalVelocity;
            double nextLateralAccumulator = vessel.lateralAccumulator + vessel.lateralVelocity;
            int forwardStep = wholeStep(nextForwardAccumulator);
            int verticalStep = wholeStep(nextVerticalAccumulator);
            int lateralStep = wholeStep(nextLateralAccumulator);
            int dx = facing.getStepX() * forwardStep + right.getStepX() * lateralStep;
            int dy = verticalStep;
            int dz = facing.getStepZ() * forwardStep + right.getStepZ() * lateralStep;

            int probeForward = forwardStep != 0 ? forwardStep : sign(vessel.forwardVelocity);
            int probeVertical = verticalStep != 0 ? verticalStep : sign(vessel.verticalVelocity);
            int probeLateral = lateralStep != 0 ? lateralStep : sign(vessel.lateralVelocity);
            int probeX = facing.getStepX() * probeForward + right.getStepX() * probeLateral;
            int probeY = probeVertical;
            int probeZ = facing.getStepZ() * probeForward + right.getStepZ() * probeLateral;

            if ((probeX != 0 || probeY != 0 || probeZ != 0)
                    && !isStepClear(level, vessel, 0, 0, 0, probeX, probeY, probeZ)) {
                stopVessel(vessel);
                continue;
            }

            vessel.forwardAccumulator = nextForwardAccumulator;
            vessel.verticalAccumulator = nextVerticalAccumulator;
            vessel.lateralAccumulator = nextLateralAccumulator;

            boolean translated = true;
            if (dx != 0 || dy != 0 || dz != 0) {
                translated = translateVessel(level, vessel, dx, dy, dz);
                if (translated) {
                    vessel.forwardAccumulator -= forwardStep;
                    vessel.verticalAccumulator -= verticalStep;
                    vessel.lateralAccumulator -= lateralStep;
                    vessel.lastMoveTick = ticks;
                    translateTerminalsAndAliases(vessel, dx, dy, dz);
                    changed = true;
                }
            }

            if (!translated) {
                stopVessel(vessel);
                continue;
            }

            // The physical blocks still move on the Minecraft block grid, but entities are
            // carried by the fractional velocity every server tick. This removes the old
            // one-block player correction and makes standing/walking inside the vessel smooth.
            if (continuousMotion.lengthSqr() > 1.0E-7D) {
                moveCarriedEntities(level, vessel, continuousMotion);
            }
        }

        if (ticks % 20L == 0L || changed) setDirty();
    }

    private static double approach(double current, double target, double response) {
        double next = current + (target - current) * response;
        return Math.abs(next) < 1.0E-5D && Math.abs(target) < 1.0E-5D ? 0.0D : next;
    }

    private static int wholeStep(double accumulator) {
        return accumulator >= 1.0D ? 1 : accumulator <= -1.0D ? -1 : 0;
    }

    private static int sign(double value) {
        return value > 1.0E-4D ? 1 : value < -1.0E-4D ? -1 : 0;
    }

    private static void stopVessel(VesselState vessel) {
        vessel.forwardVelocity *= 0.25D;
        vessel.verticalVelocity *= 0.25D;
        vessel.lateralVelocity *= 0.25D;
        vessel.forwardAccumulator = Mth.clamp(vessel.forwardAccumulator, -0.95D, 0.95D);
        vessel.verticalAccumulator = Mth.clamp(vessel.verticalAccumulator, -0.95D, 0.95D);
        vessel.lateralAccumulator = Mth.clamp(vessel.lateralAccumulator, -0.95D, 0.95D);
    }

    private Vec3 desiredInput(ServerLevel level, TerminalState terminal, VesselState vessel) {
        if (!terminal.autopilot) {
            terminal.clearAvoidance();
            return new Vec3(Mth.clamp(terminal.manualForward, -1.0F, 1.0F),
                    Mth.clamp(terminal.manualVertical, -1.0F, 1.0F), 0.0D);
        }

        BlockPos destination = null;
        if (terminal.selectedDestination == 0) {
            if (terminal.maintainPos == null) terminal.maintainPos = vessel.anchor();
            destination = terminal.maintainPos;
        } else {
            List<NavigationTarget> targetList = relevantTargets(level, vessel.anchor());
            int index = terminal.selectedDestination - 1;
            if (index >= 0 && index < targetList.size()) destination = targetList.get(index).pos;
        }
        if (destination == null) return Vec3.ZERO;

        BlockPos anchor = vessel.anchor();
        Vec3 worldDifference = new Vec3(destination.getX() - anchor.getX(),
                destination.getY() - anchor.getY(), destination.getZ() - anchor.getZ());
        if (worldDifference.lengthSqr() < 2.25D) {
            terminal.clearAvoidance();
            return Vec3.ZERO;
        }

        Direction facing = facingAt(level,
                terminal.currentTerminalPos == null ? vessel.anchor() : terminal.currentTerminalPos);
        Direction right = facing.getClockWise();
        double forward = worldDifference.x * facing.getStepX() + worldDifference.z * facing.getStepZ();
        double lateral = worldDifference.x * right.getStepX() + worldDifference.z * right.getStepZ();
        double vertical = worldDifference.y;
        double maximum = Math.max(1.0D, Math.max(Math.abs(forward), Math.max(Math.abs(vertical), Math.abs(lateral))));
        Vec3 directLocal = new Vec3(forward / maximum, vertical / maximum, lateral / maximum);

        int directX = sign(worldDifference.x);
        int directY = sign(worldDifference.y);
        int directZ = sign(worldDifference.z);
        if (isCorridorClear(level, vessel, directX, directY, directZ, 2)) {
            terminal.clearAvoidance();
            return directLocal;
        }

        if (terminal.avoidanceTicks > 0
                && isCorridorClear(level, vessel, terminal.avoidanceX, terminal.avoidanceY,
                terminal.avoidanceZ, AUTOPILOT_LOOKAHEAD)) {
            terminal.avoidanceTicks--;
            return worldStepToLocal(terminal.avoidanceX, terminal.avoidanceY,
                    terminal.avoidanceZ, facing, right);
        }

        int[] detour = chooseDetour(level, vessel, destination, directX, directY, directZ);
        if (detour == null) {
            terminal.clearAvoidance();
            return Vec3.ZERO;
        }
        terminal.avoidanceX = detour[0];
        terminal.avoidanceY = detour[1];
        terminal.avoidanceZ = detour[2];
        terminal.avoidanceTicks = 30;
        return worldStepToLocal(detour[0], detour[1], detour[2], facing, right);
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


    private void moveCarriedEntities(ServerLevel level, VesselState vessel, Vec3 delta) {
        AABB bounds = new AABB(
                vessel.min.getX() - 0.35D, vessel.min.getY() - 0.75D, vessel.min.getZ() - 0.35D,
                vessel.max.getX() + 1.35D, vessel.max.getY() + 3.0D, vessel.max.getZ() + 1.35D);
        List<Entity> carried = level.getEntities((Entity) null, bounds,
                entity -> entity.isAlive()
                        && !(entity instanceof net.minecraft.world.entity.decoration.HangingEntity)
                        && !entity.isPassenger());
        for (Entity entity : carried) {
            double x = entity.getX() + delta.x;
            double y = entity.getY() + delta.y;
            double z = entity.getZ() + delta.z;
            if (entity instanceof ServerPlayer player) {
                player.setPos(x, y, z);
                player.fallDistance = 0.0F;
                NavigationPackets.sendVesselMotion(player, delta);
            } else {
                entity.setPos(x, y, z);
                entity.fallDistance = 0.0F;
            }
        }
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
        tag.putBoolean("Powered", NavigationSystem.hasPower(level, terminalPos));
        tag.putBoolean("ActiveSonar", terminal.activeSonar);
        tag.putBoolean("Directional", terminal.directional);
        tag.putBoolean("Autopilot", terminal.autopilot);
        tag.putInt("Zoom", terminal.zoom);
        tag.putInt("SelectedDestination", terminal.selectedDestination);
        tag.putFloat("ManualForward", terminal.manualForward);
        tag.putFloat("ManualVertical", terminal.manualVertical);
        tag.putFloat("BeamAngle", terminal.beamAngle);

        if (vessel == null) {
            tag.putBoolean("Linked", false);
            tag.putInt("Depth", Math.max(0, level.getSeaLevel() - terminalPos.getY()));
            tag.putIntArray("Sonar", new int[SONAR_RAYS]);
            tag.put("Contacts", new ListTag());
            tag.put("HandSonars", new ListTag());
            tag.putByteArray("HullGrid", new byte[STATUS_COLUMNS * STATUS_ROWS]);
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
        tag.putDouble("ForwardSpeedKmh", vessel.forwardVelocity * 20.0D * 3.6D);
        tag.putDouble("VerticalSpeedKmh", -vessel.verticalVelocity * 20.0D * 3.6D);
        tag.putDouble("LateralSpeedKmh", vessel.lateralVelocity * 20.0D * 3.6D);
        tag.putInt("Depth", Math.max(0, level.getSeaLevel() - anchor.getY()));
        tag.putBoolean("Docked", vessel.origin.distSqr(anchor) < 16.0D
                && Math.abs(vessel.forwardVelocity) < 0.005D
                && Math.abs(vessel.verticalVelocity) < 0.005D
                && Math.abs(vessel.lateralVelocity) < 0.005D);
        SonarSnapshot sonar = sonarSnapshot(level, terminalPos, terminal, vessel);
        tag.putIntArray("Sonar", sonar.obstacles);
        putSonarContacts(tag, sonar.contacts);
        putHandSonars(tag, sonar.handSonars);
        tag.putByteArray("HullGrid", buildHullGridCached(level, terminalPos, vessel));
        tag.putInt("HullColumns", STATUS_COLUMNS);
        tag.putInt("HullRows", STATUS_ROWS);
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

        Direction facing = facingAt(level, terminalPos);
        double range = Mth.lerp(terminal.zoom / 100.0D, 220.0D, 72.0D);
        int[] obstacles = terminal.activeSonar
                ? scanSonarObstacles(level, terminal, vessel, facing, range)
                : new int[SONAR_RAYS];
        List<SonarContact> contacts = collectSonarContacts(level, terminal, vessel, facing, range);
        List<HandSonarContact> handSonars = collectHandSonars(level, terminal, vessel, facing, range);

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
                                     Direction facing, double maxDistance) {
        int[] distances = new int[SONAR_RAYS];
        BlockPos origin = vessel.anchor();
        for (int i = 0; i < SONAR_RAYS; i++) {
            double angle = Math.PI * 2.0D * i / SONAR_RAYS;
            if (terminal.directional && angularDifference((float) angle, terminal.beamAngle) > 0.30F) continue;
            double forward = Math.cos(angle);
            double vertical = Math.sin(angle);
            for (double distance = 3.0D; distance <= maxDistance; distance += SONAR_STEP) {
                int x = Mth.floor(origin.getX() + facing.getStepX() * forward * distance);
                int y = Mth.floor(origin.getY() + vertical * distance);
                int z = Mth.floor(origin.getZ() + facing.getStepZ() * forward * distance);
                BlockPos sample = new BlockPos(x, y, z);
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
                                                     VesselState vessel, Direction facing, double range) {
        BlockPos anchor = vessel.anchor();
        Vec3 center = Vec3.atCenterOf(anchor);
        AABB area = new AABB(anchor).inflate(range);
        List<SonarContact> contacts = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, area,
                entity -> entity.isAlive() && entity instanceof LivingEntity
                        && !(entity instanceof ServerPlayer)
                        && !vessel.contains(entity.blockPosition()))) {
            Vec3 delta = entity.position().subtract(center);
            SonarProjection projection = projectToSonar(delta, facing, range);
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
                                                      VesselState vessel, Direction facing, double range) {
        BlockPos anchor = vessel.anchor();
        Vec3 center = Vec3.atCenterOf(anchor);
        List<HandSonarContact> result = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            boolean active = player.getMainHandItem().is(ModItems.ACTIVE_HAND_SONAR.get())
                    || player.getOffhandItem().is(ModItems.ACTIVE_HAND_SONAR.get());
            if (!active) continue;
            Vec3 delta = player.position().subtract(center);
            SonarProjection projection = projectToSonar(delta, facing, range);
            if (projection == null) continue;
            float angle = (float) Math.atan2(projection.y, projection.x);
            if (terminal.directional && angularDifference(angle, terminal.beamAngle) > 0.32F) continue;
            result.add(new HandSonarContact(projection.x, projection.y,
                    player.getGameProfile().getName(), player.getUUID()));
        }
        return result;
    }

    private SonarProjection projectToSonar(Vec3 delta, Direction facing, double range) {
        Direction right = facing.getClockWise();
        double forward = delta.x * facing.getStepX() + delta.z * facing.getStepZ();
        double lateral = delta.x * right.getStepX() + delta.z * right.getStepZ();
        double horizontalMagnitude = Math.sqrt(forward * forward + lateral * lateral);
        double signedHorizontal = Math.copySign(horizontalMagnitude,
                Math.abs(forward) > 0.001D ? forward : lateral);
        if (Math.abs(signedHorizontal) > range || Math.abs(delta.y) > range) return null;
        double radiusSquared = signedHorizontal * signedHorizontal + delta.y * delta.y;
        if (radiusSquared > range * range) return null;
        return new SonarProjection((float) (signedHorizontal / range), (float) (-delta.y / range));
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
        Direction.Axis axis = facingAt(level, terminalPos).getAxis();
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
        byte[] grid = new byte[STATUS_COLUMNS * STATUS_ROWS];
        Direction facing = facingAt(level, terminalPos);
        int horizontalMin = facing.getAxis() == Direction.Axis.X ? vessel.min.getX() : vessel.min.getZ();
        int horizontalMax = facing.getAxis() == Direction.Axis.X ? vessel.max.getX() : vessel.max.getZ();
        int horizontalSize = Math.max(1, horizontalMax - horizontalMin + 1);
        int verticalSize = Math.max(1, vessel.max.getY() - vessel.min.getY() + 1);

        for (BlockPos cursor : BlockPos.betweenClosed(vessel.min, vessel.max)) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() && state.getFluidState().isEmpty()) continue;
            int horizontal = facing.getAxis() == Direction.Axis.X ? cursor.getX() : cursor.getZ();
            int column = Mth.clamp((horizontal - horizontalMin) * STATUS_COLUMNS / horizontalSize, 0, STATUS_COLUMNS - 1);
            int row = Mth.clamp((vessel.max.getY() - cursor.getY()) * STATUS_ROWS / verticalSize, 0, STATUS_ROWS - 1);
            int index = row * STATUS_COLUMNS + column;
            if (!state.getFluidState().isEmpty()) grid[index] = 2;
            else if (grid[index] == 0) grid[index] = 1;
        }
        return grid;
    }

    private void putCrew(ServerLevel level, CompoundTag tag, BlockPos terminalPos, VesselState vessel) {
        Direction facing = facingAt(level, terminalPos);
        int horizontalMin = facing.getAxis() == Direction.Axis.X ? vessel.min.getX() : vessel.min.getZ();
        int horizontalMax = facing.getAxis() == Direction.Axis.X ? vessel.max.getX() : vessel.max.getZ();
        double horizontalSize = Math.max(1.0D, horizontalMax - horizontalMin + 1.0D);
        double verticalSize = Math.max(1.0D, vessel.max.getY() - vessel.min.getY() + 1.0D);
        ListTag crew = new ListTag();
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
        private float manualForward;
        private float manualVertical;
        private float beamAngle;
        private BlockPos maintainPos;
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
            manualForward = Mth.clamp(forward, -1.0F, 1.0F);
            manualVertical = Mth.clamp(vertical, -1.0F, 1.0F);
        }
        public void setBeamAngle(float angle) {
            beamAngle = angle;
            invalidateSonar();
        }
        public void setMaintainPos(BlockPos pos) {
            maintainPos = pos;
            clearAvoidance();
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
            tag.putFloat("BeamAngle", beamAngle);
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
            state.beamAngle = tag.getFloat("BeamAngle");
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
        private double forwardVelocity;
        private double verticalVelocity;
        private double lateralVelocity;
        private double forwardAccumulator;
        private double verticalAccumulator;
        private double lateralAccumulator;
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
        }

        public UUID id() { return id; }
        public VesselMode mode() { return mode; }
        public BlockPos min() { return min; }
        public BlockPos max() { return max; }
        public String name() { return name; }
        public BlockPos anchor() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2,
                    (min.getZ() + max.getZ()) / 2);
        }
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
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
