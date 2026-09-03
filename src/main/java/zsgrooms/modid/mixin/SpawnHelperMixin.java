package zsgrooms.modid.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.StructureFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.NaturalSpawnRngAccess;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.SeedDebugLog;
import zsgrooms.modid.rng.MirroringRandom;
import zsgrooms.modid.rng.NaturalSpawnCycle;
import zsgrooms.modid.rng.NaturalSpawnRngManager;
import zsgrooms.modid.rng.NaturalSpawnScope;

import java.util.Random;

@Mixin(SpawnHelper.class)
public abstract class SpawnHelperMixin {
    private static final String OUTER_SPAWN_METHOD =
            "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;"
                    + "Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/world/chunk/WorldChunk;"
                    + "Lnet/minecraft/world/SpawnHelper$Checker;"
                    + "Lnet/minecraft/world/SpawnHelper$Runner;)V";
    private static final String INNER_SPAWN_METHOD =
            "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;"
                    + "Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/world/chunk/Chunk;"
                    + "Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/world/SpawnHelper$Checker;"
                    + "Lnet/minecraft/world/SpawnHelper$Runner;)V";
    private static final ThreadLocal<NaturalSpawnCycle> ACTIVE_CYCLE =
            new ThreadLocal<NaturalSpawnCycle>();

    @Shadow
    private static Biome.SpawnEntry pickRandomSpawnEntry(
            ServerWorld world,
            StructureAccessor structureAccessor,
            ChunkGenerator chunkGenerator,
            SpawnGroup spawnGroup,
            Random random,
            BlockPos pos
    ) {
        throw new AssertionError();
    }

    @Inject(method = OUTER_SPAWN_METHOD, at = @At("HEAD"))
    private static void zsgRooms$beginNaturalSpawnCycle(
            SpawnGroup spawnGroup,
            ServerWorld world,
            WorldChunk chunk,
            SpawnHelper.Checker checker,
            SpawnHelper.Runner runner,
            CallbackInfo ci
    ) {
        ACTIVE_CYCLE.remove();
        if (!NaturalSpawnScope.applies(
                RngStandardization.isEnabled(), world.getRegistryKey(), spawnGroup)) {
            return;
        }

        NaturalSpawnRngManager manager = ((NaturalSpawnRngAccess) world.getServer())
                .zsgRooms$getNaturalSpawnRngManager();
        if (manager == null) {
            return;
        }

        NaturalSpawnCycle cycle = manager.nextCycle(
                world.getRegistryKey().getValue(), spawnGroup, chunk.getPos());
        ACTIVE_CYCLE.set(cycle);
    }

    @Inject(method = OUTER_SPAWN_METHOD, at = @At("RETURN"))
    private static void zsgRooms$endNaturalSpawnCycle(
            SpawnGroup spawnGroup,
            ServerWorld world,
            WorldChunk chunk,
            SpawnHelper.Checker checker,
            SpawnHelper.Runner runner,
            CallbackInfo ci
    ) {
        ACTIVE_CYCLE.remove();
    }

    @Redirect(
            method = "getSpawnPos",
            at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I")
    )
    private static int zsgRooms$standardizeInitialPosition(Random original, int bound) {
        int vanillaResult = original.nextInt(bound);
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        return cycle == null ? vanillaResult : cycle.getPositionRandom().nextInt(bound);
    }

