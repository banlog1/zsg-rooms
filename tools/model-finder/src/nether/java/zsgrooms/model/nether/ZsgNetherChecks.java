package zsgrooms.model.nether;

import Xinyuiii.enumType.BastionType;
import Xinyuiii.properties.BastionGenerator;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.block.Block;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.block.BlockDirection;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcterrain.TerrainGenerator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapted from ZSGJavaBits at 073e1d1f3150213c7c3787eda7ef8106cb789817.
 * Copyright (c) 2024 DuncanRuns, MIT; see tools/filter-worker/THIRD_PARTY.md.
 * Only the binary transport and mutable ChestInfo holder are removed here.
 */
final class ZsgNetherChecks {
    private static final Set<Block> AIR = new HashSet<>(Arrays.asList(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR));

    private ZsgNetherChecks() { }

    static int obsidianScore(BastionGenerator generator, CPos bastionPos) {
        BastionType type = generator.getType();
        BPos origin = bastionPos.toBlockPos();
        BlockRotation rotation = generator.getPieces().get(0).rotation;
        int obsidian = generator.generateLoot().stream().filter(pair ->
                eligibleChest(type, pair.getFirst(), origin, rotation)
        ).mapToInt(pair -> pair.getSecond().stream()
                .filter(stack -> stack.getItem().getName().equals("obsidian"))
                .mapToInt(ItemStack::getCount).sum()).sum() + expectedTradedObsidian(type);
        return Math.min(obsidian, Byte.MAX_VALUE);
    }

    static int expectedTradedObsidian(BastionType type) {
        return type == BastionType.BRIDGE ? 7 : 4;
    }

    static boolean eligibleChest(BastionType type, BPos pos, BPos origin, BlockRotation rotation) {
        int y = pos.getY();
        switch (type) {
            case TREASURE: return y == 82;
            case BRIDGE: return true;
            case STABLES: return y == 35 || y == 72;
            case HOUSING:
                if (y == 73) return true;
                if (y == 36) {
                    BlockDirection direction = rotation.rotate(BlockDirection.SOUTH);
                    BPos chest = origin.relative(direction, 20).relative(direction.getClockWise(), 6);
                    if (pos.getX() == chest.getX() && pos.getZ() == chest.getZ()) return true;
                    chest = chest.relative(direction);
                    return pos.getX() == chest.getX() && pos.getZ() == chest.getZ();
                }
                return false;
            default: throw new IllegalArgumentException("Unknown bastion type");
        }
    }

    static boolean terrain(long seed, int bastionChunkX, int bastionChunkZ, int fortressChunkX, int fortressChunkZ) {
        int bx = bastionChunkX * 16, bz = bastionChunkZ * 16;
        int fx = fortressChunkX * 16 + 11, fz = fortressChunkZ * 16 + 11;
        TerrainGenerator gen = TerrainGenerator.of(BiomeSource.of(Dimension.NETHER, MCVersion.v1_16_1, seed));
        boolean highFort = false;
        boolean viable = (route(gen, bx, bz, fx, fz, 60) || (highFort = route(gen, bx, bz, fx, fz, 95)))
                && (route(gen, 0, 0, bx, bz, 60) || route(gen, 0, 0, bx, bz, 95));
        if (viable && highFort) {
            int airCount = 0;
            for (int y = 95; y >= 50; y -= 5) {
                if (AIR.contains(gen.getBlockAt(fx, y, fz).orElse(Blocks.NETHERRACK))) airCount++;
            }
            viable = airCount >= 6;
        }
        return viable;
    }

    private static boolean route(TerrainGenerator gen, int x1, int z1, int x2, int z2, int y) {
        int dx = x2 - x1, dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int samples = (int) (distance / 10) + 1;
        int airCount = 0;
        // Preserve upstream interpolation/casts, including its coincident-endpoint behavior.
        for (int i = 0; i < samples; i++) {
            double t = (double) i / (samples - 1);
            int x = (int) (x1 + dx * t), z = (int) (z1 + dz * t);
            if (AIR.contains(gen.getBlockAt(x, y, z).orElse(Blocks.NETHERRACK))) airCount++;
        }
        return (double) airCount / samples >= 0.8;
    }
}
