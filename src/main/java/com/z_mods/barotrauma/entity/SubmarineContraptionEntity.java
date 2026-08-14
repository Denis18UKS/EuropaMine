package com.z_mods.barotrauma.entity;

import com.z_mods.barotrauma.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One render/physics carrier for a moving submarine. The world blocks are captured once and then
 * rendered from local coordinates, so the hull is no longer re-created one block at a time while
 * travelling. Translation, yaw and pitch are continuous entity transforms.
 */
public final class SubmarineContraptionEntity extends Entity implements IEntityAdditionalSpawnData {
    private final List<BlockSnapshot> blocks = new ArrayList<>();
    private UUID vesselId;
    private int sizeX = 1;
    private int sizeY = 1;
    private int sizeZ = 1;
    private double pivotLocalX = 0.5D;
    private double pivotLocalY = 0.5D;
    private double pivotLocalZ = 0.5D;

    public SubmarineContraptionEntity(EntityType<? extends SubmarineContraptionEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public static SubmarineContraptionEntity capture(ServerLevel level, UUID vesselId,
                                                       BlockPos min, BlockPos max) {
        SubmarineContraptionEntity entity = new SubmarineContraptionEntity(ModEntities.SUBMARINE_CONTRAPTION.get(), level);
        entity.vesselId = vesselId;
        entity.sizeX = max.getX() - min.getX() + 1;
        entity.sizeY = max.getY() - min.getY() + 1;
        entity.sizeZ = max.getZ() - min.getZ() + 1;
        entity.pivotLocalX = entity.sizeX * 0.5D;
        entity.pivotLocalY = entity.sizeY * 0.5D;
        entity.pivotLocalZ = entity.sizeZ * 0.5D;

        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) continue;
            CompoundTag blockEntityTag = null;
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (blockEntity != null) blockEntityTag = blockEntity.saveWithFullMetadata();
            entity.blocks.add(new BlockSnapshot(
                    cursor.getX() - min.getX(),
                    cursor.getY() - min.getY(),
                    cursor.getZ() - min.getZ(),
                    Block.BLOCK_STATE_REGISTRY.getId(state), blockEntityTag));
        }
        if (entity.blocks.isEmpty()) return null;

        double centerX = min.getX() + entity.pivotLocalX;
        double centerY = min.getY() + entity.pivotLocalY;
        double centerZ = min.getZ() + entity.pivotLocalZ;
        entity.setPos(centerX, centerY, centerZ);

