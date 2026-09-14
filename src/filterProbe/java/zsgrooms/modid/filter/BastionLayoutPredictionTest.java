package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.ZsgRooms;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Headless differential test against actual structure starts, including layouts rejected by the bank. */
final class BastionLayoutPredictionTest {
    static void run(MinecraftServer server) {
        JsonObject report = new JsonObject();
        report.addProperty("status", "TESTING");
        long started = System.nanoTime();
        int families = 0, comparisons = 0, worlds = 0, goodStables = 0, badStables = 0;
        Set<String> types = new HashSet<String>();
        Set<String> rotations = new HashSet<String>();
        Set<String> representatives = new HashSet<String>();
        try {
            FilterProbeFiles.write(Paths.get("layout-test.json"), report);
            for (int i = 0; i < 10000; i++) {
                long lower = (i * 0x9e3779b97f4a7c15L) & StagedFilterSearch.MASK;
                ChunkPos chunk = FilterCandidateSearch.findNetherPlacement(lower, 96, true, new ChunkRandom());
                if (chunk == null || !FilterCandidateSearch.structureBiome(
                        MultiNoiseBiomeSource.Preset.NETHER.getBiomeSource(lower), chunk)
                        .hasStructureFeature(StructureFeature.BASTION_REMNANT)) continue;
                BastionLayoutPrediction base = BastionLayoutPrediction.predict(server.getStructureManager(), lower, chunk);
                String type = base.templates.get(0).split("/")[1];
                String rotation = base.layout.getList("Children", 10).getCompound(0).getString("rotation");
                require(!rotation.isEmpty(), "Missing initial rotation");
                types.add(type);
                rotations.add(rotation);
                if (BastionLayoutChecks.isStables(base.templates)) {
                    if (base.accepted) goodStables++; else badStables++;
                }
                for (int upper : new int[]{1, 32768, 65535}) {
                    long seed = lower | ((long) upper << 48);
                    BastionLayoutPrediction sister = BastionLayoutPrediction.predict(server.getStructureManager(), seed, chunk);
                    require(base.layout.equals(sister.layout) && base.accepted == sister.accepted,
                            "Sister seeds produced different bastion layouts");
                    comparisons++;
                }
                // Generate fresh native starts for each observed type/rotation/acceptance combination.
                if (representatives.add(type + ":" + rotation + ":" + base.accepted)) {
                    for (int upper : new int[]{1, 65535}) {
                        try (ProbeWorlds ignored = new ProbeWorlds(server, lower | ((long) upper << 48), UUID.randomUUID().toString())) {
                            StructureStart<?> actual = GeneratedStructureProbe.start(server.getWorld(World.NETHER),
                                    StructureFeature.BASTION_REMNANT, chunk);
                            base.verify(actual);
                            require(base.accepted == BastionLayoutChecks.accepts(GeneratedStructureProbe.templates(actual)),
                                    "Native layout acceptance differs");
                            worlds++;
                        }
                    }
                }
                families++;
                if (families >= 64 && types.size() == 4 && rotations.size() == 4 && goodStables > 0 && badStables > 0) break;
            }
            require(families >= 64 && types.size() == 4 && rotations.size() == 4 && goodStables > 0 && badStables > 0,
                    "Incomplete bastion fixture coverage");
            OfflineFamilyGate disabled = new OfflineFamilyGate(server, false, false);
            require(disabled.allows(0, null, null), "Disabled gate rejected a family");
            disabled.verify(null, null);
            require(disabled.layoutsChecked == 0 && disabled.verifiedStarts == 0, "Disabled gate performed layout work");
            report.addProperty("status", "PASSED");
            ZsgRooms.LOGGER.info("[FilterLayout] COMPLETE: families={}, sisterComparisons={}, nativeStarts={}",
                    families, comparisons, worlds);
        } catch (Throwable failure) {
            report.addProperty("status", "FAILED");
            report.addProperty("error", failure.getClass().getSimpleName());
            ZsgRooms.LOGGER.error("[FilterLayout] FAIL: " + failure.getClass().getSimpleName(), failure);
        } finally {
            report.addProperty("families", families);
            report.addProperty("sisterComparisons", comparisons);
            report.addProperty("nativeStarts", worlds);
            report.addProperty("types", types.size());
            report.addProperty("rotations", rotations.size());
            report.addProperty("acceptedStables", goodStables);
            report.addProperty("rejectedStables", badStables);
            report.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
            try { FilterProbeFiles.write(Paths.get("layout-test.json"), report); }
            catch (Exception failure) { ZsgRooms.LOGGER.error("[FilterLayout] FAIL: report write", failure); }
            server.stop(false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
