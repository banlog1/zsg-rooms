package zsgrooms.modid.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.BastionRemnantFeature;
import net.minecraft.world.gen.feature.DefaultBiomeFeatures;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.List;

/** Vanilla piece generation only. No world, chunk manager, terrain sampling or block placement. */
final class BastionLayoutPrediction {
    final CompoundTag layout;
    final List<String> templates;
    final boolean accepted;

    private BastionLayoutPrediction(StructureStart<?> start) {
        templates = GeneratedStructureProbe.templates(start);
        if (templates.isEmpty() || templates.size() != start.getChildren().size()) {
            throw new IllegalStateException("Bastion predictor encountered missing or unsupported template pieces");
        }
        layout = signature(start);
        accepted = BastionLayoutChecks.accepts(templates);
    }

    static BastionLayoutPrediction predict(StructureManager manager, long seed, ChunkPos chunk) {
        BastionRemnantFeature.Start start = new BastionRemnantFeature.Start(StructureFeature.BASTION_REMNANT,
                chunk.x, chunk.z, BlockBox.empty(), 0, seed);
        // Vanilla 1.16.1 uses fixed-height rigid bastion pools. Null prevents any accidental terrain-dependent path.
        start.init(null, manager, chunk.x, chunk.z, null, DefaultBiomeFeatures.BASTION_REMNANT.field_24836);
        return new BastionLayoutPrediction(start);
    }

    void verify(StructureStart<?> actual) {
        if (actual == null || !layout.equals(signature(actual))) {
            throw new IllegalStateException("Bastion piece prediction differs from the generated structure start");
        }
    }

    private static CompoundTag signature(StructureStart<?> start) {
        CompoundTag tag = start.toTag(start.getChunkX(), start.getChunkZ());
        tag.remove("references");
        return tag;
    }
}
