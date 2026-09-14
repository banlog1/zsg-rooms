package zsgrooms.modid.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.gen.feature.StructurePoolFeatureConfig;
import net.minecraft.world.gen.feature.VillageFeature;

/** Full-seed prediction. Terrain-projected village pieces must never be cached by lower-48 family. */
final class VillageLayoutPrediction {
    private static final VillageLootPotential LOOT = new VillageLootPotential();
    final CompoundTag layout;
    final String rejection;

    private VillageLayoutPrediction(MinecraftServer server, StructureStart<?> start) {
        layout = signature(start);
        rejection = GeneratedStructureProbe.templates(start).stream().anyMatch(name -> name.contains("/zombie/"))
                ? "ABANDONED_VILLAGE" : LOOT.canSupplyIron(server.getOverworld(), start) ? null : "VILLAGE_NO_IRON_CHEST_TEMPLATE";
    }

    static VillageLayoutPrediction predict(MinecraftServer server, long seed, ChunkPos chunk) {
        ChunkGenerator generator = GeneratorOptions.createOverworldGenerator(seed);
        Biome biome = generator.getBiomeSource().getBiomeForNoiseGen((chunk.x << 2) + 2, 0, (chunk.z << 2) + 2);
        for (ConfiguredStructureFeature<?, ?> feature : biome.method_28413()) {
            if (feature.field_24835 != StructureFeature.VILLAGE) continue;
            if (!(feature.field_24836 instanceof StructurePoolFeatureConfig)) return null;
            VillageFeature.Start start = new VillageFeature.Start(StructureFeature.VILLAGE,
                    chunk.x, chunk.z, BlockBox.empty(), 0, seed);
            start.init(generator, server.getStructureManager(), chunk.x, chunk.z, biome,
                    (StructurePoolFeatureConfig) feature.field_24836);
            return new VillageLayoutPrediction(server, start);
        }
        return null;
    }

    void verify(StructureStart<?> actual) {
        if (actual == null || !layout.equals(signature(actual))) {
            throw new IllegalStateException("Village piece prediction differs from the generated structure start");
        }
    }

    private static CompoundTag signature(StructureStart<?> start) {
        CompoundTag tag = start.toTag(start.getChunkX(), start.getChunkZ());
        tag.remove("references");
        return tag;
    }
}
