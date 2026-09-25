package zsgrooms.model;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.block.Block;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Late, model-only check. It does not load a Minecraft world or modify a portal. */
final class PortalCompletionModel {
    static final MCVersion VERSION = MCVersion.v1_16_1;
    static final int MIN_FIRE_CHARGES = 5;
    record Frame(BPos bottomLeft, boolean alongX, int width, int height, int missing) { }
    record Result(int status, int missing, int lava, int cast, int template, int y) { }

    static Result evaluate(long seed, int cx, int cz, int obsidian, int nuggets, int flint, int steel, int charges) {
        OverworldTerrainGenerator terrain = new OverworldTerrainGenerator(new OverworldBiomeSource(VERSION, seed));
        RuinedPortalGenerator portal = new RuinedPortalGenerator(VERSION);
        if (!portal.generate(terrain, cx, cz, new ChunkRand())
                || portal.getLocation() != RuinedPortalGenerator.Location.ON_LAND_SURFACE
                || !portal.getAirpocket() || portal.isBuried() || portal.getChestsPos().isEmpty()) {
            return new Result(0, 0, 0, 0, 0, 0);
        }
        Map<BPos, Block> blocks = new HashMap<>();
        for (Pair<Block, BPos> block : portal.getObsidian()) blocks.put(block.getSecond(), block.getFirst());
        for (Pair<Block, BPos> block : portal.getPortal()) blocks.put(block.getSecond(), block.getFirst());
        Frame frame = bestFrame(portal.getPortal(), blocks);
        int template = templateId(portal.getType());
        int lava = survivingLava(portal, terrain);
        if (frame == null) return new Result(1, 0, lava, 0, template, portal.getPos().getY());
        int cast = Math.max(0, frame.missing() - obsidian);
        boolean pass = resources(frame.missing(), obsidian, lava, nuggets, flint, steel, charges);
        // Status 3 still requires the native nearby-water model before acceptance.
        return new Result(pass ? (cast == 0 ? 4 : 3) : 2, frame.missing(), lava, cast, template, portal.getPos().getY());
    }

    static boolean resources(int missing, int obsidian, int lava, int nuggets, int flint, int steel, int charges) {
        int ignition = steel > 0 || charges >= MIN_FIRE_CHARGES ? 0 : flint > 0 ? 9 : Integer.MAX_VALUE;
        if (nuggets < ignition) return false;
        int cast = Math.max(0, missing - obsidian);
        return cast == 0 || (nuggets - ignition >= 27 && lava >= cast);
    }

    static Frame bestFrame(List<Pair<Block, BPos>> original, Map<BPos, Block> blocks) {
        if (original.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = minX, minZ = minX;
        int maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
        for (Pair<Block, BPos> item : original) {
            BPos p = item.getSecond();
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        // Fallen templates are not an existing upright frame to finish.
        if (maxY == minY || (minX != maxX && minZ != maxZ)) return null;
        boolean alongX = minZ == maxZ;
        int min = alongX ? minX : minZ, max = alongX ? maxX : maxZ;
        Frame best = null;
        // Allow shrinking a giant frame or extending a broken edge by two blocks.
        // At least three original edge blocks must be reused; this is not a new portal elsewhere.
        for (int width = 2; width <= Math.min(21, Math.max(3, max - min + 1)); width++) {
            for (int height = 3; height <= Math.min(21, Math.max(3, maxY - minY + 1)); height++) {
                for (int y = Math.max(1, minY - 2); y + height + 1 <= maxY + 2; y++) {
                    for (int u = min - 2; u + width + 1 <= max + 2; u++) {
                        BPos start = new BPos(alongX ? u : minX, y, alongX ? minZ : u);
                        int missing = missingBlocks(blocks, start, alongX, width, height);
                        if (missing >= 0 && (best == null || missing < best.missing())) {
                            best = new Frame(start, alongX, width, height, missing);
                        }
                    }
                }
            }
        }
        return best;
    }

    static int missingBlocks(Map<BPos, Block> blocks, BPos start, boolean alongX, int width, int height) {
        int missing = 0, existing = 0;
        for (int u = 0; u <= width + 1; u++) for (int y = 0; y <= height + 1; y++) {
            boolean side = u == 0 || u == width + 1, cap = y == 0 || y == height + 1;
            if (side && cap) continue; // Vanilla does not require frame corners.
            BPos p = start.add(alongX ? u : 0, y, alongX ? 0 : u);
            Block block = blocks.get(p);
            if (!side && !cap) {
                if (block != null) return -1; // No mining obsidian out of the opening.
            } else if (block == Blocks.CRYING_OBSIDIAN) return -1;
            else if (block == Blocks.OBSIDIAN) existing++;
            else missing++;
        }
        return existing >= 3 ? missing : -1;
    }

    static int templateId(String name) {
        return name.startsWith("giant_") ? 10 + Integer.parseInt(name.substring(13)) : Integer.parseInt(name.substring(7));
    }

    private static int survivingLava(RuinedPortalGenerator portal, OverworldTerrainGenerator terrain) {
        // SeedFinding does not model the cold property. Exclude cold/high-altitude
        // lava rather than treating netherrack replacements as bucketable sources.
        if (portal.getPos().getY() > 90) return 0;
        int[] coords = PortalLavaTemplates.COORDINATES[templateId(portal.getType()) - 1];
        int count = 0;
        ChunkRand random = new ChunkRand();
        for (int i = 0; i < coords.length; i += 3) {
            BPos p = new BPos(coords[i], coords[i + 1], coords[i + 2])
                    .transform(portal.getMirror(), portal.getRotation(), portal.getPivot()).add(portal.getPos());
            random.setPositionSeed(p, VERSION);
            if (random.nextFloat() < 0.2F) continue;
            // Do not count sources touching modeled natural water: they can solidify.
            boolean wet = false;
            for (int[] d : WATER_NEIGHBORS) {
                Block neighbor = terrain.getBlockAt(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]).orElse(Blocks.AIR);
                if (neighbor == Blocks.WATER) { wet = true; break; }
            }
            if (!wet) count++;
        }
        return count;
    }
    private static final int[][] WATER_NEIGHBORS = {{0,0,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0}};
}
