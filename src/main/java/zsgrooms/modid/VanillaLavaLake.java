package zsgrooms.modid;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static zsgrooms.modid.SurfacePoolRepair.Kind.*;

/** 1.16.1 LakeFeature geometry and lava lining, staged before any world writes. */
final class VanillaLavaLake {
    private VanillaLavaLake() { }

    static Map<BlockPos, SurfacePoolRepair.Kind> plan(SurfacePoolRepair.Terrain terrain, BlockPos center, long seed) {
        if (center.getY() < SurfacePoolRepair.MIN_POOL_Y || center.getY() > SurfacePoolRepair.MAX_POOL_Y
                || terrain.protectedAt(center) || !solid(terrain.kind(center))) return null;
        Shape shape = shape(new Random(seed));
        // Vanilla descends to ground, then places the bottom of its mask four blocks below it.
        BlockPos base = center.add(-8, -4, -8);
        boolean[] clearance = new boolean[24 * 24];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (!shape.cavity[index(x, 3, z)]) continue;
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                clearance[(x + dx + 4) * 24 + z + dz + 4] = true;
            }
        }
        // Keep lava away from trees, structures and other fluids without flattening a square bank.
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) {
            if (!clearance[x * 24 + z]) continue;
            for (int y = 3; y <= 10; y++) {
                BlockPos pos = base.add(x - 4, y, z - 4);
                SurfacePoolRepair.Kind kind = terrain.kind(pos);
                if (terrain.protectedAt(pos) || kind == BLOCKED || kind == WATER || kind == LAVA) return null;
            }
        }
        Map<BlockPos, SurfacePoolRepair.Kind> patch = new LinkedHashMap<>();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 8; y++) {
            int i = index(x, y, z);
            if (!shape.cavity[i] && !shape.boundary[i]) continue;
            BlockPos pos = base.add(x, y, z);
            SurfacePoolRepair.Kind kind = terrain.kind(pos);
            if (terrain.protectedAt(pos) || kind == BLOCKED || kind == LAVA || kind == WATER) return null;
            // Vanilla requires solid lower containment. Also disallow flooding pre-existing cavities.
            if (y < 4 && !solid(kind)) return null;
            if (shape.cavity[i]) patch.put(pos, y < 4 ? LAVA : CAVE_AIR);
            else if (shape.stone[i] && solid(kind)) patch.put(pos, STONE);
        }
        return patch;
    }

    static Shape shape(Random random) {
        Shape result = new Shape();
        int count = random.nextInt(4) + 4;
        for (int n = 0; n < count; n++) {
            double sizeX = random.nextDouble() * 6.0D + 3.0D;
            double sizeY = random.nextDouble() * 4.0D + 2.0D;
            double sizeZ = random.nextDouble() * 6.0D + 3.0D;
            double centerX = random.nextDouble() * (16.0D - sizeX - 2.0D) + 1.0D + sizeX / 2.0D;
            double centerY = random.nextDouble() * (8.0D - sizeY - 4.0D) + 2.0D + sizeY / 2.0D;
            double centerZ = random.nextDouble() * (16.0D - sizeZ - 2.0D) + 1.0D + sizeZ / 2.0D;
            for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) for (int y = 1; y < 7; y++) {
                double dx = (x - centerX) / (sizeX / 2.0D);
                double dy = (y - centerY) / (sizeY / 2.0D);
                double dz = (z - centerZ) / (sizeZ / 2.0D);
                if (dx * dx + dy * dy + dz * dz < 1.0D) result.cavity[index(x, y, z)] = true;
            }
        }
        // The iteration and random-call order match vanilla's stone-border pass.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 8; y++) {
            int i = index(x, y, z);
            boolean[] mask = result.cavity;
            result.boundary[i] = !mask[i] && ((x < 15 && mask[index(x + 1, y, z)])
                    || (x > 0 && mask[index(x - 1, y, z)]) || (z < 15 && mask[index(x, y, z + 1)])
                    || (z > 0 && mask[index(x, y, z - 1)]) || (y < 7 && mask[index(x, y + 1, z)])
                    || (y > 0 && mask[index(x, y - 1, z)]));
            result.stone[i] = result.boundary[i] && (y < 4 || random.nextInt(2) != 0);
        }
        return result;
    }

    private static boolean solid(SurfacePoolRepair.Kind kind) { return kind == GROUND || kind == STONE; }

    private static int index(int x, int y, int z) { return (x * 16 + z) * 8 + y; }

    static final class Shape {
        final boolean[] cavity = new boolean[2048];
        final boolean[] boundary = new boolean[2048];
        final boolean[] stone = new boolean[2048];
    }
}
