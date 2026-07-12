package zsgrooms.modid.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.structure.RuinedPortalStructurePiece;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.RuinedPortalChestRepair;

import java.util.Random;

@Mixin(SimpleStructurePiece.class)
public abstract class RuinedPortalStructurePieceMixin {
    @Shadow protected Structure structure;
    @Shadow protected StructurePlacementData placementData;
    @Shadow protected BlockPos pos;

    @Inject(method = "generate", at = @At("RETURN"))
    private void zsgRooms$captureRuinedPortalChestBeforeTerrain(
            ServerWorldAccess world,
            StructureAccessor structureAccessor,
            ChunkGenerator chunkGenerator,
            Random random,
            BlockBox boundingBox,
            ChunkPos chunkPos,
            BlockPos pivot,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof RuinedPortalStructurePiece)) {
            return;
        }
        for (Structure.StructureBlockInfo chest : this.structure.getInfosForBlock(this.pos, this.placementData, Blocks.CHEST)) {
            RuinedPortalChestRepair.captureGenerated(world, chest.pos, chest.state);
        }
    }
}
