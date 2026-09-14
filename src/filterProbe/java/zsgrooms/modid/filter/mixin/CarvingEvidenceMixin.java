package zsgrooms.modid.filter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import zsgrooms.modid.filter.RavineEvidence;

@Mixin(ChunkGenerator.class)
public abstract class CarvingEvidenceMixin {
    @WrapMethod(method = "carve")
    private void zsgRooms$carvingSeed(long seed, BiomeAccess access, Chunk chunk, GenerationStep.Carver carver,
                                     Operation<Void> original) {
        Long previous = RavineEvidence.enter(seed);
        try { original.call(seed, access, chunk, carver); } finally { RavineEvidence.restore(previous); }
    }
}
