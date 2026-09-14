package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.ZsgRooms;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Offline comparisons against native starts and the unoptimized validation path. */
final class EarlyFilterChecksTest {
    static void run(MinecraftServer server) {
        JsonObject report = new JsonObject();
        report.addProperty("status", "TESTING");
        long started = System.nanoTime();
        try {
            FilterProbeFiles.write(Paths.get("early-test.json"), report);
            villages(server, report);
            pools(server, report);
            candidates(server, report);
            report.addProperty("status", "PASSED");
            ZsgRooms.LOGGER.info("[FilterEarly] COMPLETE: villageStarts={}, candidateComparisons={}",
                    report.get("villageStarts"), report.get("candidateComparisons"));
        } catch (Throwable failure) {
            report.addProperty("status", "FAILED");
            report.addProperty("error", failure.getClass().getSimpleName());
            ZsgRooms.LOGGER.error("[FilterEarly] FAIL: " + failure.getClass().getSimpleName(), failure);
        } finally {
            report.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
            try { FilterProbeFiles.write(Paths.get("early-test.json"), report); }
            catch (Exception failure) { ZsgRooms.LOGGER.error("[FilterEarly] FAIL: report write", failure); }
            server.stop(false);
        }
    }

    private static void villages(MinecraftServer server, JsonObject report) throws Exception {
        Set<Biome.Category> categories = new HashSet<Biome.Category>();
        VillageLootPotential loot = new VillageLootPotential();
        int tested = 0, rejected = 0, allowed = 0;
        long predictionNanos = 0, nativeNanos = 0;
        for (int i = 0; i < 10000 && tested < 64; i++) {
            long seed = i * 0x9e3779b97f4a7c15L;
            ChunkPos chunk = FilterCandidateSearch.placement(seed, StructureFeature.VILLAGE, i % 2 == 0 ? -1 : 0, 0, new ChunkRandom());
            Biome biome = FilterCandidateSearch.structureBiome(new VanillaLayeredBiomeSource(seed, false, false), chunk);
            if (biome.getCategory() != Biome.Category.PLAINS && biome.getCategory() != Biome.Category.DESERT
                    && biome.getCategory() != Biome.Category.SAVANNA) continue;
            if (!biome.hasStructureFeature(StructureFeature.VILLAGE)) continue;
            long started = System.nanoTime();
            VillageLayoutPrediction predicted = VillageLayoutPrediction.predict(server, seed, chunk);
            predictionNanos += System.nanoTime() - started;
            require(predicted != null, "Missing eligible village prediction");
            started = System.nanoTime();
            try (ProbeWorlds ignored = new ProbeWorlds(server, seed, UUID.randomUUID().toString())) {
                StructureStart<?> actual = GeneratedStructureProbe.start(server.getOverworld(), StructureFeature.VILLAGE, chunk);
                predicted.verify(actual);
                String expected = GeneratedStructureProbe.templates(actual).stream().anyMatch(name -> name.contains("/zombie/"))
                        ? "ABANDONED_VILLAGE" : loot.canSupplyIron(server.getOverworld(), actual) ? null : "VILLAGE_NO_IRON_CHEST_TEMPLATE";
                require(java.util.Objects.equals(expected, predicted.rejection), "Village rejection differs from native layout");
            }
            nativeNanos += System.nanoTime() - started;
            if (predicted.rejection == null) allowed++; else rejected++;
            categories.add(biome.getCategory());
            tested++;
            if (tested >= 12 && categories.size() == 3 && rejected > 0 && allowed > 0) break;
        }
        require(tested >= 12 && categories.size() == 3 && rejected > 0 && allowed > 0, "Insufficient village fixture coverage");
        report.addProperty("villageStarts", tested);
        report.addProperty("villageCategories", categories.size());
        report.addProperty("villageRejected", rejected);
        report.addProperty("villageAllowed", allowed);
        report.addProperty("villagePredictionMs", predictionNanos / 1000000.0);
        report.addProperty("villageNativeMs", nativeNanos / 1000000.0);
        ZsgRooms.LOGGER.info("[FilterEarly] Village parity passed: starts={}", tested);
    }

