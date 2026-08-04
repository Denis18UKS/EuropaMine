package com.z_mods.barotrauma.navigation;

import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
    private static final int SONAR_RAYS = 120;
    private static final int STATUS_COLUMNS = 32;
    private static final int STATUS_ROWS = 12;

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
        if (ticks % 2L != 0L) return;

        List<MoveRequest> moves = new ArrayList<>();
        for (Map.Entry<Long, TerminalState> entry : new ArrayList<>(terminals.entrySet())) {
            BlockPos terminalPos = BlockPos.of(entry.getKey());
            TerminalState terminal = entry.getValue();
            VesselState vessel = vessels.get(terminal.vesselId);
            if (vessel == null) continue;

            boolean powered = NavigationSystem.hasPower(level, terminalPos);
            Vec3 input = desiredInput(level, terminal, vessel);
            double targetForward = powered ? input.x * 0.085D : 0.0D;
            double targetVertical = powered ? input.y * 0.065D : 0.0D;
            vessel.forwardVelocity += (targetForward - vessel.forwardVelocity) * 0.16D;
            vessel.verticalVelocity += (targetVertical - vessel.verticalVelocity) * 0.16D;
            if (!powered) {
                vessel.forwardVelocity *= 0.84D;
                vessel.verticalVelocity *= 0.84D;
            }

            vessel.forwardAccumulator += vessel.forwardVelocity;
            vessel.verticalAccumulator += vessel.verticalVelocity;

            Direction facing = facingAt(level, terminalPos);
            int dx = 0;
            int dy = 0;
            int dz = 0;
            if (Math.abs(vessel.forwardAccumulator) >= 1.0D) {
                int sign = vessel.forwardAccumulator > 0 ? 1 : -1;
                dx = facing.getStepX() * sign;
                dz = facing.getStepZ() * sign;
                vessel.forwardAccumulator -= sign;
            }
            if (Math.abs(vessel.verticalAccumulator) >= 1.0D) {
                dy = vessel.verticalAccumulator > 0 ? 1 : -1;
                vessel.verticalAccumulator -= dy;
            }
            if (dx != 0 || dy != 0 || dz != 0) moves.add(new MoveRequest(vessel.id, terminalPos, dx, dy, dz));
        }

        Set<UUID> moved = new HashSet<>();
        for (MoveRequest request : moves) {
            if (!moved.add(request.vesselId)) continue;
            VesselState vessel = vessels.get(request.vesselId);
            if (vessel == null) continue;
            if (translateVessel(level, vessel, request.dx, request.dy, request.dz)) {
                vessel.lastMoveTick = ticks;
                translateTerminalsAndAliases(vessel, request.dx, request.dy, request.dz);
            } else {
                vessel.forwardVelocity *= 0.2D;
                vessel.verticalVelocity *= 0.2D;
                vessel.forwardAccumulator = 0.0D;
                vessel.verticalAccumulator = 0.0D;
            }
        }
        if (ticks % 20L == 0L || !moves.isEmpty()) setDirty();
    }

    private Vec3 desiredInput(ServerLevel level, TerminalState terminal, VesselState vessel) {
        if (!terminal.autopilot) {
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

        Direction facing = facingAt(level, terminal.currentTerminalPos == null ? vessel.anchor() : terminal.currentTerminalPos);
        BlockPos anchor = vessel.anchor();
        double horizontal = (destination.getX() - anchor.getX()) * facing.getStepX()
                + (destination.getZ() - anchor.getZ()) * facing.getStepZ();
        double vertical = destination.getY() - anchor.getY();
        if (Math.abs(horizontal) < 1.5D) horizontal = 0.0D;
        if (Math.abs(vertical) < 1.5D) vertical = 0.0D;
        double max = Math.max(1.0D, Math.max(Math.abs(horizontal), Math.abs(vertical)));
        return new Vec3(horizontal / max, vertical / max, 0.0D);
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

        AABB oldBounds = new AABB(oldMin, oldMax.offset(1, 1, 1));
        List<Entity> passengers = level.getEntities((Entity) null, oldBounds,
                entity -> entity.isAlive() && !(entity instanceof net.minecraft.world.entity.decoration.HangingEntity));

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

        for (Entity entity : passengers) {
            entity.teleportTo(entity.getX() + dx, entity.getY() + dy, entity.getZ() + dz);
        }

        PowerWorldData.get(level).moveRegion(oldMin, oldMax, dx, dy, dz);
        vessel.min = oldMin.offset(dx, dy, dz);
        vessel.max = oldMax.offset(dx, dy, dz);
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
        tag.putInt("Depth", Math.max(0, level.getSeaLevel() - anchor.getY()));
        tag.putBoolean("Docked", vessel.origin.distSqr(anchor) < 16.0D && Math.abs(vessel.forwardVelocity) < 0.005D);
        tag.putIntArray("Sonar", scanSonar(level, terminalPos, terminal, vessel));
        tag.putByteArray("HullGrid", buildHullGrid(level, terminalPos, vessel));
        tag.putInt("HullColumns", STATUS_COLUMNS);
        tag.putInt("HullRows", STATUS_ROWS);
        putCrew(level, tag, terminalPos, vessel);
        putContacts(level, tag, terminalPos, terminal, vessel);
        putTargets(level, tag, terminal, anchor);
        return tag;
    }

    private int[] scanSonar(ServerLevel level, BlockPos terminalPos, TerminalState terminal, VesselState vessel) {
        int[] distances = new int[SONAR_RAYS];
        if (!terminal.activeSonar) return distances;
        Direction facing = facingAt(level, terminalPos);
        BlockPos origin = vessel.anchor();
        double maxDistance = Mth.lerp(terminal.zoom / 100.0D, 220.0D, 72.0D);
        for (int i = 0; i < SONAR_RAYS; i++) {
            double angle = Math.PI * 2.0D * i / SONAR_RAYS;
            double forward = Math.cos(angle);
            double vertical = Math.sin(angle);
            for (double distance = 3.0D; distance <= maxDistance; distance += 1.5D) {
                int x = Mth.floor(origin.getX() + facing.getStepX() * forward * distance);
                int y = Mth.floor(origin.getY() + vertical * distance);
                int z = Mth.floor(origin.getZ() + facing.getStepZ() * forward * distance);
                BlockPos sample = new BlockPos(x, y, z);
                if (vessel.contains(sample)) continue;
                BlockState state = level.getBlockState(sample);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    distances[i] = Mth.clamp((int) Math.round(distance * 100.0D / maxDistance), 1, 100);
                    break;
                }
            }
        }
        return distances;
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

    private void putContacts(ServerLevel level, CompoundTag tag, BlockPos terminalPos, TerminalState terminal, VesselState vessel) {
        BlockPos anchor = vessel.anchor();
        double range = Mth.lerp(terminal.zoom / 100.0D, 220.0D, 72.0D);
        AABB area = new AABB(anchor).inflate(range);
        Direction facing = facingAt(level, terminalPos);
        ListTag contacts = new ListTag();
        for (Entity entity : level.getEntities((Entity) null, area,
                entity -> entity.isAlive() && !vessel.contains(entity.blockPosition()) && !(entity instanceof ServerPlayer))) {
            Vec3 delta = entity.position().subtract(Vec3.atCenterOf(anchor));
            double horizontal = delta.x * facing.getStepX() + delta.z * facing.getStepZ();
            if (Math.abs(horizontal) > range || Math.abs(delta.y) > range) continue;
            if (!terminal.activeSonar && entity.getDeltaMovement().lengthSqr() < 0.0025D) continue;
            CompoundTag row = new CompoundTag();
            row.putFloat("X", (float) (horizontal / range));
            row.putFloat("Y", (float) (-delta.y / range));
            row.putFloat("Strength", (float) Mth.clamp(entity.getDeltaMovement().length() * 4.0D + 0.25D, 0.2D, 1.0D));
            row.putString("Name", entity.getDisplayName().getString());
            contacts.add(row);
            if (contacts.size() >= 48) break;
        }
        tag.put("Contacts", contacts);
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

        public UUID vesselId() { return vesselId; }
        public boolean activeSonar() { return activeSonar; }
        public boolean directional() { return directional; }
        public boolean autopilot() { return autopilot; }
        public int zoom() { return zoom; }
        public int selectedDestination() { return selectedDestination; }
        public float beamAngle() { return beamAngle; }

        public void toggleSonar() { activeSonar = !activeSonar; }
        public void toggleDirectional() { directional = !directional; }
        public void toggleAutopilot() {
            autopilot = !autopilot;
            if (!autopilot) selectedDestination = 0;
        }
        public void setZoom(int value) { zoom = Mth.clamp(value, 0, 100); }
        public void selectDestination(int value) { selectedDestination = Mth.clamp(value, 0, 12); }
        public void setManual(float forward, float vertical) {
            manualForward = Mth.clamp(forward, -1.0F, 1.0F);
            manualVertical = Mth.clamp(vertical, -1.0F, 1.0F);
        }
        public void setBeamAngle(float angle) { beamAngle = angle; }

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
        private double forwardAccumulator;
        private double verticalAccumulator;
        private long lastMoveTick;

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
            tag.putDouble("ForwardAccumulator", forwardAccumulator);
            tag.putDouble("VerticalAccumulator", verticalAccumulator);
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
            vessel.forwardAccumulator = tag.getDouble("ForwardAccumulator");
            vessel.verticalAccumulator = tag.getDouble("VerticalAccumulator");
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

    private record BlockSnapshot(BlockPos pos, BlockState state, CompoundTag blockEntityTag) {}
    private record MoveRequest(UUID vesselId, BlockPos terminalPos, int dx, int dy, int dz) {}
    private record Alias(long destination, long expiresAt) {}
}
