package zsgrooms.modid.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.StructureFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.NaturalSpawnRngAccess;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.rng.FortressSpawnOrder;
import zsgrooms.modid.rng.FortressSpawnProtection;
import zsgrooms.modid.rng.FortressSpawnProtectionState;
import zsgrooms.modid.rng.NaturalSpawnScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerFortressMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;
    @Shadow private boolean spawnMonsters;

    @Inject(method = "tickChunks", at = @At("HEAD"))
    private void zsgRooms$clearPreviousOrder(CallbackInfo ci) {
        FortressSpawnOrder.clear();
        FortressSpawnProtection.clear();
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
            target = "Ljava/util/Collections;shuffle(Ljava/util/List;)V"))
    private void zsgRooms$prepareFortressOrder(List<ChunkHolder> holders) {
        Collections.shuffle(holders);
        if (!this.spawnMonsters || !this.world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)
                || !NaturalSpawnScope.applies(RngStandardization.isEnabled(), this.world.getRegistryKey(), SpawnGroup.MONSTER)
                || ((NaturalSpawnRngAccess) this.world.getServer()).zsgRooms$getNaturalSpawnRngManager() == null) {
            return;
        }
        FortressSpawnProtectionState state = FortressSpawnProtectionState.get(this.world);
        List<WorldChunk> eligible = new ArrayList<WorldChunk>();
        for (ChunkHolder holder : holders) {
            if (!holder.getTickingFuture().getNow(ChunkHolder.UNLOADED_WORLD_CHUNK).left().isPresent()) {
                continue;
            }
            WorldChunk chunk = holder.getEntityTickingFuture()
                    .getNow(ChunkHolder.UNLOADED_WORLD_CHUNK).left().orElse(null);
            if (chunk == null || ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage)
                    .zsgRooms$invokeIsTooFarFromPlayersToSpawnMobs(holder.getPos())
                    || !this.world.getWorldBorder().contains(holder.getPos())) {
                continue;
            }
            for (long fortress : chunk.getStructureReferences(StructureFeature.FORTRESS)) {
                if (state.hasRemaining(fortress)) {
                    eligible.add(chunk);
                    break;
                }
            }
        }
        FortressSpawnOrder.prepare(this.world, eligible);
    }

    @Inject(method = "tickChunks", at = @At("RETURN"))
    private void zsgRooms$finishFortressOrder(CallbackInfo ci) {
        FortressSpawnOrder.clear();
        FortressSpawnProtection.clear();
    }
}
