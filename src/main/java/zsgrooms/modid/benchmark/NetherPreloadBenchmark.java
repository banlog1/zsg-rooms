package zsgrooms.modid.benchmark;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import zsgrooms.modid.InGame;
import zsgrooms.modid.NetherPortalPreloader;
import zsgrooms.modid.SharedNetherEntry;
import zsgrooms.modid.SharedNetherEntryState;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.mixin.NetherPreloadChunkAccessor;
import zsgrooms.modid.ui.RoomUiPreferences;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Disposable-world, real-tick experiment. No client, forced completion or changed terrain. */
public final class NetherPreloadBenchmark {
    private static final String ROOM = "NetherPreloadBenchmark";
    private static final String MODE = System.getProperty("zsgrooms.netherPreloadBenchmark.mode", "current");
    private static final int TRIALS = Integer.getInteger("zsgrooms.netherPreloadBenchmark.trials", 4);
    private static ServerPlayerEntity player;
    private static ChunkPos center;
    private static int trial;
    private static int phaseTick = -360;
    private static boolean finished;
    private static long tickStarted;
    private static long chargeStarted;
    private static long gcStarted;
    private static long cpuStarted;
    private static int terrainTick;
    private static int fullTick;
    private static final List<Double> ticks = new ArrayList<>();
    private static CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> request;
    private static Map<String, Object> result;
    private static final ChunkTicketType<UUID> RING_TICKET = ChunkTicketType.create("preload_benchmark_ring", UUID::compareTo, 120);
    private static final List<ChunkPos> ring = new ArrayList<>();
    private static final List<ChunkPos> pendingRing = new ArrayList<>();

    private NetherPreloadBenchmark() { }

    public static boolean isEnabled() {
        return Boolean.getBoolean("zsgrooms.netherPreloadBenchmark");
    }

