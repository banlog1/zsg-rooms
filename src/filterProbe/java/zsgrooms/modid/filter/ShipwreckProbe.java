package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.ShipwreckGenerator;
import net.minecraft.structure.StructureStart;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Arrays;
import java.util.List;
import static zsgrooms.modid.filter.ValidationTrace.measure;

final class ShipwreckProbe {
    static BlockPos validate(ServerWorld world, StructureStart<?> start, BlockPos anchor, JsonObject report, JsonArray rejected) {
        return validate(world, start, anchor, report, rejected, new SpawnModelCheck(null, () -> {
            OfflineFilterValidator.determineSpawn(world.getServer(), report);
            return world.getSpawnPos();
        }));
    }

    static BlockPos validate(ServerWorld world, StructureStart<?> start, BlockPos anchor, JsonObject report, JsonArray rejected, SpawnModelCheck spawn) {
        if (!measure("shipwreck.piece", () -> start.getChildren().size() == 1 && start.getChildren().get(0) instanceof ShipwreckGenerator.Piece)) {
            rejected.add("SHIPWRECK_PIECE"); return null;
        }
        CompoundTag piece = start.getChildren().get(0).getTag();
        if (!measure("shipwreck.layout", () -> !piece.getBoolean("isBeached") && piece.getString("Rot").equals("COUNTERCLOCKWISE_90")
                && Arrays.asList("minecraft:shipwreck/with_mast", "minecraft:shipwreck/rightsideup_full",
                "minecraft:shipwreck/with_mast_degraded", "minecraft:shipwreck/rightsideup_full_degraded").contains(piece.getString("Template")))) {
            rejected.add("SHIPWRECK_LAYOUT"); return null;
        }
        List<WorldChunk> chunks = measure("shipwreck.generate", () -> GeneratedStructureProbe.generate(world, start));
        GeneratedStructureProbe.Loot treasure = measure("shipwreck.treasure", () -> GeneratedStructureProbe.loot(world, start, chunks,
                table -> table.equals(LootTables.SHIPWRECK_TREASURE_CHEST.toString())));
        GeneratedStructureProbe.Loot supply = measure("shipwreck.supply", () -> GeneratedStructureProbe.loot(world, start, chunks,
                table -> table.equals(LootTables.SHIPWRECK_SUPPLY_CHEST.toString())));
        int iron = treasure.count(Items.IRON_INGOT) + treasure.count(Items.IRON_NUGGET) / 9;
        int gold = treasure.count(Items.GOLD_INGOT) + treasure.count(Items.GOLD_NUGGET) / 9;
        if (!measure("shipwreck.chestCount", () -> treasure.chests == 1 && supply.chests == 1)) rejected.add("SHIPWRECK_CHESTS");
        if (!measure("shipwreck.tools", () -> FilterResourceChecks.shipwreckTools(iron, treasure.count(Items.DIAMOND), gold))) rejected.add("SHIPWRECK_TOOLS");
        if (!measure("shipwreck.food", () -> FilterResourceChecks.shipwreckFood(supply.count(Items.WHEAT), supply.count(Items.CARROT)))) rejected.add("SHIPWRECK_FOOD");
        if (rejected.size() > 0) return null;
        if (!measure("shipwreck.spawnDistance", () -> spawn.within(48, anchor))) {
            rejected.add("SHIPWRECK_SPAWN"); return null;
        }
        MinecraftSurfaceTerrain terrain = measure("shipwreck.capture", () -> MinecraftSurfaceTerrain.capture(world, anchor.getX(), anchor.getZ(), 96));
        boolean wood = measure("shipwreck.actualTrees", () -> SurfaceTerrainChecks.findWoodedLand(terrain, anchor.getX(), anchor.getZ(), 96).isPresent());
        report.addProperty("woodedLand", wood);
        if (!wood) { rejected.add("SHIPWRECK_WOOD"); return null; }
        List<RavineEvidence.Middle> middles = measure("ravine.evidence", () -> RavineEvidence.get(world.getSeed()));
        report.addProperty("deepRavineMiddles", middles.size());
        for (RavineEvidence.Middle middle : middles) {
            double dx = Math.abs(middle.x - anchor.getX());
            double dz = Math.abs(middle.z - anchor.getZ());
            if (!measure("ravine.distance", () -> !(dx > 80 || dz > 80 || dx < 25 && dz < 25))) continue;
            BlockPos pos = middle.pos();
            BlockPos towards = new BlockPos(Math.floor((middle.x * 2 + anchor.getX()) / 3), 64,
                    Math.floor((middle.z * 2 + anchor.getZ()) / 3));
            if (!measure("ravine.deepOcean", () -> deepOcean(world.getBiome(new BlockPos(pos.getX(), 64, pos.getZ()))) && deepOcean(world.getBiome(towards)))) continue;
            // Confirm an actual open underwater lava column, not only a predicted carver ellipse.
            BlockPos entry = measure("ravine.openColumn", () -> {
                for (int z = pos.getZ() - 8; z <= pos.getZ() + 8; z++) for (int x = pos.getX() - 8; x <= pos.getX() + 8; x++) {
                    if (!world.getBlockState(new BlockPos(x, 8, z)).isOf(Blocks.LAVA)) continue;
                    boolean open = true;
                    for (int y = 11; y <= 62; y++) {
                        BlockPos column = new BlockPos(x, y, z);
                        if (!world.getBlockState(column).isAir() && !world.getFluidState(column).isIn(FluidTags.WATER)) { open = false; break; }
                    }
                    if (open) return new BlockPos(x, 11, z);
                }
                return null;
            });
            if (entry != null) return entry;
        }
        rejected.add("SHIPWRECK_RAVINE");
        return null;
    }

    private static boolean deepOcean(Biome biome) {
        return biome == Biomes.DEEP_OCEAN || biome == Biomes.DEEP_COLD_OCEAN || biome == Biomes.DEEP_FROZEN_OCEAN
                || biome == Biomes.DEEP_LUKEWARM_OCEAN || biome == Biomes.DEEP_WARM_OCEAN;
    }
}
