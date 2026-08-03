package com.z_mods.barotrauma.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Persistent per-dimension storage for GUI bindings, virtual machines and power wires.
 * A GUI can therefore be attached to any ordinary block without replacing the block itself.
 */
public final class PowerWorldData extends SavedData {
    public static final String REACTOR_GUI = "reactor";
    public static final String ELECTRICAL_PANEL_GUI = "electrical_panel";

    private static final String DATA_NAME = "barotrauma_power_world";
    private static final int MAX_FUEL = 24_000;

    private final Map<Long, String> bindings = new HashMap<>();
    private final Map<Long, MachineState> machines = new HashMap<>();
    private final Set<WireConnection> wires = new HashSet<>();
    private long ticks;

    public static PowerWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PowerWorldData::load, PowerWorldData::new, DATA_NAME);
    }

    public String guiAt(BlockPos pos) {
        return bindings.get(pos.asLong());
    }

    public MachineState machineAt(BlockPos pos) {
        return machines.get(pos.asLong());
    }

    public MachineState machineOrCreate(BlockPos pos, String guiId) {
        long key = pos.asLong();
        MachineState state = machines.computeIfAbsent(key, ignored -> new MachineState(guiId));
        if (!state.guiId.equals(guiId)) state.guiId = guiId;
        return state;
    }

    public void bind(BlockPos pos, String guiId) {
        bindings.put(pos.asLong(), guiId);
        if (REACTOR_GUI.equals(guiId) || ELECTRICAL_PANEL_GUI.equals(guiId)) {
            machineOrCreate(pos, guiId);
        } else {
            machines.remove(pos.asLong());
        }
        setChanged();
    }

    public boolean unbind(BlockPos pos) {
        boolean changed = bindings.remove(pos.asLong()) != null;
        changed |= machines.remove(pos.asLong()) != null;
        if (changed) setChanged();
        return changed;
    }

    public boolean connect(BlockPos first, BlockPos second, WireColor color) {
        if (first.equals(second)) return false;
        WireConnection connection = WireConnection.normalized(first.asLong(), second.asLong(), color);
        boolean changed = wires.add(connection);
        if (changed) setChanged();
        return changed;
    }

    public int disconnect(BlockPos pos) {
        long key = pos.asLong();
        int before = wires.size();
        wires.removeIf(connection -> connection.a == key || connection.b == key);
        int removed = before - wires.size();
        if (removed > 0) setChanged();
        return removed;
    }

    public Set<BlockPos> component(BlockPos start) {
        long startKey = start.asLong();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        visited.add(startKey);
        queue.add(startKey);
        while (!queue.isEmpty()) {
            long current = queue.remove();
            for (WireConnection connection : wires) {
                long next = Long.MIN_VALUE;
                if (connection.a == current) next = connection.b;
                else if (connection.b == current) next = connection.a;
                if (next != Long.MIN_VALUE && visited.add(next)) queue.add(next);
            }
        }
        Set<BlockPos> result = new HashSet<>();
        for (long value : visited) result.add(BlockPos.of(value));
        return result;
    }

    public CompoundTag stateTag(BlockPos pos) {
        MachineState state = machineAt(pos);
        CompoundTag tag = state == null ? new CompoundTag() : state.toTag();
        tag.putString("Gui", guiAt(pos) == null ? "" : guiAt(pos));
        tag.putLong("Pos", pos.asLong());
        tag.putInt("Connections", connectionCount(pos));
        return tag;
    }

    public int connectionCount(BlockPos pos) {
        long key = pos.asLong();
        int count = 0;
        for (WireConnection wire : wires) if (wire.a == key || wire.b == key) count++;
        return count;
    }

    public void tick(ServerLevel level) {
        ticks++;
        if (ticks % 2L == 0L) updateMachines(level);
        if (ticks % 20L == 0L) renderWireParticles(level);
    }

    private void updateMachines(ServerLevel level) {
        List<Long> exploded = new ArrayList<>();
        for (Map.Entry<Long, MachineState> entry : machines.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            MachineState state = entry.getValue();
            if (REACTOR_GUI.equals(state.guiId)) {
                double requested = requestedLoadFor(pos);
                if (state.tickReactor(requested)) {
                    warnReactor(level, pos, state);
                    if (state.shouldExplode()) {
                        explodeReactor(level, pos, state);
                        exploded.add(entry.getKey());
                    }
                }
            }
        }
        if (!exploded.isEmpty()) {
            for (long key : exploded) {
                MachineState state = machines.get(key);
                if (state != null) state.resetAfterExplosion();
            }
            setChanged();
        } else if (ticks % 20L == 0L) {
            setChanged();
        }
    }

    private double requestedLoadFor(BlockPos reactorPos) {
        double requested = 0.0D;
        for (BlockPos node : component(reactorPos)) {
            MachineState state = machineAt(node);
            if (state != null && ELECTRICAL_PANEL_GUI.equals(state.guiId)) {
                requested += state.panelDemand();
            }
        }
        return requested;
    }

    private void warnReactor(ServerLevel level, BlockPos pos, MachineState state) {
        if (!state.warningActive()) return;
        if (ticks % 10L == 0L) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                    4, 0.28D, 0.2D, 0.28D, 0.015D);
        }
        if (ticks % 30L == 0L) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.BLOCKS,
                    1.4F, state.overfuelCountdown > 0 ? 0.55F : 0.8F);
        }
    }

    private void explodeReactor(ServerLevel level, BlockPos pos, MachineState state) {
        level.sendParticles(ParticleTypes.FLAME,
                pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                80, 1.2D, 0.8D, 1.2D, 0.08D);
        level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                7.5F, Level.ExplosionInteraction.BLOCK);
    }

    private void renderWireParticles(ServerLevel level) {
        for (WireConnection wire : wires) {
            BlockPos a = BlockPos.of(wire.a);
            BlockPos b = BlockPos.of(wire.b);
            if (!level.hasChunkAt(a) && !level.hasChunkAt(b)) continue;
            DustParticleOptions particle = wire.color == WireColor.RED
                    ? new DustParticleOptions(new Vector3f(1.0F, 0.08F, 0.04F), 0.8F)
                    : new DustParticleOptions(new Vector3f(0.08F, 0.35F, 1.0F), 0.8F);
            double ax = a.getX() + 0.5D;
            double ay = a.getY() + 0.65D;
            double az = a.getZ() + 0.5D;
            double bx = b.getX() + 0.5D;
            double by = b.getY() + 0.65D;
            double bz = b.getZ() + 0.5D;
            int steps = Mth.clamp((int) Math.ceil(Math.sqrt(a.distSqr(b))) * 3, 4, 80);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                level.sendParticles(particle,
                        Mth.lerp(t, ax, bx), Mth.lerp(t, ay, by), Mth.lerp(t, az, bz),
                        1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag bindingsTag = new ListTag();
        for (Map.Entry<Long, String> entry : bindings.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putLong("Pos", entry.getKey());
            row.putString("Gui", entry.getValue());
            bindingsTag.add(row);
        }
        tag.put("Bindings", bindingsTag);

        ListTag machinesTag = new ListTag();
        for (Map.Entry<Long, MachineState> entry : machines.entrySet()) {
            CompoundTag row = entry.getValue().toTag();
            row.putLong("Pos", entry.getKey());
            machinesTag.add(row);
        }
        tag.put("Machines", machinesTag);

        ListTag wiresTag = new ListTag();
        for (WireConnection wire : wires) {
            CompoundTag row = new CompoundTag();
            row.putLong("A", wire.a);
            row.putLong("B", wire.b);
            row.putByte("Color", (byte) wire.color.ordinal());
            wiresTag.add(row);
        }
        tag.put("Wires", wiresTag);
        return tag;
    }

    public static PowerWorldData load(CompoundTag tag) {
        PowerWorldData data = new PowerWorldData();
        ListTag bindingsTag = tag.getList("Bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < bindingsTag.size(); i++) {
            CompoundTag row = bindingsTag.getCompound(i);
            data.bindings.put(row.getLong("Pos"), row.getString("Gui"));
        }
        ListTag machinesTag = tag.getList("Machines", Tag.TAG_COMPOUND);
        for (int i = 0; i < machinesTag.size(); i++) {
            CompoundTag row = machinesTag.getCompound(i);
            data.machines.put(row.getLong("Pos"), MachineState.fromTag(row));
        }
        ListTag wiresTag = tag.getList("Wires", Tag.TAG_COMPOUND);
        for (int i = 0; i < wiresTag.size(); i++) {
            CompoundTag row = wiresTag.getCompound(i);
            int color = Mth.clamp(row.getByte("Color"), 0, WireColor.values().length - 1);
            data.wires.add(WireConnection.normalized(row.getLong("A"), row.getLong("B"), WireColor.values()[color]));
        }
        return data;
    }

    public enum WireColor {
        RED,
        BLUE
    }

    private record WireConnection(long a, long b, WireColor color) {
        static WireConnection normalized(long first, long second, WireColor color) {
            return first <= second ? new WireConnection(first, second, color) : new WireConnection(second, first, color);
        }
    }

    public static final class MachineState {
        private String guiId;
        private boolean enabled;
        private boolean automatic;
        private int target = 35;
        private double fission;
        private double output;
        private double load;
        private double temperature = 18.0D;
        private double burnAccumulator;
        private final int[] fuel = new int[4];
        private int overfuelCountdown;
        private int meltdownCountdown;
        private int health = 100;
        private int sabotageTicks;

        private MachineState(String guiId) {
            this.guiId = guiId;
        }

        public String guiId() {
            return guiId;
        }

        public boolean enabled() {
            return enabled;
        }

        public void toggleEnabled() {
            enabled = !enabled;
        }

        public boolean automatic() {
            return automatic;
        }

        public void toggleAutomatic() {
            automatic = !automatic;
        }

        public int target() {
            return target;
        }

        public void adjustTarget(int delta) {
            target = Mth.clamp(target + delta, 0, 100);
        }

        public double fission() {
            return fission;
        }

        public double output() {
            return output;
        }

        public double load() {
            return load;
        }

        public double temperature() {
            return temperature;
        }

        public int fuel(int slot) {
            return fuel[Mth.clamp(slot, 0, fuel.length - 1)];
        }

        public void setFuel(int slot, int remaining) {
            fuel[Mth.clamp(slot, 0, fuel.length - 1)] = Mth.clamp(remaining, 0, MAX_FUEL);
        }

        public boolean hasFuel() {
            for (int value : fuel) if (value > 0) return true;
            return false;
        }

        public int firstFuelSlot() {
            for (int i = 0; i < fuel.length; i++) if (fuel[i] > 0) return i;
            return -1;
        }

        public void triggerOverfuel() {
            if (overfuelCountdown <= 0) overfuelCountdown = 1;
        }

        public int health() {
            return health;
        }

        public void repair(int amount) {
            health = Mth.clamp(health + amount, 0, 100);
            if (health > 0) sabotageTicks = 0;
        }

        public void sabotage(int amount) {
            health = Mth.clamp(health - amount, 0, 100);
            sabotageTicks = 200;
        }

        public double panelDemand() {
            if (health <= 0) return 0.0D;
            double integrityFactor = 0.35D + 0.65D * (health / 100.0D);
            return 1494.0D * integrityFactor;
        }

        private boolean tickReactor(double requestedLoad) {
            boolean changed = false;
            if (automatic && enabled) {
                int automaticTarget = requestedLoad <= 0.0D ? 8 : Mth.clamp((int) Math.ceil(requestedLoad / 30.0D), 5, 100);
                if (target != automaticTarget) {
                    target += Integer.compare(automaticTarget, target);
                    changed = true;
                }
            }

            double desiredFission = enabled && hasFuel() ? target : 0.0D;
            double previousFission = fission;
            fission += (desiredFission - fission) * (enabled ? 0.045D : 0.09D);
            if (Math.abs(previousFission - fission) > 0.001D) changed = true;

            output = enabled && hasFuel() ? Math.max(0.0D, fission * 30.0D) : 0.0D;
            load = Math.min(output, Math.max(0.0D, requestedLoad));
            double excess = Math.max(0.0D, output - load);

            if (enabled && hasFuel()) {
                burnAccumulator += Math.max(0.2D, fission / 22.0D);
                while (burnAccumulator >= 1.0D) {
                    burnAccumulator -= 1.0D;
                    int slot = firstFuelSlot();
                    if (slot >= 0) fuel[slot] = Math.max(0, fuel[slot] - 1);
                }
            }

            double heatGain = enabled ? fission * 0.014D + excess * 0.00065D : 0.0D;
            double cooling = automatic ? 0.72D : 0.48D;
            temperature += heatGain - cooling;
            if (!enabled) temperature += (18.0D - temperature) * 0.035D;
            temperature = Mth.clamp(temperature, -40.0D, 220.0D);

            boolean dangerousOutput = output > 2700.0D && excess > 650.0D;
            if (temperature > 112.0D || dangerousOutput) {
                meltdownCountdown = meltdownCountdown <= 0 ? 1 : meltdownCountdown + 1;
            } else if (meltdownCountdown > 0) {
                meltdownCountdown = Math.max(0, meltdownCountdown - 2);
            }
            if (overfuelCountdown > 0) overfuelCountdown++;
            if (sabotageTicks > 0) sabotageTicks--;
            return changed || warningActive();
        }

        public boolean warningActive() {
            return overfuelCountdown > 0 || meltdownCountdown > 0;
        }

        public boolean shouldExplode() {
            return overfuelCountdown >= 120 || meltdownCountdown >= 200;
        }

        public void resetAfterExplosion() {
            enabled = false;
            automatic = false;
            target = 0;
            fission = 0;
            output = 0;
            load = 0;
            temperature = 18;
            overfuelCountdown = 0;
            meltdownCountdown = 0;
            health = 0;
            for (int i = 0; i < fuel.length; i++) fuel[i] = 0;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Gui", guiId);
            tag.putBoolean("Enabled", enabled);
            tag.putBoolean("Automatic", automatic);
            tag.putInt("Target", target);
            tag.putDouble("Fission", fission);
            tag.putDouble("Output", output);
            tag.putDouble("Load", load);
            tag.putDouble("Temperature", temperature);
            tag.putDouble("BurnAccumulator", burnAccumulator);
            for (int i = 0; i < fuel.length; i++) tag.putInt("Fuel" + i, fuel[i]);
            tag.putInt("Overfuel", overfuelCountdown);
            tag.putInt("Meltdown", meltdownCountdown);
            tag.putInt("Health", health);
            tag.putInt("SabotageTicks", sabotageTicks);
            return tag;
        }

        private static MachineState fromTag(CompoundTag tag) {
            MachineState state = new MachineState(tag.getString("Gui"));
            state.enabled = tag.getBoolean("Enabled");
            state.automatic = tag.getBoolean("Automatic");
            state.target = Mth.clamp(tag.getInt("Target"), 0, 100);
            state.fission = tag.getDouble("Fission");
            state.output = tag.getDouble("Output");
            state.load = tag.getDouble("Load");
            state.temperature = tag.contains("Temperature") ? tag.getDouble("Temperature") : 18.0D;
            state.burnAccumulator = tag.getDouble("BurnAccumulator");
            for (int i = 0; i < state.fuel.length; i++) state.fuel[i] = Mth.clamp(tag.getInt("Fuel" + i), 0, MAX_FUEL);
            state.overfuelCountdown = tag.getInt("Overfuel");
            state.meltdownCountdown = tag.getInt("Meltdown");
            state.health = tag.contains("Health") ? Mth.clamp(tag.getInt("Health"), 0, 100) : 100;
            state.sabotageTicks = tag.getInt("SabotageTicks");
            return state;
        }
    }
}