    private static void pools(MinecraftServer server, JsonObject report) throws Exception {
        try (ProbeWorlds ignored = new ProbeWorlds(server, 1, UUID.randomUUID().toString())) {
            ServerWorld world = server.getOverworld();
            // Complete decoration before constructing a small positive pool/shore fixture in this disposable world.
            MinecraftSurfaceTerrain.captureForPools(world, 0, 0, 12);
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                world.setBlockState(new BlockPos(x, 200, z), Blocks.STONE.getDefaultState(), 2);
                for (int y = 201; y < 256; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 2);
            }
            for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
                world.setBlockState(new BlockPos(x, 200, z), Blocks.LAVA.getDefaultState(), 2);
            }
            world.setBlockState(new BlockPos(-1, 204, 0), Blocks.OAK_LEAVES.getDefaultState(), 2);
            long start = System.nanoTime();
            MinecraftSurfaceTerrain dense = MinecraftSurfaceTerrain.capture(world, 0, 0, 12);
            List<SurfaceTerrainChecks.LavaPool> expected = SurfaceTerrainChecks.findLavaPools(dense, 0, 0, 12);
            report.addProperty("densePoolMs", (System.nanoTime() - start) / 1000000.0);
            start = System.nanoTime();
            MinecraftSurfaceTerrain lazy = MinecraftSurfaceTerrain.captureForPools(world, 0, 0, 12);
            List<SurfaceTerrainChecks.LavaPool> actual = SurfaceTerrainChecks.findLavaPools(lazy, 0, 0, 12);
            report.addProperty("lazyPoolMs", (System.nanoTime() - start) / 1000000.0);
            require(!expected.isEmpty() && expected.size() == actual.size(), "Positive pool fixture mismatch");
            for (int i = 0; i < expected.size(); i++) {
                require(expected.get(i).anchor.equals(actual.get(i).anchor) && expected.get(i).shore.equals(actual.get(i).shore)
                        && expected.get(i).sources.equals(actual.get(i).sources), "Pool geometry or selection differs");
            }
            for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) {
                require(dense.surfaceY(x, z) == lazy.surfaceY(x, z), "Surface height differs");
                for (int y = 160; y <= 206; y++) require(dense.cell(x, y, z) == lazy.cell(x, y, z), "Terrain cell differs");
            }
            report.addProperty("poolParity", true);
        }
    }

    private static void candidates(MinecraftServer server, JsonObject report) throws Exception {
        StagedFilterSearch search = new StagedFilterSearch(FilterCandidateSearch.Type.TEMPLE, false, 1000, 1000,
                new OfflineFamilyGate(server, true, true));
        int tested = 0;
        long baselineNanos = 0, optimizedNanos = 0;
        for (int i = 0; i < 300 && tested < 3; i++) {
            FilterCandidateSearch.Candidate candidate = search.next(1000000, 30000, () -> false).candidate().orElse(null);
            if (candidate == null || !legacySpawnFixture(candidate) || !TempleWoodSearchHint.promising(candidate)) continue;
            JsonObject baseline, optimized;
            long start = System.nanoTime();
            try (ProbeWorlds ignored = new ProbeWorlds(server, candidate.seedForValidation(), UUID.randomUUID().toString())) {
                baseline = OfflineFilterValidator.validate(server, candidate);
            }
            baselineNanos += System.nanoTime() - start;
            start = System.nanoTime();
            try (ProbeWorlds ignored = new ProbeWorlds(server, candidate.seedForValidation(), UUID.randomUUID().toString())) {
                optimized = OfflineFilterValidator.validate(server, candidate, null, null, true);
            }
            optimizedNanos += System.nanoTime() - start;
            require(baseline.get("status").equals(optimized.get("status")), "Optimized candidate acceptance differs");
            if ("VALIDATION_PASS".equals(baseline.get("status").getAsString())) {
                require(baseline.get("portal").equals(optimized.get("portal"))
                        && baseline.get("structure").equals(optimized.get("structure")), "Candidate anchors differ");
            }
            tested++;
            ZsgRooms.LOGGER.info("[FilterEarly] Candidate parity passed: fixture={}", tested);
        }
        require(tested == 3, "Insufficient candidate fixtures");
        report.addProperty("candidateComparisons", tested);
        report.addProperty("baselineCandidateMs", baselineNanos / 1000000.0);
        report.addProperty("optimizedCandidateMs", optimizedNanos / 1000000.0);
    }

    // Retain the historical fixture corpus for old/new exact-verifier comparisons only.
    private static boolean legacySpawnFixture(FilterCandidateSearch.Candidate candidate) {
        long seed = candidate.seedForValidation();
        VanillaLayeredBiomeSource source = new VanillaLayeredBiomeSource(seed, false, false);
        BlockPos hint = source.locateBiome(0, 63, 0, 256, source.getSpawnBiomes(), new java.util.Random(seed));
        if (hint == null) return true;
        BlockPos main = new BlockPos(candidate.main.x << 4, 64, candidate.main.z << 4);
        if (TempleCandidateChecks.withinAxes(hint, main, 64)) return true;
        for (BlockPos lake : candidate.lakeAttempts) if (TempleCandidateChecks.withinAxes(hint, lake, 80)) return true;
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
