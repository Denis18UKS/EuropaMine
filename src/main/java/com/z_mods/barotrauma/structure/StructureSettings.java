package com.z_mods.barotrauma.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class StructureSettings {
    public static final String TAG = "BarotraumaStructureConfig";
    private static final String KIND_KEY = "Kind";
    private static final String WALL_TYPE_KEY = "WallType";
    private static final String UNBREAKABLE_KEY = "Unbreakable";
    private static final String NO_AI_TARGET_KEY = "NoAiTarget";
    private static final String HEALTH_KEY = "Health";

    private StructureSettings() {
    }

    public static Config read(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return new Config(Kind.PLATFORM, WallType.INTERNAL, false, false, 100.0F);
        }

        return new Config(
                Kind.byId(tag.getString(KIND_KEY)),
                WallType.byId(tag.getString(WALL_TYPE_KEY)),
                tag.getBoolean(UNBREAKABLE_KEY),
                tag.getBoolean(NO_AI_TARGET_KEY),
                tag.contains(HEALTH_KEY) ? tag.getFloat(HEALTH_KEY) : 100.0F
        );
    }

    public static void write(ItemStack stack, Config config) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putString(KIND_KEY, config.kind().id);
        tag.putString(WALL_TYPE_KEY, config.wallType().id);
        tag.putBoolean(UNBREAKABLE_KEY, config.unbreakable());
        tag.putBoolean(NO_AI_TARGET_KEY, config.noAiTarget());
        tag.putFloat(HEALTH_KEY, Math.max(0.0F, config.health()));
    }

    public enum Kind {
        PLATFORM("platform"),
        WALL("wall");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Kind byId(String id) {
            return WALL.id.equals(id) ? WALL : PLATFORM;
        }
    }

    public enum WallType {
        INTERNAL("internal"),
        EXTERNAL("external");

        private final String id;

        WallType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static WallType byId(String id) {
            return EXTERNAL.id.equals(id) ? EXTERNAL : INTERNAL;
        }
    }

    public record Config(Kind kind, WallType wallType, boolean unbreakable, boolean noAiTarget, float health) {
    }
}
