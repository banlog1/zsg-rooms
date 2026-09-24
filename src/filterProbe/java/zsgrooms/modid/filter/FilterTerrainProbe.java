package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.ZsgRooms;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Offline investigation only: a passing terrain check is not a passing ZSG seed. */
public final class FilterTerrainProbe implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(FilterTerrainProbe::run);
    }

    private static void run(MinecraftServer server) {
        if (Boolean.getBoolean("zsgrooms.portalRepairCalibration")) {
            RuinedPortalRepairCalibration.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.villageCalibration")) {
            VillageLootCalibration.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.poolCalibration")) {
            SurfacePoolCalibration.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.spawnCalibration")) {
            SpawnModelCalibration.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.filterVerifierWorker")) {
            FilterVerifierWorker.run(server);
            return;
        }
        if (!System.getProperty("zsgrooms.filterVerifierTest", "").isEmpty()) {
            FilterVerifierParityTest.run(server, System.getProperty("zsgrooms.filterVerifierTest"));
            return;
        }
        if (Boolean.getBoolean("zsgrooms.filterEarlyTest")) {
            EarlyFilterChecksTest.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.filterLayoutTest")) {
            BastionLayoutPredictionTest.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.filterVerify")) {
            FilterBankVerifier.run(server);
            return;
        }
        if (Boolean.getBoolean("zsgrooms.filterBank")) {
            OfflineBankRunner.run(server);
            return;
        }
        JsonObject report = new JsonObject();
        report.addProperty("formatVersion", 1);
        report.addProperty("status", "VALIDATING");
        JsonArray rejected = new JsonArray();
        report.add("rejections", rejected);
        long validationStarted = System.nanoTime();
        try {
            FilterProbeFiles.write(Paths.get("validation.json"), report);
            JsonObject candidate = null;
            if (Boolean.getBoolean("zsgrooms.filterProbe.candidate")) {
                try (Reader reader = Files.newBufferedReader(Paths.get("candidate.json"))) {
                    candidate = new JsonParser().parse(reader).getAsJsonObject();
                }
                if (candidate.get("formatVersion").getAsInt() != FilterCandidateSearch.FORMAT_VERSION
                        || !"PRELIMINARY".equals(candidate.get("status").getAsString())) {
                    throw new IllegalStateException("Candidate is not a supported preliminary result");
                }
                report.addProperty("candidateId", java.util.UUID.fromString(candidate.get("id").getAsString()).toString());
            }
            String type = System.getProperty("zsgrooms.filterProbe.type", "village");
            if (candidate != null) type = candidate.get("type").getAsString().toLowerCase(java.util.Locale.ROOT);
            StructureFeature<?> structure;
            if ("village".equals(type)) structure = StructureFeature.VILLAGE;
            else if ("temple".equals(type)) structure = StructureFeature.DESERT_PYRAMID;
            else if ("shipwreck".equals(type)) structure = StructureFeature.SHIPWRECK;
            else throw new IllegalArgumentException("Probe type must be village, temple or shipwreck");
            report.addProperty("type", type);
            int x = Integer.parseInt(System.getProperty("zsgrooms.filterProbe.x", "0"));
            int z = Integer.parseInt(System.getProperty("zsgrooms.filterProbe.z", "0"));
            if (Math.abs((long) x) > 29000000 || Math.abs((long) z) > 29000000) {
                throw new IllegalArgumentException("Probe search origin is outside supported bounds");
            }
            ServerWorld world = server.getOverworld();
            Properties settings = new Properties();
            try (Reader reader = Files.newBufferedReader(Paths.get("server.properties"))) {
                settings.load(reader);
            }
            if (world.getSeed() != Long.parseLong(settings.getProperty("level-seed"))) {
                throw new IllegalStateException("Probe save does not match the requested seed");
            }
            long started = System.nanoTime();
            BlockPos anchor;
            if (candidate == null) {
                anchor = world.locateStructure(structure, new BlockPos(x, 64, z), 8, false);
            } else {
                if (world.getSeed() != Long.parseLong(candidate.get("seed").getAsString())) {
                    throw new IllegalStateException("Candidate seed does not match the loaded probe world");
                }
                int chunkX = candidate.get("mainChunkX").getAsInt();
                int chunkZ = candidate.get("mainChunkZ").getAsInt();
                anchor = hasStart(world, structure, chunkX, chunkZ) ? new BlockPos(chunkX << 4, 64, chunkZ << 4) : null;
            }
            long located = System.nanoTime();
            if (candidate != null) {
                ServerWorld nether = server.getWorld(World.NETHER);
                boolean bastion = hasStart(nether, StructureFeature.BASTION_REMNANT,
                        candidate.get("bastionChunkX").getAsInt(), candidate.get("bastionChunkZ").getAsInt());
                boolean fortress = hasStart(nether, StructureFeature.FORTRESS,
                        candidate.get("fortressChunkX").getAsInt(), candidate.get("fortressChunkZ").getAsInt());
                ZsgRooms.LOGGER.info("[FilterProbe] Predicted Nether starts: bastion={}, fortress={}, checkMs={}",
                        bastion, fortress, millis(located, System.nanoTime()));
                if (!bastion) rejected.add("BASTION_START");
                if (!fortress) rejected.add("FORTRESS_START");
            }
            long terrainStarted = System.nanoTime();
            if (anchor == null) {
                rejected.add("MAIN_START");
                verifyFixtures(world, new BlockPos(x, 64, z));
                ZsgRooms.LOGGER.info("[FilterProbe] COMPLETE: type={}, structureFound=false, locateMs={}",
                        type, millis(started, located));
                return;
            }
            MinecraftSurfaceTerrain terrain = MinecraftSurfaceTerrain.capture(world, anchor.getX(), anchor.getZ(),
                    "temple".equals(type) ? 96 + TempleCandidateChecks.WOOD_RADIUS : 96);
            long captured = System.nanoTime();
            Optional<SurfaceTerrainChecks.LavaPool> pool = SurfaceTerrainChecks.findLavaPool(terrain,
                    anchor.getX(), anchor.getZ(), SurfaceTerrainChecks.LAVA_POOL_RADIUS);
            Optional<SurfaceTerrainChecks.WoodedLand> woods = SurfaceTerrainChecks.findWoodedLand(terrain,
                    anchor.getX(), anchor.getZ(), SurfaceTerrainChecks.WOODED_LAND_RADIUS);
            if ("temple".equals(type)) {
                StructureStart<?> start = world.getStructureAccessor().getStructureStart(
                        ChunkSectionPos.from(anchor.getX() >> 4, 0, anchor.getZ() >> 4), structure,
                        world.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS));
                TempleLootProbe loot = TempleLootProbe.read(world, start);
                TempleCandidateChecks.Result result = TempleCandidateChecks.check(terrain, anchor, world.getSpawnPos(),
                        loot.chests, loot.iron, loot.diamonds);
                for (TempleCandidateChecks.Rejection reason : result.rejections) rejected.add(reason.name());
                report.addProperty("templeChests", loot.chests);
                report.addProperty("templeIron", loot.iron);
                report.addProperty("templeDiamonds", loot.diamonds);
                report.addProperty("surfacePools", result.surfacePools);
                report.addProperty("suitablePool", result.pool().isPresent());
                ZsgRooms.LOGGER.info("[FilterProbe] Temple: chests={}, iron={}, diamonds={}, pools={}, suitablePool={}, rejections={}",
                        loot.chests, loot.iron, loot.diamonds, result.surfacePools, result.pool().isPresent(), result.rejections);
            } else {
                if ("village".equals(type) && !pool.isPresent()) rejected.add("NO_SURFACE_POOL");
                if (!woods.isPresent()) rejected.add("NO_WOODED_LAND");
            }
            long checked = System.nanoTime();
            report.addProperty("captureMs", millis(terrainStarted, captured));
            report.addProperty("checkMs", millis(captured, checked));
            verifyFixtures(world, anchor);
            ZsgRooms.LOGGER.info("[FilterProbe] COMPLETE: type={}, structureFound=true, surfacePool={}, "
                            + "exposedSources={}, woodedLand={}, locateMs={}, captureMs={}, checkMs={}",
                    type, pool.isPresent(), pool.map(value -> value.exposedSources).orElse(0), woods.isPresent(),
                    millis(started, located), millis(terrainStarted, captured), millis(captured, checked));
        } catch (Throwable failure) {
            report.addProperty("status", "ERROR");
            report.addProperty("error", failure.getClass().getSimpleName());
            ZsgRooms.LOGGER.error("[FilterProbe] FAIL: " + failure.getClass().getSimpleName(), failure);
        } finally {
            if (!"ERROR".equals(report.get("status").getAsString())) {
                report.addProperty("status", rejected.size() == 0 ? "PARTIAL_PASS" : "REJECTED");
            }
            report.addProperty("validationMs", millis(validationStarted, System.nanoTime()));
            try {
                FilterProbeFiles.write(Paths.get("validation.json"), report);
            } catch (Exception failure) {
                ZsgRooms.LOGGER.error("[FilterProbe] FAIL: could not write validation report", failure);
            }
            server.stop(false);
        }
    }

    private static long millis(long start, long end) {
        return (end - start) / 1000000;
    }

    private static boolean hasStart(ServerWorld world, StructureFeature<?> feature, int chunkX, int chunkZ) {
        if (world == null || Math.abs((long) chunkX) > 64 || Math.abs((long) chunkZ) > 64) {
            throw new IllegalArgumentException("Candidate start is outside the supported origin area");
        }
        StructureStart<?> start = world.getStructureAccessor().getStructureStart(ChunkSectionPos.from(chunkX, 0, chunkZ),
                feature, world.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_STARTS));
        return start != null && start.hasChildren();
    }

    private static void verifyFixtures(ServerWorld world, BlockPos reference) {
        int centerX = (reference.getX() >> 4) * 16 + 15;
        int centerZ = (reference.getZ() >> 4) * 16 + 15;
        Map<BlockPos, BlockState> original = new HashMap<BlockPos, BlockState>();
        try {
            for (int z = -8; z <= 8; z++) for (int x = -8; x <= 8; x++) for (int y = 239; y < 256; y++) {
                BlockPos pos = new BlockPos(centerX + x, y, centerZ + z);
                original.put(pos, world.getBlockState(pos));
                world.setBlockState(pos, y <= 240 ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState(), 18);
            }
            for (int x = -1; x <= 3; x++) for (int z = -3; z <= -2; z++) {
                world.setBlockState(new BlockPos(centerX + x, 240, centerZ + z), Blocks.LAVA.getDefaultState(), 18);
            }
            int treeX = centerX - 4;
            int treeZ = centerZ + 4;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(new BlockPos(treeX + dx, 244, treeZ + dz),
                        Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, false), 18);
            }
            for (int y = 241; y <= 244; y++) {
                world.setBlockState(new BlockPos(treeX, y, treeZ), Blocks.OAK_LOG.getDefaultState(), 18);
            }
            MinecraftSurfaceTerrain before = MinecraftSurfaceTerrain.capture(world, centerX, centerZ, 8);
            if (!SurfaceTerrainChecks.findLavaPool(before, centerX, centerZ, 8).isPresent()
                    || !SurfaceTerrainChecks.findWoodedLand(before, centerX, centerZ, 8).isPresent()) {
                throw new AssertionError("Minecraft source lava or rooted natural-leaf tree was rejected");
            }
            world.setBlockState(new BlockPos(centerX - 1, 240, centerZ - 3),
                    Blocks.LAVA.getDefaultState().with(FluidBlock.LEVEL, 1), 18);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = new BlockPos(treeX + dx, 244, treeZ + dz);
                if (world.getBlockState(pos).isOf(Blocks.OAK_LEAVES)) {
                    world.setBlockState(pos, Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true), 18);
                }
            }
            MinecraftSurfaceTerrain after = MinecraftSurfaceTerrain.capture(world, centerX, centerZ, 8);
            if (SurfaceTerrainChecks.findLavaPool(after, centerX, centerZ, 8).isPresent()
                    || SurfaceTerrainChecks.findWoodedLand(after, centerX, centerZ, 8).isPresent()) {
                throw new AssertionError("Flowing lava or persistent leaf fixture was accepted");
            }
            if (!SurfaceTerrainChecks.findLavaPool(before, centerX, centerZ, 8).isPresent()) {
                throw new AssertionError("Snapshot changed after the live world changed");
            }
            ZsgRooms.LOGGER.info("[FilterProbe] Fixtures passed: cross-chunk sources, natural tree, "
                    + "flowing lava rejection, persistent leaf rejection, immutable snapshot");
        } finally {
            for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) {
                world.setBlockState(entry.getKey(), entry.getValue(), 18);
            }
        }
    }
}
