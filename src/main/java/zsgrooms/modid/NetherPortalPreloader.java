package zsgrooms.modid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public static void tick(ServerPlayerEntity player, boolean inNetherPortal, int portalTime) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (!inNetherPortal) {
            logAndClear(playerId, "portal contact ended");
            return;
        }
        if (!isActiveOverworldRoomRun(player)) {
            logAndClear(playerId, "player left the active Overworld race");
            return;
        }

        MinecraftServer server = player.getServer();
        ServerWorld nether = server == null ? null : server.getWorld(World.NETHER);
        if (nether == null) {
            logAndClear(playerId, "Nether world was unavailable");
            return;
        }

        ChunkPos center = projectedNetherChunk(player.getX(), player.getZ());
        Warmup warmup = WARMUPS.get(playerId);
        if (warmup == null || warmup.world != nether || !warmup.center.equals(center)) {
            warmup = new Warmup(nether, center, chunkOrder(center));
            WARMUPS.put(playerId, warmup);
            SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Started for {} at portal time {}/{}",
                    player.getEntityName(), portalTime, player.getMaxNetherPortalTime());
        }
        advanceWarmup(playerId, warmup);
    }

    public static void beforeVanillaTransfer(ServerPlayerEntity player, int portalTime) {
        Warmup warmup = player == null ? null : WARMUPS.get(player.getUuid());
        if (warmup == null) {
            return;
        }
        SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Vanilla transfer starting for {} at portal time {}; "
                        + "requested={}, prepared={}, active={}",
                player.getEntityName(), portalTime, warmup.nextIndex,
                warmup.completed, warmup.pending.size());
    }

    public static void afterVanillaTransfer(ServerPlayerEntity player) {
        Warmup warmup = player == null ? null : WARMUPS.get(player.getUuid());
        if (warmup == null) {
            return;
        }
        boolean enteredNether = player.world != null && player.world.getRegistryKey() == World.NETHER;
        SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Vanilla transfer returned for {}; enteredNether={}, "
                        + "requested={}, prepared={}, active={}",
                player.getEntityName(), enteredNether, warmup.nextIndex,
                warmup.completed, warmup.pending.size());
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
        if (warmup.loggedComplete) {
            return;
        }

        collectCompleted(warmup);

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
            BlockView loaded = warmup.world.getChunkManager()
                    .getChunk(pending.chunk.x, pending.chunk.z);
            if (loaded instanceof WorldChunk) {
                iterator.remove();
                warmup.completed++;
                SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Prepared chunk {}/{}; active={}",
                        warmup.completed, warmup.chunks.size(), warmup.pending.size());
            }
        }
    }

    private static void requestNextChunk(Warmup warmup) {
        ChunkPos chunk = warmup.chunks.get(warmup.nextIndex++);
        warmup.world.getChunkManager().addTicket(
                PRELOAD_TICKET, chunk, FULL_NON_TICKING_LEVEL, chunk);
        warmup.pending.add(new PendingChunk(chunk));
        SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Requested chunk {}/{}; active={}",
                warmup.nextIndex, warmup.chunks.size(), warmup.pending.size());
    }

    private static void logAndClear(UUID playerId, String reason) {
        Warmup warmup = WARMUPS.remove(playerId);
        if (warmup != null && !warmup.loggedComplete) {
            SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Cleared: {}; requested={}, prepared={}, active={}",
                    reason, warmup.nextIndex, warmup.completed, warmup.pending.size());
        }
    }

    private static final class Warmup {
        private final ServerWorld world;
        private final ChunkPos center;
        private final List<ChunkPos> chunks;
        private final List<PendingChunk> pending = new ArrayList<PendingChunk>(MAX_IN_FLIGHT);
        private final long startedNanos = System.nanoTime();
        private int nextIndex;
        private int completed;
        private boolean loggedComplete;

        private Warmup(ServerWorld world, ChunkPos center, List<ChunkPos> chunks) {
            this.world = world;
            this.center = center;
            this.chunks = chunks;
        }
    }

    private static final class PendingChunk {
        private final ChunkPos chunk;

        private PendingChunk(ChunkPos chunk) {
            this.chunk = chunk;
        }
    }
}
