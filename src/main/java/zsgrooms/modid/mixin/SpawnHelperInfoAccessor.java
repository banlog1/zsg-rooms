package zsgrooms.modid.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpawnHelper.Info.class)
public interface SpawnHelperInfoAccessor {
    @Accessor("spawningChunkCount")
    int zsgRooms$getSpawningChunkCount();

    @Invoker("isBelowCap")
    boolean zsgRooms$invokeIsBelowCap(SpawnGroup group);
}
