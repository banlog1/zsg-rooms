package zsgrooms.modid;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import zsgrooms.modid.mixin.NetherPortalContactAccessor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Server-thread-only first-entry state; never shared with the seed prefetcher or the relay. */
public final class SharedNetherEntry {
    private static MinecraftServer server;
    private static SharedNetherEntryState state;
    private static BlockPos reference;
    private static final Set<UUID> TRANSFERRING = new HashSet<UUID>();

    private SharedNetherEntry() {
    }

    public static void configure(MinecraftServer owner, boolean enabled) {
        server = owner;
        state = null;
        reference = null;
        TRANSFERRING.clear();
        if (enabled && owner.getOverworld() != null && owner.getWorld(World.NETHER) != null) {
            ServerWorld overworld = owner.getOverworld();
            state = SharedNetherEntryState.get(overworld);
            reference = state.initialize(overworld.getSpawnPos());
        }
    }

    public static void stop(MinecraftServer owner) {
        if (owner == server) {
            server = null;
            state = null;
            reference = null;
            TRANSFERRING.clear();
        }
    }

    private static boolean eligible(ServerPlayerEntity player) {
        return state != null && player.getServer() == server
                && player.world == server.getOverworld() && !state.hasEntered(player.getUuid());
    }

    public static ChunkPos preloadCenter(ServerPlayerEntity player) {
        return eligible(player) ? new ChunkPos(reference) : null;
    }

    public static void beginTransfer(ServerPlayerEntity player, ServerWorld destination) {
        TRANSFERRING.remove(player.getUuid());
        if (!eligible(player) || destination != server.getWorld(World.NETHER)
                || !((NetherPortalContactAccessor) player).zsgRooms$isInNetherPortal()) {
            return;
        }
        TRANSFERRING.add(player.getUuid());
        // Called after vanilla's coordinate scaling, before either portal lookup or creation.
        player.refreshPositionAndAngles(reference.getX() + 0.5D, reference.getY(),
                reference.getZ() + 0.5D, player.yaw, player.pitch);
        SeedDebugLog.info("[ZSG-Rooms/NetherEntry] Applying shared first-entry reference");
    }

    public static void finishTransfer(ServerPlayerEntity player, Entity result) {
        if (TRANSFERRING.remove(player.getUuid()) && state != null && player.getServer() == server
                && result != null && player.world == server.getWorld(World.NETHER)) {
            state.complete(player.getUuid());
            SeedDebugLog.info("[ZSG-Rooms/NetherEntry] First entry completed; later transfers use vanilla linking");
        }
    }

    public static int portalOrientation(Random vanilla, int bound, Entity entity, ServerWorld destination) {
        boolean shared = state != null && entity instanceof ServerPlayerEntity
                && entity.getServer() == server && destination == server.getWorld(World.NETHER)
                && TRANSFERRING.contains(entity.getUuid());
        return orientationRoll(vanilla, bound, shared, destination.getSeed());
    }

    static int orientationRoll(Random vanilla, int bound, boolean shared, long seed) {
        int original = vanilla.nextInt(bound);
        if (!shared) {
            return original;
        }
        // Consume the original roll too: later vanilla portals retain their RNG progression.
        return new Random(RngStandardization.eventSeed(seed, "first_nether_portal", "global", 0L))
                .nextInt(bound);
    }
}
