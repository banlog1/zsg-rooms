package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.StructureSpawnProximity;
import zsgrooms.modid.SurfacePoolRepairDiagnostics;
import zsgrooms.modid.ZsgRooms;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real-world runtime-repair regression test. Never invoked by production seed search. */
final class SurfacePoolCalibration {
    private static final int SCAN_RADIUS = SurfacePoolRepairDiagnostics.SCAN_RADIUS;
    private static final int POOL_RADIUS = SurfacePoolRepairDiagnostics.MAX_POOL_RADIUS;
    private final List<JsonObject> samples;
    private int nextTrial;
    private Map<BlockPos, BlockState> expected;
    private boolean finished;
    private int pairedWorlds;
    private int partialWorlds;

    private SurfacePoolCalibration(List<JsonObject> samples) { this.samples = samples; }

    static void run(MinecraftServer server) {
        try {
            List<JsonObject> samples = new ArrayList<>();
            Map<String, Integer> counts = new HashMap<>();
            int perType = Integer.getInteger("zsgrooms.poolCalibration.samples", 1);
            if (perType < 1 || perType > 5) throw new IllegalArgumentException("Use 1-5 samples per type");
            try (java.util.stream.Stream<String> lines = Files.lines(Paths.get(System.getProperty("zsgrooms.poolCalibration.bank")))) {
                for (String line : (Iterable<String>) lines::iterator) {
                    JsonObject row = new JsonParser().parse(line).getAsJsonObject();
                    String type = row.get("type").getAsString();
                    if ((!type.equals("temple") && !type.equals("village")) || counts.getOrDefault(type, 0) >= perType) continue;
                    if (!"zsg-model-only-v5".equals(row.get("profile").getAsString())) throw new AssertionError("Wrong profile");
                    samples.add(row);
                    counts.put(type, counts.getOrDefault(type, 0) + 1);
                    if (samples.size() == perType * 2) break;
                }
            }
            if (samples.size() != perType * 2) throw new AssertionError("Not enough calibration samples");
            int onlySample = Integer.getInteger("zsgrooms.poolCalibration.onlySample", 0);
            if (onlySample > 0) {
                JsonObject selected = samples.get(onlySample - 1);
                samples.clear();
                samples.add(selected);
            }
            // Each disposable world gets its own callback, rather than one multi-minute server tick.
            SurfacePoolCalibration test = new SurfacePoolCalibration(samples);
            ServerTickEvents.END_SERVER_TICK.register(test::tick);
        } catch (Throwable error) {
            ZsgRooms.LOGGER.error("[SurfacePoolTest] FAIL: {}", error.getMessage());
            server.stop(false);
        }
    }

