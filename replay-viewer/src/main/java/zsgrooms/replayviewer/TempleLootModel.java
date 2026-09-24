// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.Random;

/** 1.16.1 temple placement and the decorator sequence used by the ZSG-derived filter. */
final class TempleLootModel {
    private TempleLootModel() { }

    static boolean isStart(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, 32), regionZ = Math.floorDiv(chunkZ, 32);
        Random random = new Random(regionX * 341873128712L + regionZ * 132897987541L + seed + 14357617L);
        return chunkX == regionX * 32 + random.nextInt(24) && chunkZ == regionZ * 32 + random.nextInt(24);
    }

    static int chestIndex(long seed, int x, int y, int z) {
        if (y != 53) return -1;
        int chunkX = x >> 4, chunkZ = z >> 4;
        if (!isStart(seed, chunkX, chunkZ)) return -1;
        Random random = new Random(seed);
        long a = random.nextLong(), b = random.nextLong();
        random.setSeed(chunkX * a ^ chunkZ * b ^ seed);
        int orientation = random.nextInt(4); // Direction.Type.HORIZONTAL: north, east, south, west.
        for (int index = 0; index < 4; index++) {
            int localX = index == 1 ? 12 : index == 3 ? 8 : 10;
            int localZ = index == 0 ? 8 : index == 2 ? 12 : 10;
            int transformedX = orientation == 1 ? localZ : orientation == 3 ? 20 - localZ : localX;
            int transformedZ = orientation == 0 ? 20 - localZ : orientation == 2 ? localZ : localX;
            if ((x & 15) == transformedX && (z & 15) == transformedZ) return index;
        }
        return -1;
    }

    static long lootSeed(long seed, int chunkX, int chunkZ, int index) {
        if (index < 0 || index > 3) throw new IllegalArgumentException("Invalid temple chest");
        Random random = new Random(seed);
        long a = random.nextLong() | 1L, b = random.nextLong() | 1L;
        random.setSeed((((long) chunkX * 16 * a + (long) chunkZ * 16 * b) ^ seed) + 40003L);
        long loot = 0;
        for (int i = 0; i <= index; i++) loot = random.nextLong();
        return loot;
    }
}