        // Remove after the complete snapshot exists. BlockEntity data stays inside the carrier and
        // can be materialised again later without losing inventories/settings.
        for (BlockSnapshot snapshot : entity.blocks) {
            BlockPos source = min.offset(snapshot.x, snapshot.y, snapshot.z);
            level.setBlock(source, Blocks.AIR.defaultBlockState(), 18);
        }
        level.addFreshEntity(entity);
        return entity;
    }

    public UUID vesselId() { return vesselId; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public double pivotLocalX() { return pivotLocalX; }
    public double pivotLocalY() { return pivotLocalY; }
    public double pivotLocalZ() { return pivotLocalZ; }
    public List<BlockSnapshot> blocks() { return Collections.unmodifiableList(blocks); }

    public Vec3 localBlockOrigin(BlockSnapshot snapshot) {
        return new Vec3(snapshot.x - pivotLocalX, snapshot.y - pivotLocalY, snapshot.z - pivotLocalZ);
    }

    public Vec3 localBlockCenter(BlockSnapshot snapshot) {
        Vec3 origin = localBlockOrigin(snapshot);
        return origin.add(0.5D, 0.5D, 0.5D);
    }

    /** Highest local block top directly under the supplied local feet position. */
    public double supportHeight(double localX, double localY, double localZ) {
        double best = Double.NaN;
        for (BlockSnapshot snapshot : blocks) {
            BlockState state = snapshot.state();
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            double x0 = snapshot.x - pivotLocalX;
            double y0 = snapshot.y - pivotLocalY;
            double z0 = snapshot.z - pivotLocalZ;
            if (localX < x0 - 0.30D || localX > x0 + 1.30D
                    || localZ < z0 - 0.30D || localZ > z0 + 1.30D) continue;
            double top = y0 + 1.0D;
            if (top > localY + 0.35D || top < localY - 1.25D) continue;
            if (Double.isNaN(best) || top > best) best = top;
        }
        return best;
    }

    /** Local (unrotated) hull bounds around the entity pivot. */
    public AABB localBounds() {
        return new AABB(-pivotLocalX, -pivotLocalY, -pivotLocalZ,
                sizeX - pivotLocalX, sizeY - pivotLocalY, sizeZ - pivotLocalZ);
    }

    /** Conservative world AABB of the rotated cuboid, used for carried entities and obstacle tests. */
    public AABB transformedBounds(double x, double y, double z, float yaw, float pitch) {
        AABB local = localBounds();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
                for (int iz = 0; iz < 2; iz++) {
                    Vec3 corner = new Vec3(ix == 0 ? local.minX : local.maxX,
                            iy == 0 ? local.minY : local.maxY,
                            iz == 0 ? local.minZ : local.maxZ);
                    Vec3 rotated = rotateLocal(corner, yaw, pitch).add(x, y, z);
                    minX = Math.min(minX, rotated.x); minY = Math.min(minY, rotated.y); minZ = Math.min(minZ, rotated.z);
                    maxX = Math.max(maxX, rotated.x); maxY = Math.max(maxY, rotated.y); maxZ = Math.max(maxZ, rotated.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Vec3 localToWorld(Vec3 local, double x, double y, double z, float yaw, float pitch) {
        return rotateLocal(local, yaw, pitch).add(x, y, z);
    }

    public Vec3 worldToLocal(Vec3 world, double x, double y, double z, float yaw, float pitch) {
        return inverseRotate(world.subtract(x, y, z), yaw, pitch);
    }

    /** Forward vector of local +X after yaw/pitch. */
    public static Vec3 forward(float yaw, float pitch) {
        return rotateLocal(new Vec3(1.0D, 0.0D, 0.0D), yaw, pitch).normalize();
    }

    public static Vec3 rotateLocal(Vec3 local, float yawDegrees, float pitchDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        // Pitch around local +Z: +pitch raises local +X (the bow).
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double px = local.x * cp - local.y * sp;
        double py = local.x * sp + local.y * cp;
        double pz = local.z;
        // Yaw around world +Y: +90 rotates +X towards +Z.
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        return new Vec3(px * cy - pz * sy, py, px * sy + pz * cy);
    }

    public static Vec3 inverseRotate(Vec3 worldDelta, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(-yawDegrees);
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double yx = worldDelta.x * cy - worldDelta.z * sy;
        double yy = worldDelta.y;
        double yz = worldDelta.x * sy + worldDelta.z * cy;

        double pitch = Math.toRadians(-pitchDegrees);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        return new Vec3(yx * cp - yy * sp, yx * sp + yy * cp, yz);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        blocks.clear();
        if (tag.hasUUID("VesselId")) vesselId = tag.getUUID("VesselId");
        sizeX = Math.max(1, tag.getInt("SizeX"));
        sizeY = Math.max(1, tag.getInt("SizeY"));
        sizeZ = Math.max(1, tag.getInt("SizeZ"));
        pivotLocalX = tag.contains("PivotX") ? tag.getDouble("PivotX") : sizeX * 0.5D;
        pivotLocalY = tag.contains("PivotY") ? tag.getDouble("PivotY") : sizeY * 0.5D;
        pivotLocalZ = tag.contains("PivotZ") ? tag.getDouble("PivotZ") : sizeZ * 0.5D;
        ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) blocks.add(BlockSnapshot.fromTag(list.getCompound(i)));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (vesselId != null) tag.putUUID("VesselId", vesselId);
        tag.putInt("SizeX", sizeX);
        tag.putInt("SizeY", sizeY);
        tag.putInt("SizeZ", sizeZ);
        tag.putDouble("PivotX", pivotLocalX);
        tag.putDouble("PivotY", pivotLocalY);
        tag.putDouble("PivotZ", pivotLocalZ);
        ListTag list = new ListTag();
        for (BlockSnapshot snapshot : blocks) list.add(snapshot.toTag());
        tag.put("Blocks", list);
    }

    @Override
    public void tick() {
        xOld = getX();
        yOld = getY();
        zOld = getZ();
        yRotO = getYRot();
        xRotO = getXRot();
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeBoolean(vesselId != null);
        if (vesselId != null) buffer.writeUUID(vesselId);
        buffer.writeVarInt(sizeX);
        buffer.writeVarInt(sizeY);
        buffer.writeVarInt(sizeZ);
        buffer.writeDouble(pivotLocalX);
        buffer.writeDouble(pivotLocalY);
        buffer.writeDouble(pivotLocalZ);
        buffer.writeVarInt(blocks.size());
        for (BlockSnapshot snapshot : blocks) {
            buffer.writeVarInt(snapshot.x);
            buffer.writeVarInt(snapshot.y);
            buffer.writeVarInt(snapshot.z);
            buffer.writeVarInt(snapshot.stateId);
            buffer.writeBoolean(snapshot.blockEntityTag != null);
            if (snapshot.blockEntityTag != null) buffer.writeNbt(snapshot.blockEntityTag);
        }
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        blocks.clear();
        vesselId = buffer.readBoolean() ? buffer.readUUID() : null;
        sizeX = Math.max(1, buffer.readVarInt());
        sizeY = Math.max(1, buffer.readVarInt());
        sizeZ = Math.max(1, buffer.readVarInt());
        pivotLocalX = buffer.readDouble();
        pivotLocalY = buffer.readDouble();
        pivotLocalZ = buffer.readDouble();
        int count = Math.min(buffer.readVarInt(), 32_768);
        for (int i = 0; i < count; i++) {
            int x = buffer.readVarInt();
            int y = buffer.readVarInt();
            int z = buffer.readVarInt();
            int state = buffer.readVarInt();
            CompoundTag be = buffer.readBoolean() ? buffer.readNbt() : null;
            blocks.add(new BlockSnapshot(x, y, z, state, be));
        }
    }

    public record BlockSnapshot(int x, int y, int z, int stateId, @Nullable CompoundTag blockEntityTag) {
        public BlockState state() {
            BlockState state = Block.stateById(stateId);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", x);
            tag.putInt("Y", y);
            tag.putInt("Z", z);
            tag.putInt("State", stateId);
            if (blockEntityTag != null) tag.put("BlockEntity", blockEntityTag.copy());
            return tag;
        }

        static BlockSnapshot fromTag(CompoundTag tag) {
            return new BlockSnapshot(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"), tag.getInt("State"),
                    tag.contains("BlockEntity", Tag.TAG_COMPOUND) ? tag.getCompound("BlockEntity").copy() : null);
        }
    }
}
