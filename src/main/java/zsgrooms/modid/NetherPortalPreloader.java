package zsgrooms.modid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import zsgrooms.modid.ui.RoomUiPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NetherPortalPreloader {
    // In 1.16.1, ticket level 34 targets FEATURES while level 33 targets FULL.
    static final int TERRAIN_TICKET_LEVEL = 34;
    static final int FULL_TICKET_LEVEL = 33;
    static final int MIN_FULL_UPGRADE_PORTAL_TIME = 20;
    static final int MIN_REMAINING_PORTAL_TIME = 20;
    static final float MAX_HEALTHY_TICK_TIME_MS = 35.0F;
    private static final int TICKET_EXPIRY_TICKS = 120;
    private static final int MAX_DESTINATION_COORDINATE = 29999872;
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
            if (warmup != null) {
                releaseTickets(warmup);
            }
            warmup = new Warmup(nether, center);
            WARMUPS.put(playerId, warmup);
            SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Started for {} at portal time {}/{}",
                    player.getEntityName(), portalTime, player.getMaxNetherPortalTime());
        }
        advanceWarmup(playerId, player, warmup, portalTime);
    }

    public static void beforeVanillaTransfer(ServerPlayerEntity player, int portalTime) {
        Warmup warmup = player == null ? null : WARMUPS.get(player.getUuid());
        if (warmup == null) {
            return;
        }
        SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Vanilla transfer starting for {} at portal time {}; "
                        + "terrainReady={}, fullRequested={}, fullReady={}",
                player.getEntityName(), portalTime, warmup.terrainReady,
                warmup.fullTicketAdded, warmup.fullReady);
    }

    public static void afterVanillaTransfer(ServerPlayerEntity player) {
        Warmup warmup = player == null ? null : WARMUPS.get(player.getUuid());
        if (warmup == null) {
            return;
        }
        boolean enteredNether = player.world != null && player.world.getRegistryKey() == World.NETHER;
        SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Vanilla transfer returned for {}; enteredNether={}, "
                        + "terrainReady={}, fullRequested={}, fullReady={}",
                player.getEntityName(), enteredNether, warmup.terrainReady,
                warmup.fullTicketAdded, warmup.fullReady);
        if (enteredNether) {
            releaseTickets(warmup);
            WARMUPS.remove(player.getUuid());
        }
    }

    static ChunkPos projectedNetherChunk(double overworldX, double overworldZ) {
        double netherX = MathHelper.clamp(overworldX / 8.0D,
                -MAX_DESTINATION_COORDINATE, MAX_DESTINATION_COORDINATE);
        double netherZ = MathHelper.clamp(overworldZ / 8.0D,
                -MAX_DESTINATION_COORDINATE, MAX_DESTINATION_COORDINATE);
        return new ChunkPos(MathHelper.floor(netherX) >> 4, MathHelper.floor(netherZ) >> 4);
    }

    private static boolean isActiveOverworldRoomRun(ServerPlayerEntity player) {
        if (player.world == null || player.world.getRegistryKey() != World.OVERWORLD) {
            return false;
        }
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        return game != null && game.getIsInGame() && game.hasNetherEntryWarmup()
                && RoomUiPreferences.isNetherEntryWarmupEnabled();
    }

    static boolean shouldUpgradeToFull(int portalTime, int maxPortalTime, float tickTimeMs) {
        return portalTime >= MIN_FULL_UPGRADE_PORTAL_TIME
                && maxPortalTime - portalTime >= MIN_REMAINING_PORTAL_TIME
                && tickTimeMs <= MAX_HEALTHY_TICK_TIME_MS;
    }

    private static void advanceWarmup(UUID playerId, ServerPlayerEntity player, Warmup warmup, int portalTime) {
        if (warmup.fullReady) {
            return;
        }

        try {
            if (!warmup.terrainTicketAdded) {
                warmup.world.getChunkManager().addTicket(
                        PRELOAD_TICKET, warmup.center, TERRAIN_TICKET_LEVEL, warmup.center);
                warmup.terrainTicketAdded = true;
                SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Requested staged terrain preparation");
            }

            collectStatus(warmup);
            MinecraftServer server = player.getServer();
            if (warmup.terrainReady && !warmup.fullTicketAdded && server != null
                    && shouldUpgradeToFull(portalTime, player.getMaxNetherPortalTime(), server.getTickTime())) {
                warmup.world.getChunkManager().addTicket(
                        PRELOAD_TICKET, warmup.center, FULL_TICKET_LEVEL, warmup.center);
                warmup.fullTicketAdded = true;
                SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Promoted destination chunk to full preparation");
            }

            collectStatus(warmup);
        } catch (RuntimeException error) {
            Warmup removed = WARMUPS.remove(playerId);
            if (removed != null) {
                releaseTickets(removed);
            }
            ZsgRooms.LOGGER.warn("Could not prepare Nether destination chunks: {}",
                    error.getClass().getSimpleName());
            return;
        }

        if (warmup.fullReady && !warmup.loggedComplete) {
            warmup.loggedComplete = true;
            SeedDebugLog.info("Prepared the Nether destination chunk during portal charge in {} ms",
                    (System.nanoTime() - warmup.startedNanos) / 1_000_000L);
        }
    }

    private static void collectStatus(Warmup warmup) {
        // This overload polls completed chunk futures with getNow; it does not wait for generation.
        BlockView loaded = warmup.world.getChunkManager().getChunk(warmup.center.x, warmup.center.z);
        if (!warmup.terrainReady && loaded instanceof Chunk
                && ((Chunk) loaded).getStatus().isAtLeast(ChunkStatus.FEATURES)) {
            warmup.terrainReady = true;
            SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Staged terrain preparation completed");
        }
        warmup.fullReady = loaded instanceof WorldChunk;
    }

    private static void logAndClear(UUID playerId, String reason) {
        Warmup warmup = WARMUPS.remove(playerId);
        if (warmup != null) {
            releaseTickets(warmup);
            if (!warmup.loggedComplete) {
                SeedDebugLog.info("[ZSG-Rooms/NetherWarmup] Cleared: {}; terrainReady={}, "
                                + "fullRequested={}, fullReady={}",
                        reason, warmup.terrainReady, warmup.fullTicketAdded, warmup.fullReady);
            }
        }
    }

    private static void releaseTickets(Warmup warmup) {
        if (warmup.fullTicketAdded) {
            warmup.world.getChunkManager().removeTicket(
                    PRELOAD_TICKET, warmup.center, FULL_TICKET_LEVEL, warmup.center);
            warmup.fullTicketAdded = false;
        }
        if (warmup.terrainTicketAdded) {
            warmup.world.getChunkManager().removeTicket(
                    PRELOAD_TICKET, warmup.center, TERRAIN_TICKET_LEVEL, warmup.center);
            warmup.terrainTicketAdded = false;
        }
    }

    private static final class Warmup {
        private final ServerWorld world;
        private final ChunkPos center;
        private final long startedNanos = System.nanoTime();
        private boolean terrainTicketAdded;
        private boolean terrainReady;
        private boolean fullTicketAdded;
        private boolean fullReady;
        private boolean loggedComplete;

        private Warmup(ServerWorld world, ChunkPos center) {
            this.world = world;
            this.center = center;
        }
    }
}
