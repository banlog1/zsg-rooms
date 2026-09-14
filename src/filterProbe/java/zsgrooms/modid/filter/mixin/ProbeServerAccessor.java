package zsgrooms.modid.filter.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public interface ProbeServerAccessor {
    @Accessor("worlds") Map<RegistryKey<World>, ServerWorld> zsgRooms$worlds();
    @Accessor("workerExecutor") Executor zsgRooms$workerExecutor();
    @Mutable @Accessor("saveProperties") void zsgRooms$saveProperties(SaveProperties properties);

    @Invoker("setupSpawn")
    static void zsgRooms$setupSpawn(ServerWorld world, ServerWorldProperties properties,
                                    boolean bonusChest, boolean debug, boolean findSpawn) {
        throw new AssertionError();
    }
}
