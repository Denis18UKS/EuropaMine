package com.z_mods.barotrauma.submarines;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent library of submarine construction zones/templates. */
public final class SubmarineZoneData extends SavedData {
    private static final String DATA_NAME = "barotrauma_submarine_zones";
    private final Map<UUID, Zone> zones = new LinkedHashMap<>();

    public static SubmarineZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SubmarineZoneData::load, SubmarineZoneData::new, DATA_NAME);
    }

    public Zone add(Kind kind, BlockPos first, BlockPos second) {
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        long volume = (long)(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume < 1 || volume > 262_144L) return null;
        int number = 1 + (int)zones.values().stream().filter(zone -> zone.kind == kind).count();
        Zone zone = new Zone(UUID.randomUUID(), kind, min, max, kind.title + " " + number);
        zones.put(zone.id, zone);
        setDirty();
        return zone;
    }

    public Zone get(UUID id) { return zones.get(id); }

    public List<Zone> all() {
        List<Zone> list = new ArrayList<>(zones.values());
        list.sort(Comparator.comparing(Zone::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(list);
    }

    public boolean remove(UUID id) {
        boolean changed = zones.remove(id) != null;
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag rows = new ListTag();
        for (Zone zone : zones.values()) rows.add(zone.toTag());
        tag.put("Zones", rows);
        return tag;
    }

    public static SubmarineZoneData load(CompoundTag tag) {
        SubmarineZoneData data = new SubmarineZoneData();
        ListTag rows = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            Zone zone = Zone.fromTag(rows.getCompound(i));
            if (zone != null) data.zones.put(zone.id, zone);
        }
        return data;
    }

    public enum Kind {
        EXTERNAL_WALL("external_wall", "Внешняя стена"),
        INTERNAL_WALL("internal_wall", "Внутренняя стена"),
        FLOOR("floor", "Пол");

        private final String id;
        private final String title;
        Kind(String id, String title) { this.id = id; this.title = title; }
        public String id() { return id; }
        public String title() { return title; }
        public static Kind byId(String id) {
            for (Kind kind : values()) if (kind.id.equals(id)) return kind;
            return EXTERNAL_WALL;
        }
    }

    public record Zone(UUID id, Kind kind, BlockPos min, BlockPos max, String name) {
        public BlockPos center() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Kind", kind.id());
            tag.putLong("Min", min.asLong());
            tag.putLong("Max", max.asLong());
            tag.putString("Name", name);
            return tag;
        }

        private static Zone fromTag(CompoundTag tag) {
            if (!tag.hasUUID("Id") || !tag.contains("Min") || !tag.contains("Max")) return null;
            return new Zone(tag.getUUID("Id"), Kind.byId(tag.getString("Kind")),
                    BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")),
                    tag.getString("Name"));
        }
    }
}
