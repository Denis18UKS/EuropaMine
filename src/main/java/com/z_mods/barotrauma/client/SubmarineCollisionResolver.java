package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.entity.SubmarineContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Collision solver for a player living in the local reference frame of a detached submarine.
 *
 * The blocks of a moving submarine no longer exist in the vanilla Level, therefore vanilla
 * Entity#move cannot see them.  This class reconstructs the actual BlockState collision shapes
 * from the contraption snapshot and resolves the player's local motion against those shapes.
 * The index is built once per carrier and queried only around the player's swept box.
 */
public final class SubmarineCollisionResolver {
    private static final double EPS = 1.0E-7D;
    private static final double STEP_HEIGHT = 0.60D;
    private static final WeakHashMap<SubmarineContraptionEntity, CollisionIndex> CACHE = new WeakHashMap<>();

    private SubmarineCollisionResolver() {
    }

    public static Result resolve(SubmarineContraptionEntity ship, Vec3 startFeet, Vec3 desiredFeet,
                                 double playerWidth, double playerHeight) {
        CollisionIndex index = CACHE.computeIfAbsent(ship, CollisionIndex::new);
        double half = Math.max(0.20D, playerWidth * 0.5D);
        AABB startBox = new AABB(startFeet.x - half, startFeet.y, startFeet.z - half,
                startFeet.x + half, startFeet.y + playerHeight, startFeet.z + half);
        Vec3 wanted = desiredFeet.subtract(startFeet);

        AABB swept = expandTowards(startBox, wanted).inflate(0.08D);
        List<AABB> obstacles = index.query(swept, ship.pivotLocalX(), ship.pivotLocalZ());
        Motion normal = collide(startBox, wanted, obstacles);

        // Vanilla-like step attempt.  It makes slabs, stairs and small ledges usable instead of
        // treating every horizontal contact as a hard wall.
        if ((normal.blockedX || normal.blockedZ) && wanted.y <= 0.05D) {
            Motion stepped = collideWithStep(startBox, wanted, obstacles);
            double normalHorizontal = normal.delta.x * normal.delta.x + normal.delta.z * normal.delta.z;
            double steppedHorizontal = stepped.delta.x * stepped.delta.x + stepped.delta.z * stepped.delta.z;
            if (steppedHorizontal > normalHorizontal + 1.0E-7D) normal = stepped;
        }

        Vec3 feet = startFeet.add(normal.delta);
        boolean supported = normal.grounded || hasSupport(boxAt(feet, half, playerHeight), obstacles);
        return new Result(feet, normal.delta, normal.blockedX, normal.blockedY, normal.blockedZ, supported);
    }

    private static Motion collideWithStep(AABB start, Vec3 wanted, List<AABB> obstacles) {
        double up = clipY(start, obstacles, STEP_HEIGHT);
        if (up <= EPS) return new Motion(Vec3.ZERO, true, false, true, false, false);

        AABB raised = start.move(0.0D, up, 0.0D);
        double dx = clipX(raised, obstacles, wanted.x);
        raised = raised.move(dx, 0.0D, 0.0D);
        double dz = clipZ(raised, obstacles, wanted.z);
        raised = raised.move(0.0D, 0.0D, dz);

        // Descend back onto the nearest surface, while still honouring any requested vertical
        // movement. A little extra downward probe lets a player settle naturally onto a slab.
        double downWanted = Math.min(wanted.y - up, -up - 0.08D);
        double dyDown = clipY(raised, obstacles, downWanted);
        double dy = up + dyDown;
        boolean grounded = downWanted < 0.0D && Math.abs(dyDown - downWanted) > EPS;
        return new Motion(new Vec3(dx, dy, dz),
                Math.abs(dx - wanted.x) > EPS,
                Math.abs(dy - wanted.y) > EPS,
                Math.abs(dz - wanted.z) > EPS,
                grounded, true);
    }

    private static Motion collide(AABB start, Vec3 wanted, List<AABB> obstacles) {
        AABB box = start;
        double dy = clipY(box, obstacles, wanted.y);
        box = box.move(0.0D, dy, 0.0D);
        double dx = clipX(box, obstacles, wanted.x);
        box = box.move(dx, 0.0D, 0.0D);
        double dz = clipZ(box, obstacles, wanted.z);

        boolean blockedX = Math.abs(dx - wanted.x) > EPS;
        boolean blockedY = Math.abs(dy - wanted.y) > EPS;
        boolean blockedZ = Math.abs(dz - wanted.z) > EPS;
        boolean grounded = wanted.y < 0.0D && blockedY;
        return new Motion(new Vec3(dx, dy, dz), blockedX, blockedY, blockedZ, grounded, false);
    }

    private static double clipX(AABB box, List<AABB> obstacles, double amount) {
        if (Math.abs(amount) < EPS) return 0.0D;
        double result = amount;
        for (AABB obstacle : obstacles) {
            if (!overlaps(box.minY, box.maxY, obstacle.minY, obstacle.maxY)
                    || !overlaps(box.minZ, box.maxZ, obstacle.minZ, obstacle.maxZ)) continue;
            if (result > 0.0D && box.maxX <= obstacle.minX + EPS) {
                result = Math.min(result, obstacle.minX - box.maxX);
            } else if (result < 0.0D && box.minX >= obstacle.maxX - EPS) {
                result = Math.max(result, obstacle.maxX - box.minX);
            }
        }
        return result;
    }

