package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.world.MobSpawnerEntry;
import net.minecraft.world.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(MobSpawnerLogic.class)
public interface MobSpawnerLogicAccessor {
    @Accessor("spawnDelay")
    int zsgRooms$getSpawnDelay();

    @Accessor("spawnDelay")
    void zsgRooms$setSpawnDelay(int delay);

    @Accessor("spawnPotentials")
    List<MobSpawnerEntry> zsgRooms$getSpawnPotentials();

    @Accessor("spawnEntry")
    MobSpawnerEntry zsgRooms$getSpawnEntry();

    @Accessor("minSpawnDelay")
    int zsgRooms$getMinSpawnDelay();

    @Accessor("maxSpawnDelay")
    int zsgRooms$getMaxSpawnDelay();

    @Accessor("spawnCount")
    int zsgRooms$getSpawnCount();

    @Accessor("maxNearbyEntities")
    int zsgRooms$getMaxNearbyEntities();

    @Accessor("requiredPlayerRange")
    int zsgRooms$getRequiredPlayerRange();

    @Accessor("spawnRange")
    int zsgRooms$getSpawnRange();

    @Invoker("getEntityId")
    Identifier zsgRooms$invokeGetEntityId();

    @Invoker("isPlayerInRange")
    boolean zsgRooms$invokeIsPlayerInRange();

    @Invoker("spawnEntity")
    void zsgRooms$invokeSpawnEntity(Entity entity);
}