    private void tick(MinecraftServer server) {
        if (finished) return;
        int index = nextTrial / 2;
        int trial = nextTrial % 2;
        JsonObject sample = samples.get(index);
        String type = sample.get("type").getAsString();
        // POI updates queued during village generation must execute before their world's storage closes.
        try (ProbeWorlds worlds = new ProbeWorlds(server, Long.parseLong(sample.get("seed").getAsString()), UUID.randomUUID().toString());
                AutoCloseable pendingUpdates = () -> server.runTasks(() -> server.getTaskCount() == 0)) {
            ProbeWorlds.determineSpawn(server);
            ServerWorld world = server.getOverworld();
            StructureFeature<?> feature = type.equals("temple") ? StructureFeature.DESERT_PYRAMID : StructureFeature.VILLAGE;
            BlockPos target = world.locateStructure(feature, world.getSpawnPos(), 160, false);
            check(target != null, "No target structure");
            // Prepare both inspection bands before snapshotting; this harness includes the full water range.
            SurfacePoolRepairDiagnostics.inspect(world, target, "rooms-" + type + "-v5");
            Map<BlockPos, BlockState> protectedBlocks = blockEntities(world, target);
            Map<BlockPos, BlockState> originalWater = waterSurface(world, target);
            StructureSpawnProximity.configure(false, false, "rooms-" + type + "-v5");
            long started = System.nanoTime();
            StructureSpawnProximity.prepare(world);
            long millis = (System.nanoTime() - started) / 1_000_000L;
            MinecraftSurfaceTerrain terrain = MinecraftSurfaceTerrain.capture(world, target.getX(), target.getZ(), POOL_RADIUS);
            check(originalWater.equals(waterSurface(world, target)), "Water surface changed during repair");
            List<SurfaceTerrainChecks.LavaPool> pools = SurfaceTerrainChecks.findLavaPools(terrain, target.getX(), target.getZ(), POOL_RADIUS);
            List<SurfaceTerrainChecks.LavaPool> wet = new ArrayList<>();
            for (SurfaceTerrainChecks.LavaPool pool : pools) if (hasWater(world, pool.anchor)) wet.add(pool);
            boolean separated = false;
            for (SurfaceTerrainChecks.LavaPool first : wet) {
                for (SurfaceTerrainChecks.LavaPool second : wet) {
                    if (SurfacePoolRepairDiagnostics.separated(first.anchor, second.anchor, target)) separated = true;
                }
            }
            if (separated) {
                pairedWorlds++;
            } else {
                partialWorlds++;
                ZsgRooms.LOGGER.warn("[SurfacePoolTest] PARTIAL: sample={}, type={}, trial={}, usable={}",
                        index + 1, type, trial + 1, wet.size());
                check(Boolean.getBoolean("zsgrooms.poolCalibration.allowPartial"), "Fewer than two separated pools with water");
            }
            protectedBlocks.forEach((pos, state) -> check(world.getBlockState(pos).equals(state), "Block entity changed"));
            Map<BlockPos, BlockState> actual = surface(world, target);
            // Include submerged cavities and banks, not just the visible liquid surface.
            for (SurfaceTerrainChecks.LavaPool pool : wet) {
                for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) for (int y = -4; y <= 5; y++) {
                    BlockPos pos = pool.anchor.add(x, y, z);
                    actual.put(pos, world.getBlockState(pos));
                }
            }
            if (trial == 0) expected = actual;
            else check(expected.equals(actual), "World-reset surface mismatch");

            // Execute the real scheduled fluid code repeatedly; the finished basins must remain contained.
            for (int tick = 0; tick < 8; tick++) {
                for (Map.Entry<BlockPos, BlockState> entry : actual.entrySet()) {
                    if (!entry.getValue().getFluidState().isEmpty()) {
                        world.getBlockState(entry.getKey()).getFluidState().onScheduledTick(world, entry.getKey());
                    }
                }
            }
            for (SurfaceTerrainChecks.LavaPool pool : wet) {
                check(pool.sources.stream().allMatch(pos -> world.getBlockState(pos).isOf(Blocks.LAVA)
                        && world.getFluidState(pos).isStill()), "Source lava lost after fluid ticks");
            }
            if (!wet.isEmpty()) {
                for (BlockPos source : wet.get(0).sources) world.setBlockState(source, Blocks.AIR.getDefaultState(), 18);
            }
            Map<BlockPos, BlockState> consumed = surface(world, target);
            StructureSpawnProximity.prepare(world);
            check(consumed.equals(surface(world, target)), "Preparation repeated after pool consumption");
            ZsgRooms.LOGGER.info("[SurfacePoolTest] sample={}, type={}, trial={}, usable={}, preparedTerrainMs={}",
                    index + 1, type, trial + 1, wet.size(), millis);
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[SurfacePoolTest] FAIL: {}", error.getMessage());
            server.stop(false);
            return;
        }
        if (++nextTrial == samples.size() * 2) {
            finished = true;
            ZsgRooms.LOGGER.info("[SurfacePoolTest] PASS: matching resets, natural water preserved, fluid containment, block entities and no refill; paired worlds={}, partial worlds={}",
                    pairedWorlds, partialWorlds);
            server.stop(false);
        }
    }

    private static Map<BlockPos, BlockState> surface(ServerWorld world, BlockPos origin) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (int z = origin.getZ() - SCAN_RADIUS; z <= origin.getZ() + SCAN_RADIUS; z++) {
            for (int x = origin.getX() - SCAN_RADIUS; x <= origin.getX() + SCAN_RADIUS; x++) {
                int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);
                result.put(pos, world.getBlockState(pos));
            }
        }
        return result;
    }

    private static Map<BlockPos, BlockState> waterSurface(ServerWorld world, BlockPos origin) {
        Map<BlockPos, BlockState> result = surface(world, origin);
        result.entrySet().removeIf(entry -> !entry.getValue().isOf(Blocks.WATER));
        return result;
    }

    private static Map<BlockPos, BlockState> blockEntities(ServerWorld world, BlockPos origin) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (int z = (origin.getZ() - SCAN_RADIUS) >> 4; z <= (origin.getZ() + SCAN_RADIUS) >> 4; z++) {
            for (int x = (origin.getX() - SCAN_RADIUS) >> 4; x <= (origin.getX() + SCAN_RADIUS) >> 4; x++) {
                for (BlockPos pos : world.getChunk(x, z).getBlockEntities().keySet()) result.put(pos, world.getBlockState(pos));
            }
        }
        return result;
    }

    private static boolean hasWater(ServerWorld world, BlockPos pool) {
        for (int z = -47; z <= 47; z++) {
            for (int x = -47; x <= 47; x++) {
                if (x * x + z * z > 47 * 47) continue;
                BlockPos water = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, pool.add(x, 0, z)).down();
                if (!world.getBlockState(water).isOf(Blocks.WATER) || Math.abs(water.getY() - pool.getY()) > 8) continue;
                boolean patch = true;
                for (int dz = -1; dz <= 2 && patch; dz++) {
                    for (int dx = -1; dx <= 2; dx++) {
                        BlockPos pos = water.add(dx, 0, dz);
                        if (!world.getBlockState(pos).isOf(Blocks.WATER) || !world.getFluidState(pos).isStill()) { patch = false; break; }
                    }
                }
                if (patch) return true;
            }
        }
        return false;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