    @Redirect(
            method = INNER_SPAWN_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Random;nextFloat()F",
                    ordinal = 0)
    )
    private static float zsgRooms$standardizeAttemptCount(Random original) {
        float vanillaResult = original.nextFloat();
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle == null) {
            return vanillaResult;
        }

        cycle.beginPack();
        float standardizedResult = cycle.getAttemptCountRandom().nextFloat();
        return standardizedResult;
    }

    @Redirect(
            method = INNER_SPAWN_METHOD,
            at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I"),
            slice = @Slice(
                    from = @At("HEAD"),
                    to = @At(
                            value = "INVOKE",
                            target = "Ljava/util/Random;nextInt(I)I",
                            ordinal = 3))
    )
    private static int zsgRooms$standardizeAttemptOffset(Random original, int bound) {
        int vanillaResult = original.nextInt(bound);
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        return cycle == null ? vanillaResult : cycle.nextOffsetInt(bound);
    }

    @Redirect(
            method = INNER_SPAWN_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/SpawnHelper;pickRandomSpawnEntry("
                            + "Lnet/minecraft/server/world/ServerWorld;"
                            + "Lnet/minecraft/world/gen/StructureAccessor;"
                            + "Lnet/minecraft/world/gen/chunk/ChunkGenerator;"
                            + "Lnet/minecraft/entity/SpawnGroup;"
                            + "Ljava/util/Random;"
                            + "Lnet/minecraft/util/math/BlockPos;)"
                            + "Lnet/minecraft/world/biome/Biome$SpawnEntry;")
    )
    private static Biome.SpawnEntry zsgRooms$standardizeSpawnSelection(
            ServerWorld world,
            StructureAccessor structureAccessor,
            ChunkGenerator chunkGenerator,
            SpawnGroup spawnGroup,
            Random original,
            BlockPos pos
    ) {
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        boolean fortressCandidate = cycle != null
                && zsgRooms$usesFortressSpawnTable(world, structureAccessor, pos);
        if (cycle != null) {
            cycle.setCurrentAttemptFortress(fortressCandidate);
        }
        Random selectionRandom = cycle == null
                ? original
                : new MirroringRandom(original, cycle.getSelectionRandom());
        Biome.SpawnEntry selected = pickRandomSpawnEntry(
                world,
                structureAccessor,
                chunkGenerator,
                spawnGroup,
                selectionRandom,
                pos);

        if (fortressCandidate && SeedDebugLog.isEnabled()) {
            SeedDebugLog.info(
                    "[ZSG-Rooms/NaturalSpawn] selection chunk={},{} cycle={} "
                            + "pack={} attempt={} pos={} fortress={} entity={}",
                    cycle.getSection().getChunkX(),
                    cycle.getSection().getChunkZ(),
                    cycle.getCycleIndex(),
                    cycle.getPackIndex(),
                    cycle.getAttemptIndex(),
                    pos,
                    fortressCandidate,
                    selected == null
                            ? "none"
                            : Registry.ENTITY_TYPE.getId(selected.type));
        }
        return selected;
    }

    @Redirect(
            method = INNER_SPAWN_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Random;nextInt(I)I",
                    ordinal = 4)
    )
    private static int zsgRooms$standardizePackSize(Random original, int bound) {
        int vanillaResult = original.nextInt(bound);
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        return cycle == null
                ? vanillaResult
                : cycle.getSelectionRandom().nextInt(bound);
    }

    @ModifyArg(
            method = "canSpawn(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/SpawnGroup;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/gen/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/world/biome/Biome$SpawnEntry;"
                    + "Lnet/minecraft/util/math/BlockPos$Mutable;D)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/SpawnRestriction;canSpawn("
                            + "Lnet/minecraft/entity/EntityType;"
                            + "Lnet/minecraft/world/WorldAccess;"
                            + "Lnet/minecraft/entity/SpawnReason;"
                            + "Lnet/minecraft/util/math/BlockPos;"
                            + "Ljava/util/Random;)Z"),
            index = 4
    )
    private static Random zsgRooms$standardizeSpawnCheck(Random original) {
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        return cycle == null
                ? original
                : new MirroringRandom(original, cycle.getSpawnCheckRandom());
    }

    @Inject(
            method = "canSpawn(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/SpawnGroup;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/gen/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/world/biome/Biome$SpawnEntry;"
                    + "Lnet/minecraft/util/math/BlockPos$Mutable;D)Z",
            at = @At("RETURN")
    )
    private static void zsgRooms$logEnvironmentCheck(
            ServerWorld world,
            SpawnGroup spawnGroup,
            StructureAccessor structureAccessor,
            ChunkGenerator chunkGenerator,
            Biome.SpawnEntry spawnEntry,
            BlockPos.Mutable pos,
            double squaredDistance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle == null || !SeedDebugLog.isEnabled()) {
            return;
        }

        boolean fortressCandidate = zsgRooms$usesFortressSpawnTable(
                world, structureAccessor, pos);
        cycle.setCurrentAttemptFortress(fortressCandidate);
        if (!fortressCandidate) {
            return;
        }
        SeedDebugLog.info(
                "[ZSG-Rooms/NaturalSpawn] environment-check chunk={},{} cycle={} "
                        + "pack={} attempt={} pos={} fortress={} entity={} allowed={}",
                cycle.getSection().getChunkX(),
                cycle.getSection().getChunkZ(),
                cycle.getCycleIndex(),
                cycle.getPackIndex(),
                cycle.getAttemptIndex(),
                pos,
                fortressCandidate,
                Registry.ENTITY_TYPE.getId(spawnEntry.type),
                cir.getReturnValue());
    }

    @Redirect(
            method = INNER_SPAWN_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/SpawnHelper$Checker;test("
                            + "Lnet/minecraft/entity/EntityType;"
                            + "Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/world/chunk/Chunk;)Z")
    )
    private static boolean zsgRooms$logDensityCheck(
            SpawnHelper.Checker checker,
            EntityType<?> entityType,
            BlockPos pos,
            Chunk chunk
    ) {
        boolean allowed = checker.test(entityType, pos, chunk);
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle != null
                && cycle.isCurrentAttemptFortress()
                && SeedDebugLog.isEnabled()) {
            SeedDebugLog.info(
                    "[ZSG-Rooms/NaturalSpawn] density-check chunk={},{} cycle={} "
                            + "pack={} attempt={} pos={} entity={} allowed={}",
                    cycle.getSection().getChunkX(),
                    cycle.getSection().getChunkZ(),
                    cycle.getCycleIndex(),
                    cycle.getPackIndex(),
                    cycle.getAttemptIndex(),
                    pos,
                    Registry.ENTITY_TYPE.getId(entityType),
                    allowed);
        }
        return allowed;
    }

    @Inject(
            method = "isValidSpawn(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/mob/MobEntity;D)Z",
            at = @At("RETURN")
    )
    private static void zsgRooms$logEntityCheck(
            ServerWorld world,
            MobEntity mob,
            double squaredDistance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle != null
                && cycle.isCurrentAttemptFortress()
                && SeedDebugLog.isEnabled()) {
            SeedDebugLog.info(
                    "[ZSG-Rooms/NaturalSpawn] entity-check chunk={},{} cycle={} "
                            + "pack={} attempt={} pos={} entity={} allowed={}",
                    cycle.getSection().getChunkX(),
                    cycle.getSection().getChunkZ(),
                    cycle.getCycleIndex(),
                    cycle.getPackIndex(),
                    cycle.getAttemptIndex(),
                    mob.getBlockPos(),
                    Registry.ENTITY_TYPE.getId(mob.getType()),
                    cir.getReturnValue());
        }
    }

    @Redirect(
            method = INNER_SPAWN_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/SpawnHelper$Runner;run("
                            + "Lnet/minecraft/entity/mob/MobEntity;"
                            + "Lnet/minecraft/world/chunk/Chunk;)V")
    )
    private static void zsgRooms$logAcceptedSpawn(
            SpawnHelper.Runner runner,
            MobEntity mob,
            Chunk chunk
    ) {
        runner.run(mob, chunk);
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle == null
                || !cycle.isCurrentAttemptFortress()
                || !SeedDebugLog.isEnabled()) {
            return;
        }

        SeedDebugLog.info(
                "[ZSG-Rooms/NaturalSpawn] accepted chunk={},{} cycle={} pack={} "
                        + "attempt={} pos={} fortress={} entity={}",
                cycle.getSection().getChunkX(),
                cycle.getSection().getChunkZ(),
                cycle.getCycleIndex(),
                cycle.getPackIndex(),
                cycle.getAttemptIndex(),
                mob.getBlockPos(),
                true,
                Registry.ENTITY_TYPE.getId(mob.getType()));
    }

    private static boolean zsgRooms$usesFortressSpawnTable(
            ServerWorld world,
            StructureAccessor structureAccessor,
            BlockPos pos
    ) {
        return world.getBlockState(pos.down()).getBlock() == Blocks.NETHER_BRICKS
                && structureAccessor.method_28388(
                        pos, false, StructureFeature.FORTRESS).hasChildren();
    }
}
