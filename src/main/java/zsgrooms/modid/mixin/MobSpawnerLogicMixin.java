package zsgrooms.modid.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.MobSpawnerEntry;
import net.minecraft.world.MobSpawnerLogic;
import net.minecraft.world.gen.feature.StructureFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.BlazeSpawnerStandardization;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.SeedDebugLog;

import java.util.List;
import java.util.Optional;

@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {
    @Unique
    private static final Identifier zsgRooms$BLAZE_ID = new Identifier("minecraft", "blaze");

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$standardizeBlazeSpawner(CallbackInfo ci) {
        MobSpawnerLogic logic = (MobSpawnerLogic) (Object) this;
        if (!(logic.getWorld() instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) logic.getWorld();
        BlockPos pos = logic.getPos();
        MobSpawnerLogicAccessor accessor = (MobSpawnerLogicAccessor) this;
        if (!RngStandardization.isEnabled()
                || !accessor.zsgRooms$invokeIsPlayerInRange()
                || !zsgRooms$qualifies(logic, accessor, world, pos)) {
            return;
        }

        ci.cancel();
        String key = BlazeSpawnerStandardization.spawnerKey(
                world.getRegistryKey().getValue().toString(), pos.asLong(), zsgRooms$BLAZE_ID.toString());
        zsgRooms$tickStandardized(logic, accessor, world, pos, key);
    }

    private boolean zsgRooms$qualifies(MobSpawnerLogic logic, MobSpawnerLogicAccessor accessor,
                                       ServerWorld world, BlockPos pos) {
        if (!RngStandardization.isEnabled()) {
            return false;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        boolean ownedBlockSpawner = world.getBlockState(pos).isOf(Blocks.SPAWNER)
                && blockEntity instanceof MobSpawnerBlockEntity
                && ((MobSpawnerBlockEntity) blockEntity).getLogic() == logic;
        Identifier entityId = accessor.zsgRooms$invokeGetEntityId();
        CompoundTag spawnData = accessor.zsgRooms$getSpawnEntry().getEntityTag();
        List<MobSpawnerEntry> potentials = accessor.zsgRooms$getSpawnPotentials();
        boolean insideFortress = world.getStructureAccessor()
                .getStructuresWithChildren(ChunkSectionPos.from(pos), StructureFeature.FORTRESS)
                .anyMatch(start -> start.getBoundingBox().contains(pos));

        return BlazeSpawnerStandardization.qualifies(
                true,
                true,
                ownedBlockSpawner,
                zsgRooms$BLAZE_ID.equals(entityId),
                insideFortress,
                spawnData.contains("Pos"),
                zsgRooms$isDefaultBlazeData(spawnData),
                zsgRooms$hasSupportedPotentials(potentials),
                accessor.zsgRooms$getMinSpawnDelay(),
                accessor.zsgRooms$getMaxSpawnDelay(),
                accessor.zsgRooms$getSpawnCount(),
                accessor.zsgRooms$getMaxNearbyEntities(),
                accessor.zsgRooms$getRequiredPlayerRange(),
                accessor.zsgRooms$getSpawnRange());
    }

    private void zsgRooms$tickStandardized(MobSpawnerLogic logic, MobSpawnerLogicAccessor accessor,
                                           ServerWorld world, BlockPos pos, String key) {
        if (accessor.zsgRooms$getSpawnDelay() == -1) {
            zsgRooms$scheduleDelay(logic, accessor, world, key, false);
        }
        if (accessor.zsgRooms$getSpawnDelay() > 0) {
            accessor.zsgRooms$setSpawnDelay(accessor.zsgRooms$getSpawnDelay() - 1);
            return;
        }

        BlazeSpawnerStandardization.SpawnerEvent event =
                BlazeSpawnerStandardization.currentEvent(key);
        boolean spawned = false;

        for (int attempt = 0; attempt < accessor.zsgRooms$getSpawnCount(); attempt++) {
            CompoundTag entityTag = accessor.zsgRooms$getSpawnEntry().getEntityTag();
            Optional<EntityType<?>> entityType = EntityType.fromTag(entityTag);
            if (!entityType.isPresent()) {
                zsgRooms$scheduleDelay(logic, accessor, world, key, true);
                return;
            }

            BlazeSpawnerStandardization.CandidatePosition candidate =
                    BlazeSpawnerStandardization.standardizedSpawnerPosition(
                            world.getSeed(),
                            key,
                            event.getCycleIndex(),
                            event.getRetryBatchIndex(),
                            attempt,
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            accessor.zsgRooms$getSpawnRange());
            double x = candidate.getX();
            double y = candidate.getY();
            double z = candidate.getZ();

            if (!world.doesNotCollide(entityType.get().createSimpleBoundingBox(x, y, z))) {
                zsgRooms$logAttempt(key, event, attempt, candidate, "collision");
                continue;
            }
            if (!SpawnRestriction.canSpawn(
                    entityType.get(),
                    world.getWorld(),
                    SpawnReason.SPAWNER,
                    new BlockPos(x, y, z),
                    candidate.getAttemptRandom())) {
                zsgRooms$logAttempt(key, event, attempt, candidate, "spawn_restriction");
                continue;
            }

            Entity entity = EntityType.loadEntityWithPassengers(entityTag, world, loaded -> {
                loaded.refreshPositionAndAngles(x, y, z, loaded.yaw, loaded.pitch);
                return loaded;
            });
            if (entity == null) {
                zsgRooms$logAttempt(key, event, attempt, candidate, "entity_load_failed");
                zsgRooms$scheduleDelay(logic, accessor, world, key, true);
                return;
            }

            int nearby = world.getNonSpectatingEntities(
                    entity.getClass(),
                    new Box(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
                            .expand(accessor.zsgRooms$getSpawnRange()))
                    .size();
            if (nearby >= accessor.zsgRooms$getMaxNearbyEntities()) {
                zsgRooms$logAttempt(key, event, attempt, candidate, "nearby_cap");
                zsgRooms$scheduleDelay(logic, accessor, world, key, true);
                return;
            }

            entity.refreshPositionAndAngles(
                    entity.getX(), entity.getY(), entity.getZ(),
                    world.random.nextFloat() * 360.0F, 0.0F);
            if (entity instanceof MobEntity) {
                MobEntity mob = (MobEntity) entity;
                if (!mob.canSpawn(world, SpawnReason.SPAWNER) || !mob.canSpawn(world)) {
                    zsgRooms$logAttempt(key, event, attempt, candidate, "mob_rejected");
                    continue;
                }
                if (entityTag.getSize() == 1 && entityTag.contains("id", 8)) {
                    mob.initialize(
                            world,
                            world.getLocalDifficulty(entity.getBlockPos()),
                            SpawnReason.SPAWNER,
                            null,
                            null);
                }
            }

            accessor.zsgRooms$invokeSpawnEntity(entity);
            world.syncWorldEvent(2004, pos, 0);
            if (entity instanceof MobEntity) {
                ((MobEntity) entity).playSpawnEffects();
            }
            spawned = true;
            zsgRooms$logAttempt(key, event, attempt, candidate, "spawned");
        }

        if (spawned) {
            zsgRooms$scheduleDelay(logic, accessor, world, key, true);
        } else {
            BlazeSpawnerStandardization.SpawnerEvent retry =
                    BlazeSpawnerStandardization.advanceRetryBatch(key);
            SeedDebugLog.info(
                    "[ZSG-Rooms/Spawner] key={} cycle={} batch={} result=retry_next_tick",
                    key, retry.getCycleIndex(), retry.getRetryBatchIndex());
        }
    }

    private void zsgRooms$scheduleDelay(MobSpawnerLogic logic, MobSpawnerLogicAccessor accessor,
                                        ServerWorld world, String key, boolean advanceCycle) {
        BlazeSpawnerStandardization.SpawnerEvent event = advanceCycle
                ? BlazeSpawnerStandardization.advanceCycle(key)
                : BlazeSpawnerStandardization.currentEvent(key);
        int delay = BlazeSpawnerStandardization.standardizedSpawnerDelay(
                world.getSeed(),
                key,
                event.getCycleIndex(),
                accessor.zsgRooms$getMinSpawnDelay(),
                accessor.zsgRooms$getMaxSpawnDelay());
        accessor.zsgRooms$setSpawnDelay(delay);
        logic.sendStatus(1);
        SeedDebugLog.info(
                "[ZSG-Rooms/Spawner] key={} cycle={} batch={} nextDelay={}",
                key, event.getCycleIndex(), event.getRetryBatchIndex(), delay);
    }

    private static boolean zsgRooms$isDefaultBlazeData(CompoundTag data) {
        return data != null
                && data.getSize() == 1
                && data.contains("id", 8)
                && zsgRooms$BLAZE_ID.toString().equals(data.getString("id"));
    }

    private static boolean zsgRooms$hasSupportedPotentials(List<MobSpawnerEntry> potentials) {
        return potentials != null
                && (potentials.isEmpty()
                || (potentials.size() == 1
                && zsgRooms$isDefaultBlazeData(potentials.get(0).getEntityTag())));
    }

    private static void zsgRooms$logAttempt(
            String key,
            BlazeSpawnerStandardization.SpawnerEvent event,
            int attempt,
            BlazeSpawnerStandardization.CandidatePosition candidate,
            String result
    ) {
        SeedDebugLog.info(
                "[ZSG-Rooms/Spawner] key={} cycle={} batch={} attempt={} position={},{},{} result={}",
                key,
                event.getCycleIndex(),
                event.getRetryBatchIndex(),
                attempt,
                candidate.getX(),
                candidate.getY(),
                candidate.getZ(),
                result);
    }
}
