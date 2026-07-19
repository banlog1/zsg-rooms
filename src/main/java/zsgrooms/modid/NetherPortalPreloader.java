package zsgrooms.modid;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import zsgrooms.modid.mixin.ServerChunkManagerInvoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NetherPortalPreloader {
    static final int PRELOAD_RADIUS = 1;
    static final int MAX_IN_FLIGHT = 2;
    private static final int FULL_NON_TICKING_LEVEL = 33;
    private static final int TICKET_EXPIRY_TICKS = 120;
    private static final int MAX_DESTINATION_COORDINATE = 29999872;
    private static final int[][] CHUNK_OFFSETS = new int[][]{
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };
    private static final ChunkTicketType<ChunkPos> PRELOAD_TICKET = ChunkTicketType.create(
            "zsg_nether_preload",
            (left, right) -> Long.compare(left.toLong(), right.toLong()),
            TICKET_EXPIRY_TICKS
    );
    private static final Map<UUID, Warmup> WARMUPS = new HashMap<UUID, Warmup>();

    private NetherPortalPreloader() {
    }

    public static void tick(ServerPlayerEntity player, boolean inNetherPortal) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (!inNetherPortal || !isActiveOverworldRoomRun(player)) {
            WARMUPS.remove(playerId);
            return;
        }

        MinecraftServer server = player.getServer();
        ServerWorld nether = server == null ? null : server.getWorld(World.NETHER);
        if (nether == null) {
            WARMUPS.remove(playerId);
            return;
        }

        ChunkPos center = projectedNetherChunk(player.getX(), player.getZ());
        Warmup warmup = WARMUPS.get(playerId);
        if (warmup == null || warmup.world != nether || !warmup.center.equals(center)) {
            warmup = new Warmup(nether, center, chunkOrder(center));
            WARMUPS.put(playerId, warmup);
        }
        advanceWarmup(playerId, warmup);
    }

    static ChunkPos projectedNetherChunk(double overworldX, double overworldZ) {
        double netherX = MathHelper.clamp(overworldX / 8.0D,
                -MAX_DESTINATION_COORDINATE, MAX_DESTINATION_COORDINATE);
        double netherZ = MathHelper.clamp(overworldZ / 8.0D,
                -MAX_DESTINATION_COORDINATE, MAX_DESTINATION_COORDINATE);
        return new ChunkPos(MathHelper.floor(netherX) >> 4, MathHelper.floor(netherZ) >> 4);
    }

    static List<ChunkPos> chunkOrder(ChunkPos center) {
        List<ChunkPos> chunks = new ArrayList<ChunkPos>(CHUNK_OFFSETS.length);
        for (int[] offset : CHUNK_OFFSETS) {
            chunks.add(new ChunkPos(center.x + offset[0], center.z + offset[1]));
        }
        return chunks;
    }

    private static boolean isActiveOverworldRoomRun(ServerPlayerEntity player) {
        if (player.world == null || player.world.getRegistryKey() != World.OVERWORLD) {
            return false;
        }
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        return game != null && game.getIsInGame();
    }

    private static void advanceWarmup(UUID playerId, Warmup warmup) {
        if (warmup.failed || warmup.loggedComplete) {
            return;
        }

        collectCompleted(warmup);
        if (warmup.failed) {
            WARMUPS.remove(playerId);
            return;
        }

        try {
            while (warmup.pending.size() < MAX_IN_FLIGHT
                    && warmup.nextIndex < warmup.chunks.size()) {
                requestNextChunk(warmup);
            }
        } catch (RuntimeException error) {
            WARMUPS.remove(playerId);
            ZsgRooms.LOGGER.warn("Could not prepare Nether destination chunks: {}",
                    error.getClass().getSimpleName());
            return;
        }

        if (warmup.completed == warmup.chunks.size()) {
            warmup.loggedComplete = true;
            SeedDebugLog.info("Prepared {} Nether destination chunks during portal charge in {} ms",
                    warmup.chunks.size(), (System.nanoTime() - warmup.startedNanos) / 1_000_000L);
        }
    }

    private static void collectCompleted(Warmup warmup) {
        Iterator<PendingChunk> iterator = warmup.pending.iterator();
        while (iterator.hasNext()) {
            PendingChunk pending = iterator.next();
            if (!pending.future.isDone()) {
                continue;
            }

            iterator.remove();
            if (pending.future.isCompletedExceptionally()) {
                warmup.failed = true;
                ZsgRooms.LOGGER.warn("Could not prepare a Nether destination chunk");
                continue;
            }

            Either<Chunk, ChunkHolder.Unloaded> result = pending.future.getNow(null);
            if (result == null || !result.left().isPresent()) {
                warmup.failed = true;
                ZsgRooms.LOGGER.warn("A Nether destination chunk became unavailable during preparation");
                continue;
            }
            warmup.completed++;
        }
    }

    private static void requestNextChunk(Warmup warmup) {
        ChunkPos chunk = warmup.chunks.get(warmup.nextIndex++);
        warmup.world.getChunkManager().addTicket(
                PRELOAD_TICKET, chunk, FULL_NON_TICKING_LEVEL, chunk);
        CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future =
                ((ServerChunkManagerInvoker) warmup.world.getChunkManager())
                        .zsgRooms$getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true);
        warmup.pending.add(new PendingChunk(future));
    }

    private static final class Warmup {
        private final ServerWorld world;
        private final ChunkPos center;
        private final List<ChunkPos> chunks;
        private final List<PendingChunk> pending = new ArrayList<PendingChunk>(MAX_IN_FLIGHT);
        private final long startedNanos = System.nanoTime();
        private int nextIndex;
        private int completed;
        private boolean failed;
        private boolean loggedComplete;

        private Warmup(ServerWorld world, ChunkPos center, List<ChunkPos> chunks) {
            this.world = world;
            this.center = center;
            this.chunks = chunks;
        }
    }

    private static final class PendingChunk {
        private final CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future;

        private PendingChunk(CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> future) {
            this.future = future;
        }
    }
}