    private static double clipY(AABB box, List<AABB> obstacles, double amount) {
        if (Math.abs(amount) < EPS) return 0.0D;
        double result = amount;
        for (AABB obstacle : obstacles) {
            if (!overlaps(box.minX, box.maxX, obstacle.minX, obstacle.maxX)
                    || !overlaps(box.minZ, box.maxZ, obstacle.minZ, obstacle.maxZ)) continue;
            if (result > 0.0D && box.maxY <= obstacle.minY + EPS) {
                result = Math.min(result, obstacle.minY - box.maxY);
            } else if (result < 0.0D && box.minY >= obstacle.maxY - EPS) {
                result = Math.max(result, obstacle.maxY - box.minY);
            }
        }
        return result;
    }

    private static double clipZ(AABB box, List<AABB> obstacles, double amount) {
        if (Math.abs(amount) < EPS) return 0.0D;
        double result = amount;
        for (AABB obstacle : obstacles) {
            if (!overlaps(box.minX, box.maxX, obstacle.minX, obstacle.maxX)
                    || !overlaps(box.minY, box.maxY, obstacle.minY, obstacle.maxY)) continue;
            if (result > 0.0D && box.maxZ <= obstacle.minZ + EPS) {
                result = Math.min(result, obstacle.minZ - box.maxZ);
            } else if (result < 0.0D && box.minZ >= obstacle.maxZ - EPS) {
                result = Math.max(result, obstacle.maxZ - box.minZ);
            }
        }
        return result;
    }

    private static boolean hasSupport(AABB box, List<AABB> obstacles) {
        AABB probe = box.move(0.0D, -0.045D, 0.0D);
        for (AABB obstacle : obstacles) {
            if (intersects(probe, obstacle) && box.minY >= obstacle.maxY - 0.08D) return true;
        }
        return false;
    }

    private static boolean overlaps(double a0, double a1, double b0, double b1) {
        return a1 > b0 + EPS && a0 < b1 - EPS;
    }

    private static boolean intersects(AABB a, AABB b) {
        return overlaps(a.minX, a.maxX, b.minX, b.maxX)
                && overlaps(a.minY, a.maxY, b.minY, b.maxY)
                && overlaps(a.minZ, a.maxZ, b.minZ, b.maxZ);
    }

    private static AABB expandTowards(AABB box, Vec3 delta) {
        return new AABB(
                delta.x < 0.0D ? box.minX + delta.x : box.minX,
                delta.y < 0.0D ? box.minY + delta.y : box.minY,
                delta.z < 0.0D ? box.minZ + delta.z : box.minZ,
                delta.x > 0.0D ? box.maxX + delta.x : box.maxX,
                delta.y > 0.0D ? box.maxY + delta.y : box.maxY,
                delta.z > 0.0D ? box.maxZ + delta.z : box.maxZ);
    }

    private static AABB boxAt(Vec3 feet, double half, double height) {
        return new AABB(feet.x - half, feet.y, feet.z - half,
                feet.x + half, feet.y + height, feet.z + half);
    }

    public record Result(Vec3 feet, Vec3 localMotion, boolean blockedX, boolean blockedY,
                         boolean blockedZ, boolean grounded) {
    }

    private record Motion(Vec3 delta, boolean blockedX, boolean blockedY,
                          boolean blockedZ, boolean grounded, boolean stepped) {
    }

    private static final class CollisionIndex {
        private final Map<Long, List<AABB>> columns = new HashMap<>();

        CollisionIndex(SubmarineContraptionEntity ship) {
            for (SubmarineContraptionEntity.BlockSnapshot snapshot : ship.blocks()) {
                VoxelShape shape = snapshot.state().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (shape.isEmpty()) continue;
                Vec3 origin = ship.localBlockOrigin(snapshot);
                List<AABB> column = columns.computeIfAbsent(columnKey(snapshot.x(), snapshot.z()), ignored -> new ArrayList<>());
                for (AABB box : shape.toAabbs()) {
                    column.add(box.move(origin.x, origin.y, origin.z));
                }
            }
        }

        List<AABB> query(AABB localSwept, double pivotX, double pivotZ) {
            int minX = (int)Math.floor(localSwept.minX + pivotX) - 1;
            int maxX = (int)Math.floor(localSwept.maxX + pivotX) + 1;
            int minZ = (int)Math.floor(localSwept.minZ + pivotZ) - 1;
            int maxZ = (int)Math.floor(localSwept.maxZ + pivotZ) + 1;
            List<AABB> result = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    List<AABB> list = columns.get(columnKey(x, z));
                    if (list == null) continue;
                    for (AABB box : list) {
                        if (box.maxY >= localSwept.minY - 1.0D && box.minY <= localSwept.maxY + 1.0D) result.add(box);
                    }
                }
            }
            return result;
        }

        private static long columnKey(int x, int z) {
            return ((long)x << 32) ^ (z & 0xffffffffL);
        }
    }
}
