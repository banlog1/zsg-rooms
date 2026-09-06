package zsgrooms.modid.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
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
import zsgrooms.modid.rng.NaturalSpawnDiagnostics;
import zsgrooms.modid.rng.NaturalSpawnRngManager;
import zsgrooms.modid.rng.NaturalSpawnScope;
import zsgrooms.modid.rng.FortressSpawnProtection;
import zsgrooms.modid.rng.FortressSpawnOrder;

import java.util.List;
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
    private static final ThreadLocal<String> REJECTION_REASON = new ThreadLocal<String>();
    private static final String ENVIRONMENT_METHOD =
            "canSpawn(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/SpawnGroup;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/gen/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/world/biome/Biome$SpawnEntry;"
                    + "Lnet/minecraft/util/math/BlockPos$Mutable;D)Z";
    private static final String ENTITY_CHECK_METHOD =
            "isValidSpawn(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/mob/MobEntity;D)Z";

    // The 1.16.1 bytecode calls isBelowCap through this Java 8 synthetic bridge.
    @Redirect(method = "spawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/SpawnHelper$Info;method_27829(Lnet/minecraft/world/SpawnHelper$Info;Lnet/minecraft/entity/SpawnGroup;)Z"))
    private static boolean zsgRooms$observeMobCap(
            SpawnHelper.Info info, SpawnGroup group, ServerWorld world, WorldChunk chunk,
            SpawnHelper.Info unusedInfo, boolean animals, boolean monsters, boolean periodicAnimals
    ) {
        FortressSpawnProtection.clear();
        SpawnHelperInfoAccessor accessor = (SpawnHelperInfoAccessor) info;
        boolean allowed = accessor.zsgRooms$invokeIsBelowCap(group);
        boolean standardized = NaturalSpawnScope.applies(
                RngStandardization.isEnabled(), world.getRegistryKey(), group)
                && ((NaturalSpawnRngAccess) world.getServer()).zsgRooms$getNaturalSpawnRngManager() != null;
        if (standardized) {
            chunk = FortressSpawnOrder.target(world, chunk);
        }
        if (SeedDebugLog.isEnabled()
                && standardized
                && NaturalSpawnDiagnostics.hasFortressReference(chunk)) {
            int spawningChunks = accessor.zsgRooms$getSpawningChunkCount();
            NaturalSpawnDiagnostics.recordCap(world, chunk, allowed,
                    info.getGroupToCount().getInt(group), group.getCapacity() * spawningChunks / 289,
                    spawningChunks);
        }
        return standardized ? FortressSpawnProtection.beginPass(world, chunk, allowed) : allowed;
    }

    @Redirect(method = "spawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/SpawnHelper;" + OUTER_SPAWN_METHOD))
    private static void zsgRooms$dispatchSpawn(
            SpawnGroup group, ServerWorld world, WorldChunk chunk,
            SpawnHelper.Checker checker, SpawnHelper.Runner runner
    ) {
        if (NaturalSpawnScope.applies(RngStandardization.isEnabled(), world.getRegistryKey(), group)) {
            chunk = FortressSpawnOrder.target(world, chunk);
        }
        try {
            SpawnHelper.spawnEntitiesInChunk(group, world, chunk, checker, runner);
        } finally {
            FortressSpawnProtection.clear();
        }
    }

    @Inject(method = "method_29950", at = @At("RETURN"))
    private static void zsgRooms$observeActualTable(
            ServerWorld world, StructureAccessor structures, ChunkGenerator generator,
            SpawnGroup group, BlockPos pos, Biome biome,
            CallbackInfoReturnable<List<Biome.SpawnEntry>> cir
    ) {
        FortressSpawnProtection.observeTable(world, pos, cir.getReturnValue());
    }

    @Inject(method = "containsSpawnEntry", at = @At("RETURN"), cancellable = true)
    private static void zsgRooms$limitProtectedCandidate(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !FortressSpawnProtection.allowsCandidate()) {
            REJECTION_REASON.set("outside_protected_fortress");
            cir.setReturnValue(false);
        }
    }

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
        REJECTION_REASON.remove();
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
        REJECTION_REASON.remove();
    }

    @Inject(method = "getSpawnPos", at = @At("RETURN"))
    private static void zsgRooms$observeInitialPosition(
            World world, WorldChunk chunk, CallbackInfoReturnable<BlockPos> cir
    ) {
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle != null && SeedDebugLog.isEnabled() && world instanceof ServerWorld
                && NaturalSpawnDiagnostics.hasFortressReference(chunk)) {
            BlockPos pos = cir.getReturnValue();
            NaturalSpawnDiagnostics.recordInitial((ServerWorld) world, chunk, cycle.getCycleIndex(),
                    pos.getY() < 1, pos.getY() >= 1 && chunk.getBlockState(pos).isSolidBlock(chunk, pos));
        }
    }

    @Redirect(method = INNER_SPAWN_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;getClosestPlayer(DDDDZ)Lnet/minecraft/entity/player/PlayerEntity;"))
    private static PlayerEntity zsgRooms$observeNearestPlayer(
            ServerWorld world, double x, double y, double z, double distance, boolean ignoreCreative
    ) {
        PlayerEntity player = world.getClosestPlayer(x, y, z, distance, ignoreCreative);
        if (player == null && ACTIVE_CYCLE.get() != null && SeedDebugLog.isEnabled()) {
            BlockPos pos = new BlockPos(x, y, z);
            if (zsgRooms$usesFortressSpawnTable(world, world.getStructureAccessor(), pos)) {
                zsgRooms$logEarlyRejection(world, pos, "no_eligible_player", -1.0);
            }
        }
        return player;
    }

    @Inject(method = "isAcceptableSpawnPosition", at = @At("RETURN"))
    private static void zsgRooms$observePlayerDistance(
            ServerWorld world, Chunk chunk, BlockPos.Mutable pos, double squaredDistance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() && ACTIVE_CYCLE.get() != null && SeedDebugLog.isEnabled()
                && zsgRooms$usesFortressSpawnTable(world, world.getStructureAccessor(), pos)) {
            String reason = squaredDistance <= 576.0 ? "player_within_24_blocks"
                    : world.getSpawnPos().isWithinDistance(
                            new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), 24.0)
                            ? "world_spawn_within_24_blocks" : "candidate_chunk_not_ticking";
            zsgRooms$logEarlyRejection(world, pos, reason, squaredDistance);
        }
    }

    private static void zsgRooms$logEarlyRejection(
            ServerWorld world, BlockPos pos, String reason, double squaredDistance
    ) {
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        SeedDebugLog.info("[ZSG-Rooms/NaturalSpawn] early-rejection chunk={},{} cycle={} "
                        + "pack={} attempt={} pos={} tick={} reason={} distanceSquared={}",
                cycle.getSection().getChunkX(), cycle.getSection().getChunkZ(), cycle.getCycleIndex(),
                cycle.getPackIndex(), cycle.getAttemptIndex(), pos, world.getTime(), reason, squaredDistance);
    }

    @Inject(method = ENVIRONMENT_METHOD, at = @At("HEAD"), cancellable = true)
    private static void zsgRooms$beginEnvironmentTrace(
            ServerWorld world, SpawnGroup group, StructureAccessor structures, ChunkGenerator generator,
            Biome.SpawnEntry entry, BlockPos.Mutable pos, double squaredDistance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        REJECTION_REASON.remove();
        if (!FortressSpawnProtection.allowsPack()) {
            REJECTION_REASON.set("unprotected_pack_at_cap");
            cir.setReturnValue(false);
            return;
        }
        if (ACTIVE_CYCLE.get() != null && SeedDebugLog.isEnabled()
                && zsgRooms$usesFortressSpawnTable(world, structures, pos)) {
            String reason = entry.type.getSpawnGroup() == SpawnGroup.MISC ? "misc_spawn_group"
                    : !entry.type.isSpawnableFarFromPlayer()
                            && squaredDistance > Math.pow(entry.type.getSpawnGroup().getImmediateDespawnRange(), 2)
                            ? "beyond_spawn_distance" : "entity_not_summonable";
            REJECTION_REASON.set(reason);
        }
    }

    @Inject(method = ENVIRONMENT_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/SpawnHelper;containsSpawnEntry(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/world/biome/Biome$SpawnEntry;Lnet/minecraft/util/math/BlockPos;)Z"))
    private static void zsgRooms$traceSpawnTable(CallbackInfoReturnable<Boolean> cir) {
        zsgRooms$traceReason("spawn_table_mismatch");
    }

    @Inject(method = ENVIRONMENT_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/SpawnHelper;canSpawn(Lnet/minecraft/entity/SpawnRestriction$Location;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/EntityType;)Z"))
    private static void zsgRooms$traceTerrain(CallbackInfoReturnable<Boolean> cir) {
        zsgRooms$traceReason("terrain_or_fluid_or_border");
    }

    @Inject(method = ENVIRONMENT_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/SpawnRestriction;canSpawn(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)Z"))
    private static void zsgRooms$traceRestriction(CallbackInfoReturnable<Boolean> cir) {
        zsgRooms$traceReason("spawn_restriction");
    }

    @Inject(method = ENVIRONMENT_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;doesNotCollide(Lnet/minecraft/util/math/Box;)Z"))
    private static void zsgRooms$traceBoundingBox(CallbackInfoReturnable<Boolean> cir) {
        zsgRooms$traceReason("bounding_box_collision");
    }

    @Inject(method = ENTITY_CHECK_METHOD, at = @At("HEAD"))
    private static void zsgRooms$beginEntityTrace(
            ServerWorld world, MobEntity mob, double squaredDistance, CallbackInfoReturnable<Boolean> cir
    ) {
        REJECTION_REASON.remove();
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle != null && cycle.isCurrentAttemptFortress() && SeedDebugLog.isEnabled()) {
            REJECTION_REASON.set("immediate_despawn_distance");
        }
    }

    @Inject(method = ENTITY_CHECK_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/mob/MobEntity;canSpawn(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;)Z"))
    private static void zsgRooms$traceEntityRule(CallbackInfoReturnable<Boolean> cir) {
        zsgRooms$traceReason("entity_spawn_rule");
    }

    @Inject(method = ENTITY_CHECK_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/mob/MobEntity;canSpawn(Lnet/minecraft/world/WorldView;)Z"))
    private static void zsgRooms$traceEntitySpace(CallbackInfoReturnable<Boolean> cir) {
        zsgRooms$traceReason("entity_space_or_fluid");
    }

    private static void zsgRooms$traceReason(String reason) {
        if (REJECTION_REASON.get() != null) {
            REJECTION_REASON.set(reason);
        }
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
        FortressSpawnProtection.beginPack();
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
        boolean fortressCandidate = cycle != null && SeedDebugLog.isEnabled()
                && zsgRooms$usesFortressSpawnTable(world, structureAccessor, pos);
        if (cycle != null) {
            cycle.setCurrentAttemptFortress(fortressCandidate);
        }
        Random selectionRandom = cycle == null
                ? original
                : new MirroringRandom(original, cycle.getSelectionRandom());
        FortressSpawnProtection.beginSelection();
        Biome.SpawnEntry selected = pickRandomSpawnEntry(
                world,
                structureAccessor,
                chunkGenerator,
                spawnGroup,
                selectionRandom,
                pos);
        FortressSpawnProtection.finishSelection(cycle, pos, selected);

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
        String reason = cir.getReturnValue() ? "none" : REJECTION_REASON.get();
        REJECTION_REASON.remove();
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
                        + "pack={} attempt={} pos={} fortress={} entity={} allowed={} tick={} reason={}",
                cycle.getSection().getChunkX(),
                cycle.getSection().getChunkZ(),
                cycle.getCycleIndex(),
                cycle.getPackIndex(),
                cycle.getAttemptIndex(),
                pos,
                fortressCandidate,
                Registry.ENTITY_TYPE.getId(spawnEntry.type),
                cir.getReturnValue(), world.getTime(), reason == null ? "untraced" : reason);
        if (!cir.getReturnValue()) {
            NaturalSpawnDiagnostics.rejectionDetails(world, pos, spawnEntry.type, null, squaredDistance);
        }
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
                            + "pack={} attempt={} pos={} entity={} allowed={} reason={}",
                    cycle.getSection().getChunkX(),
                    cycle.getSection().getChunkZ(),
                    cycle.getCycleIndex(),
                    cycle.getPackIndex(),
                    cycle.getAttemptIndex(),
                    pos,
                    Registry.ENTITY_TYPE.getId(entityType),
                    allowed, allowed ? "none" : "biome_spawn_density");
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
        String reason = cir.getReturnValue() ? "none" : REJECTION_REASON.get();
        REJECTION_REASON.remove();
        NaturalSpawnCycle cycle = ACTIVE_CYCLE.get();
        if (cycle != null
                && cycle.isCurrentAttemptFortress()
                && SeedDebugLog.isEnabled()) {
            SeedDebugLog.info(
                    "[ZSG-Rooms/NaturalSpawn] entity-check chunk={},{} cycle={} "
                            + "pack={} attempt={} pos={} entity={} allowed={} tick={} reason={}",
                    cycle.getSection().getChunkX(),
                    cycle.getSection().getChunkZ(),
                    cycle.getCycleIndex(),
                    cycle.getPackIndex(),
                    cycle.getAttemptIndex(),
                    mob.getBlockPos(),
                    Registry.ENTITY_TYPE.getId(mob.getType()),
                    cir.getReturnValue(), world.getTime(), reason == null ? "untraced" : reason);
            if (!cir.getReturnValue()) {
                NaturalSpawnDiagnostics.rejectionDetails(world, mob.getBlockPos(), mob.getType(),
                        mob, squaredDistance);
            }
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
        return FortressSpawnProtection.fortressAt(world, pos).hasChildren();
    }
}