    public static void register() {
        if (!isEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!MODE.equals("off") && !MODE.equals("current") && !MODE.equals("request") && !MODE.equals("ring")) {
                throw new IllegalArgumentException("Unknown preload benchmark mode " + MODE);
            }
            if (TRIALS < 1 || TRIALS > 16) throw new IllegalArgumentException("Use 1 to 16 trials");
            ZsgRooms.createRoom(ROOM, 1, 1, "generic");
            InGame game = ZsgRooms.getGame(ROOM);
            game.setNetherEntryWarmup(!MODE.equals("off"));
            game.startGame();
            RoomUiPreferences.setNetherEntryWarmupEnabled(true);
            RoomUiPreferences.setSeedDebugLoggingEnabled(false);
            ZsgRooms.LOGGER.info("[NetherPreloadBench] mode={} seed={} java={} processors={}", MODE,
                    server.getOverworld().getSeed(), System.getProperty("java.version"),
                    Runtime.getRuntime().availableProcessors());
        });
        ServerTickEvents.START_SERVER_TICK.register(NetherPreloadBenchmark::startTick);
        ServerTickEvents.END_SERVER_TICK.register(NetherPreloadBenchmark::endTick);
    }

    private static void startTick(MinecraftServer server) {
        if (finished) return;
        try {
            if (phaseTick == -320) prepare(server);
            if (phaseTick < 0) return;
            tickStarted = System.nanoTime();
            if (phaseTick == 0) {
                chargeStarted = tickStarted;
                gcStarted = gcTime();
                cpuStarted = cpuTime();
            }
            ServerWorld nether = server.getWorld(World.NETHER);
            if (phaseTick < 80) {
                NetherPortalPreloader.tick(player, true, phaseTick);
                if (MODE.equals("ring")) {
                    if (phaseTick == 0) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                ChunkPos pos = new ChunkPos(center.x + x, center.z + z);
                                ring.add(pos);
                                pendingRing.add(pos);
                                nether.getChunkManager().addTicket(RING_TICKET, pos, -1, player.getUuid());
                            }
                        }
                    }
                    pendingRing.removeIf(pos -> ((NetherPreloadChunkAccessor) nether.getChunkManager())
                            .zsgRooms$requestChunkFuture(pos.x, pos.z, ChunkStatus.FEATURES, false)
                            != ChunkHolder.UNLOADED_CHUNK_FUTURE);
                } else if (MODE.equals("request") && request == null) {
                    CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future =
                            ((NetherPreloadChunkAccessor) nether.getChunkManager()).zsgRooms$requestChunkFuture(
                                    center.x, center.z, ChunkStatus.FEATURES, false);
                    if (future != ChunkHolder.UNLOADED_CHUNK_FUTURE) request = future;
                }
                return;
            }
            result = new LinkedHashMap<>();
            result.put("mode", MODE);
            result.put("trial", trial);
            result.put("seed", Long.toString(server.getOverworld().getSeed()));
            result.put("center", center.toString());
            result.put("terrainTick", terrainTick);
            result.put("fullTick", fullTick);
            if (Boolean.getBoolean("zsgrooms.netherPreloadBenchmark.expectReady") && terrainTick < 0) {
                throw new IllegalStateException("Terrain never became ready during the real portal charge");
            }
            result.put("chargeWallMs", (tickStarted - chargeStarted) / 1e6);
            if (tickStarted - chargeStarted < 3_800_000_000L) {
                throw new IllegalStateException("Server catch-up shortened the portal charge; discard this run");
            }
            result.put("chargeTicksMs", new ArrayList<>(ticks));
            result.put("chargeCpuMs", (cpuTime() - cpuStarted) / 1e6);
            result.put("netherHoldersBefore", nether.getChunkManager().getLoadedChunkCount());
            long transfer = System.nanoTime();
            player.setInNetherPortal(player.getBlockPos());
            player.changeDimension(nether);
            NetherPortalPreloader.afterVanillaTransfer(player);
            result.put("transferMs", (System.nanoTime() - transfer) / 1e6);
            if (player.world != nether) throw new IllegalStateException("Transfer failed");
            result.put("arrival", player.getPos().toString());
            result.put("portalAxis", nether.getBlockState(player.getBlockPos()).get(NetherPortalBlock.AXIS).toString());
            result.put("netherHoldersAfter", nether.getChunkManager().getLoadedChunkCount());
            result.put("chargeAndTransferCpuMs", (cpuTime() - cpuStarted) / 1e6);
            result.put("gcMs", gcTime() - gcStarted);
            result.put("usedHeapMiB", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576);
            releaseRing(server);
            player.getServerWorld().removePlayer(player);
            player = null;
        } catch (Throwable error) {
            fail(server, error);
        }
    }

    private static void endTick(MinecraftServer server) {
        if (finished) return;
        try {
            if (phaseTick >= 0 && phaseTick < 80) {
                ticks.add((System.nanoTime() - tickStarted) / 1e6);
                Object chunk = server.getWorld(World.NETHER).getChunkManager().getChunk(center.x, center.z);
                if (terrainTick < 0 && chunk instanceof Chunk
                        && ((Chunk) chunk).getStatus().isAtLeast(ChunkStatus.FEATURES)) terrainTick = phaseTick;
                if (fullTick < 0 && chunk instanceof WorldChunk) fullTick = phaseTick;
            }
            if (phaseTick == 80) {
                result.put("transferTickMs", (System.nanoTime() - tickStarted) / 1e6);
                String json = new Gson().toJson(result);
                Files.write(Paths.get("results.jsonl"), (json + "\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                ZsgRooms.LOGGER.info("[NetherPreloadBench] RESULT {}", json);
                if (++trial == TRIALS) {
                    finished = true;
                    NetherPortalPreloader.stop(server);
                    ZsgRooms.LOGGER.info("[NetherPreloadBench] PASS");
                    server.stop(false);
                    return;
                }
                phaseTick = -360;
                return;
            }
            phaseTick++;
        } catch (Throwable error) {
            fail(server, error);
        }
    }

    private static void prepare(MinecraftServer server) {
        int coordinate = 32000 + trial * 16000;
        BlockPos origin = new BlockPos(coordinate, 70, 32000);
        ServerWorld world = server.getOverworld();
        world.setSpawnPos(origin);
        SharedNetherEntryState.get(world).fromTag(new CompoundTag());
        SharedNetherEntry.configure(server, true);
        player = new ServerPlayerEntity(server, world, new GameProfile(new UUID(0, trial + 1), "PreloadBench"),
                new ServerPlayerInteractionManager(world)) {
            { lastNetherPortalDirectionVector = new Vec3d(0.5, 0, 0); lastNetherPortalDirection = Direction.NORTH; }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), player) {
            @Override public void sendPacket(Packet<?> packet) { }
        };
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                boolean frame = x == -1 || x == 2 || y == -1 || y == 3;
                world.setBlockState(origin.add(x, y, 0), frame ? Blocks.OBSIDIAN.getDefaultState()
                        : Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, Direction.Axis.X), 18);
            }
        }
        player.refreshPositionAndAngles(origin.getX() + 0.6, origin.getY(), origin.getZ() + 0.6, 0, 0);
        center = SharedNetherEntry.preloadCenter(player);
        if (server.getWorld(World.NETHER).getChunkManager().getChunk(center.x, center.z) != null) {
            throw new IllegalStateException("Target is already generated in memory");
        }
        request = null;
        ticks.clear();
        terrainTick = -1;
        fullTick = -1;
    }

    private static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }

    private static long cpuTime() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
    }

    private static void fail(MinecraftServer server, Throwable error) {
        finished = true;
        releaseRing(server);
        NetherPortalPreloader.stop(server);
        ZsgRooms.LOGGER.error("[NetherPreloadBench] FAIL", error);
        server.stop(false);
    }

    private static void releaseRing(MinecraftServer server) {
        if (player != null) {
            for (ChunkPos pos : ring) {
                server.getWorld(World.NETHER).getChunkManager().removeTicket(RING_TICKET, pos, -1, player.getUuid());
            }
        }
        ring.clear();
        pendingRing.clear();
    }
}
