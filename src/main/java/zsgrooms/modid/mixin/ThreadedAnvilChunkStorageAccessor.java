package zsgrooms.modid.mixin;

import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadedAnvilChunkStorage.class)
public interface ThreadedAnvilChunkStorageAccessor {
    @Invoker("isTooFarFromPlayersToSpawnMobs")
    boolean zsgRooms$invokeIsTooFarFromPlayersToSpawnMobs(ChunkPos pos);
}
