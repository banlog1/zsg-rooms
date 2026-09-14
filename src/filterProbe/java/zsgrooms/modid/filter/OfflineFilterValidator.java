package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.List;
import static zsgrooms.modid.filter.ValidationTrace.measure;
import static zsgrooms.modid.filter.ValidationTrace.run;

final class OfflineFilterValidator {
    static final String PROFILE = "zsg-lava-wooded-stables-v3";
    private static final VillageLootPotential VILLAGE_LOOT = new VillageLootPotential();

    static JsonObject validate(MinecraftServer server, FilterCandidateSearch.Candidate candidate) {
        return validate(server, candidate, null, null, false);
    }

    static JsonObject validate(MinecraftServer server, FilterCandidateSearch.Candidate candidate, OfflineFamilyGate familyGate,
                               VillageLayoutPrediction village, boolean earlyChecks) {
        return validate(server, candidate, familyGate, village, earlyChecks, null);
    }

    static JsonObject validate(MinecraftServer server, FilterCandidateSearch.Candidate candidate, OfflineFamilyGate familyGate,
                               VillageLayoutPrediction village, boolean earlyChecks, BlockPos spawnPrediction) {
        long started = System.nanoTime();
        JsonObject report = new JsonObject();
        report.addProperty("profile", PROFILE);
        report.addProperty("type", candidate.type.name());
        report.addProperty("status", "REJECTED");
        JsonArray rejections = new JsonArray();
        report.add("rejections", rejections);
        report.addProperty("spawnPolicy", spawnPrediction == null ? "NATIVE" : "CUBIOMES_MARGIN_16_V1");
        SpawnModelCheck spawn = new SpawnModelCheck(spawnPrediction, () -> {
            determineSpawn(server, report);
            return server.getOverworld().getSpawnPos();
        });
        try {
            ServerWorld world = server.getOverworld();
            ServerWorld nether = server.getWorld(World.NETHER);
            long stage = System.nanoTime();
            StructureStart<?> main = measure("start.main", () -> GeneratedStructureProbe.start(world, candidate.type.structure(), candidate.main));
            report.addProperty("mainStartMs", (System.nanoTime() - stage) / 1000000);
            if (village != null) {
                run("parity.village", () -> village.verify(main));
                report.addProperty("villagePredictionVerified", true);
            }
            stage = System.nanoTime();
            StructureStart<?> bastion = measure("start.bastion", () -> GeneratedStructureProbe.start(nether, StructureFeature.BASTION_REMNANT, candidate.bastion));
            report.addProperty("bastionStartMs", (System.nanoTime() - stage) / 1000000);
            if (familyGate != null) {
                run("parity.bastion", () -> familyGate.verify(candidate, bastion));
                report.addProperty("bastionPredictionVerified", familyGate.verifiedStarts > 0);
            }
            stage = System.nanoTime();
            StructureStart<?> fortress = measure("start.fortress", () -> GeneratedStructureProbe.start(nether, StructureFeature.FORTRESS, candidate.fortress));
            report.addProperty("fortressStartMs", (System.nanoTime() - stage) / 1000000);
            if (!measure("present.main", () -> main != null)) rejections.add("MAIN_START");
            if (!measure("present.bastion", () -> bastion != null)) rejections.add("BASTION_START");
            if (!measure("present.fortress", () -> fortress != null)) rejections.add("FORTRESS_START");
            if (rejections.size() > 0) return report;
            List<String> bastionTemplates = measure("bastion.templates", () -> GeneratedStructureProbe.templates(bastion));
            boolean stables = BastionLayoutChecks.isStables(bastionTemplates);
            report.addProperty("stables", stables);
            if (!measure("bastion.layout", () -> BastionLayoutChecks.accepts(bastionTemplates))) { rejections.add("STABLES_LAYOUT"); return report; }
            BlockPos anchor = new BlockPos(candidate.main.x << 4, 64, candidate.main.z << 4);
            if (candidate.type == FilterCandidateSearch.Type.TEMPLE) {
                TempleLootProbe loot = measure("temple.loot", () -> TempleLootProbe.read(world, main));
                if (loot.chests == 4) {
                    run("parity.templeLoot", () -> TempleLootPrediction.verify(world, candidate.main, loot));
                    report.addProperty("lootPredictionVerified", true);
                }
                report.addProperty("chests", loot.chests);
                report.addProperty("iron", loot.iron);
                report.addProperty("diamonds", loot.diamonds);
                if (!measure("temple.chestCount", () -> loot.chests == 4)) rejections.add("TEMPLE_CHESTS");
                if (!measure("temple.resources", () -> TempleCandidateChecks.hasResources(loot.iron, loot.diamonds))) rejections.add("TEMPLE_RESOURCES");
                if (rejections.size() > 0) return report;
                spawn.prepareExactMode();
                if (earlyChecks && !measure("pool.spawnEnvelope", () -> spawn.canReachPoolArea(anchor))) {
                    rejections.add("SPAWN_POOL_ENVELOPE"); return report;
                }
                MinecraftSurfaceTerrain terrain = poolTerrain(world, anchor, report, earlyChecks);
                TempleCandidateChecks.Result checked = measure("temple.poolChecks", () -> TempleCandidateChecks.check(terrain, anchor,
                        loot.chests, loot.iron, loot.diamonds, pool -> GenerationEvidence.isNaturalPool(world, pool),
                        pos -> TreeBiomeChecks.nearby(at -> world.getBiome(at).getCategory(), pos, false),
                        pos -> spawn.within(32, anchor, pos)));
                for (TempleCandidateChecks.Rejection reason : checked.rejections) rejections.add(reason.name());
                if (rejections.size() > 0) return report;
                anchor(report, "portal", checked.pool().get().shore);
            } else if (candidate.type == FilterCandidateSearch.Type.VILLAGE) {
                if (!measure("village.nonAbandoned", () -> !GeneratedStructureProbe.templates(main).stream().anyMatch(name -> name.contains("/zombie/")))) {
                    rejections.add("ABANDONED_VILLAGE"); return report;
                }
                if (!measure("village.ironPotential", () -> VILLAGE_LOOT.canSupplyIron(world, main))) {
                    rejections.add("VILLAGE_NO_IRON_CHEST_TEMPLATE"); return report;
                }
                if (!measure("village.treeBiome", () -> TreeBiomeChecks.nearby(at -> world.getBiome(at).getCategory(), anchor, true))) {
                    rejections.add("VILLAGE_TREE_BIOME"); return report;
                }
                List<WorldChunk> chunks = measure("village.generate", () -> GeneratedStructureProbe.generate(world, main));
                GeneratedStructureProbe.Loot loot = measure("village.loot", () -> GeneratedStructureProbe.loot(world, main, chunks,
                        table -> table.startsWith("minecraft:chests/village/")));
                boolean golem = measure("village.golem", () -> GeneratedStructureProbe.hasGolem(main, chunks));
                report.addProperty("golem", golem);
                report.addProperty("iron", loot.count(Items.IRON_INGOT));
                if (!measure("village.iron", () -> FilterResourceChecks.villageIron(loot.count(Items.IRON_INGOT), loot.count(Items.IRON_NUGGET),
                        loot.count(Items.IRON_BLOCK), golem))) { rejections.add("VILLAGE_IRON"); return report; }
                spawn.prepareExactMode();
                if (earlyChecks && !measure("pool.spawnEnvelope", () -> spawn.canReachPoolArea(anchor))) {
                    rejections.add("SPAWN_POOL_ENVELOPE"); return report;
                }
                MinecraftSurfaceTerrain terrain = poolTerrain(world, anchor, report, earlyChecks);
                SurfaceTerrainChecks.LavaPool pool = measure("pool.shape", () -> SurfaceTerrainChecks.findLavaPools(terrain, anchor.getX(), anchor.getZ(), 96)).stream()
                        .filter(value -> measure("pool.provenance", () -> GenerationEvidence.isNaturalPool(world, value)))
                        .filter(value -> measure("pool.spawnDistance", () -> spawn.within(32, anchor, value.anchor))).findFirst().orElse(null);
                if (pool == null) { rejections.add("VILLAGE_POOL_OR_SPAWN"); return report; }
                anchor(report, "portal", pool.shore);
            } else {
                BlockPos entry = measure("shipwreck.checks", () -> ShipwreckProbe.validate(world, main, anchor, report, rejections, spawn));
                if (entry == null) return report;
                anchor(report, "portal", entry);
            }
            if (stables && !measure("stables.integrity", () -> BastionLayoutProbe.intactStables(nether, bastion))) {
                rejections.add("STABLES_DAMAGED"); return report;
            }
            anchor(report, "structure", anchor);
            // Remains non-exportable until the profile and all acceptance checks have integration coverage.
            report.addProperty("status", "VALIDATION_PASS");
            return report;
        } finally {
            report.addProperty("spawnModelPasses", spawn.modelPasses);
            report.addProperty("spawnModelRejections", spawn.modelRejections);
            report.addProperty("spawnNativeRequests", spawn.nativeRequests);
            report.addProperty("validationMs", (System.nanoTime() - started) / 1000000);
        }
    }

    static void determineSpawn(MinecraftServer server, JsonObject report) {
        long started = System.nanoTime();
        run("spawn.actual", () -> ProbeWorlds.determineSpawn(server));
        report.addProperty("spawnMs", (System.nanoTime() - started) / 1000000);
    }

    private static MinecraftSurfaceTerrain poolTerrain(ServerWorld world, BlockPos anchor, JsonObject report, boolean earlyChecks) {
        long started = System.nanoTime();
        MinecraftSurfaceTerrain terrain = measure("pool.capture", () -> earlyChecks
                ? MinecraftSurfaceTerrain.captureForPools(world, anchor.getX(), anchor.getZ(), 96)
                : MinecraftSurfaceTerrain.capture(world, anchor.getX(), anchor.getZ(), 96));
        report.addProperty("poolCaptureMs", (System.nanoTime() - started) / 1000000);
        return terrain;
    }

    private static void anchor(JsonObject report, String key, BlockPos pos) {
        JsonObject value = new JsonObject();
        value.addProperty("x", pos.getX());
        value.addProperty("y", pos.getY());
        value.addProperty("z", pos.getZ());
        report.add(key, value);
    }
}
