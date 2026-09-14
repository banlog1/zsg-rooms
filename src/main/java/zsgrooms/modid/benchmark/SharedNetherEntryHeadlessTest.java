package zsgrooms.modid.benchmark;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import zsgrooms.modid.SharedNetherEntry;
import zsgrooms.modid.SharedNetherEntryState;
import zsgrooms.modid.ZsgRooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in test of real portal lookup, creation and ServerPlayerEntity dimension transfer. */
public final class SharedNetherEntryHeadlessTest {
    private static final BlockPos SPAWN = new BlockPos(32000, 64, 32000);
    private static final BlockPos REFERENCE = new BlockPos(4000, 64, 4000);

    private SharedNetherEntryHeadlessTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("zsgrooms.netherEntryTest");
    }

    public static void register() {
        if (isEnabled()) {
            ServerLifecycleEvents.SERVER_STARTED.register(SharedNetherEntryHeadlessTest::run);
        }
    }

    private static void run(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        ServerWorld nether = server.getWorld(World.NETHER);
        BlockPos originalSpawn = overworld.getSpawnPos();
        SharedNetherEntryState state = SharedNetherEntryState.get(overworld);
        CompoundTag savedState = state.toTag(new CompoundTag());
        List<ServerPlayerEntity> players = new ArrayList<ServerPlayerEntity>();
        Map<BlockPos, BlockState> sourceBlocks = new HashMap<BlockPos, BlockState>();
        TerrainFixture terrain = null;
        try {
            overworld.setSpawnPos(SPAWN);
            state.fromTag(new CompoundTag());
            SharedNetherEntry.configure(server, true);
            terrain = new TerrainFixture(nether);
            Map<BlockPos, BlockState> expected = null;
            UUID runner = UUID.randomUUID();
            BlockPos[] entries = {SPAWN, SPAWN.add(27, 35, -19), SPAWN.add(-33, -20, 45)};
            for (int trial = 0; trial < entries.length; trial++) {
                // A new persistent state models a fresh same-seed world reset, including the same UUID.
                state.fromTag(new CompoundTag());
                SharedNetherEntry.configure(server, true);
                ServerPlayerEntity player = player(server, overworld, runner, players);
                check(new ChunkPos(REFERENCE).equals(SharedNetherEntry.preloadCenter(player)), "Wrong preload target");
                enter(player, nether, entries[trial], trial % 2 == 0 ? Direction.Axis.X : Direction.Axis.Z, sourceBlocks);
                check(player.world == nether, "Dimension transfer failed");
                Map<BlockPos, BlockState> result = terrain.changes();
                check(result.values().stream().anyMatch(block -> block.isOf(Blocks.NETHER_PORTAL)), "No portal generated");
                if (expected == null) {
                    expected = result;
                } else {
                    check(expected.equals(result), "Portal frame/location/axis differed across resets");
                }
                if (trial == 0) {
                    ServerPlayerEntity other = player(server, overworld, UUID.randomUUID(), players);
                    check(SharedNetherEntry.preloadCenter(other) != null, "Another runner lost their first entry");
                    enter(other, nether, SPAWN.add(19, 15, 21), Direction.Axis.Z, sourceBlocks);
                    check(expected.equals(terrain.changes()), "Another runner received a different portal");
                    check(other.getPos().squaredDistanceTo(player.getPos()) < 9,
                            "Another runner did not arrive at the shared portal");
                    other.getServerWorld().removePlayer(other);
                }
                // Returning and a later entry use normal linking, even after reconfiguring the same save.
                player.changeDimension(overworld);
                check(player.world == overworld, "Return trip failed");
                SharedNetherEntry.configure(server, true);
                check(SharedNetherEntry.preloadCenter(player) == null, "First entry applied twice");
                if (trial == 0) {
                    enter(player, nether, SPAWN.add(2400, 10, 0), Direction.Axis.X, sourceBlocks);
                    check(Math.abs(player.getX() - REFERENCE.getX()) > 200, "Later portal was forced to shared entry");
                }
                player.getServerWorld().removePlayer(player);
                terrain.restore();
                ZsgRooms.LOGGER.info("[NetherEntryTest] Matching frame trial {} passed", trial + 1);
            }

            state.fromTag(new CompoundTag());
            SharedNetherEntry.configure(server, true);
            ServerPlayerEntity guest = player(server, overworld, UUID.randomUUID(), players);
            // Direct changeDimension calls without portal contact must not consume/redirect a first entry.
            guest.refreshPositionAndAngles(SPAWN.getX() + 2400, 74, SPAWN.getZ(), 0, 0);
            guest.changeDimension(nether);
            check(Math.abs(guest.getX() - REFERENCE.getX()) > 200, "Non-portal travel was redirected");
            guest.teleport(overworld, SPAWN.getX(), 64, SPAWN.getZ(), 0, 0);
            check(SharedNetherEntry.preloadCenter(guest) != null, "Non-portal travel consumed first entry");
            touchPortal(guest, SPAWN, Direction.Axis.X, sourceBlocks);
            SharedNetherEntry.beginTransfer(guest, nether);
            check(guest.getBlockPos().equals(REFERENCE), "Failed-transfer fixture did not start a shared transfer");
            SharedNetherEntry.finishTransfer(guest, null);
            check(SharedNetherEntry.preloadCenter(guest) != null, "Failed transfer consumed first entry");

            SharedNetherEntry.configure(server, false);
            check(SharedNetherEntry.preloadCenter(guest) == null, "Disabled preloading was redirected");
            enter(guest, nether, SPAWN.add(2400, 10, 0), Direction.Axis.Z, sourceBlocks);
            check(Math.abs(guest.getX() - REFERENCE.getX()) > 200, "Disabled transfer was redirected");
            ZsgRooms.LOGGER.info("[NetherEntryTest] PASS: matching frames across X/Y/Z and axis changes, "
                    + "same-seed resets, preload target, persisted consumption, vanilla return/later entry, "
                    + "non-portal travel and disabled fallback");
        } catch (Throwable error) {
            ZsgRooms.LOGGER.error("[NetherEntryTest] FAIL", error);
        } finally {
            for (ServerPlayerEntity player : players) {
                player.getServerWorld().removePlayer(player);
            }
            if (terrain != null) {
                terrain.restore();
            }
            for (Map.Entry<BlockPos, BlockState> entry : sourceBlocks.entrySet()) {
                overworld.setBlockState(entry.getKey(), entry.getValue(), 18);
            }
            overworld.setSpawnPos(originalSpawn);
            state.fromTag(savedState);
            state.markDirty();
            SharedNetherEntry.stop(server);
            server.stop(false);
        }
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, UUID id,
            List<ServerPlayerEntity> players) {
        ServerPlayerEntity player = new TestPlayer(server, world,
                new GameProfile(id, "PortalTest" + players.size()));
        player.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), player) {
            @Override
            public void sendPacket(Packet<?> packet) {
            }
        };
        player.refreshPositionAndAngles(SPAWN.getX(), SPAWN.getY(), SPAWN.getZ(), 0, 0);
        world.onPlayerConnected(player);
        players.add(player);
        return player;
    }

    private static void enter(ServerPlayerEntity player, ServerWorld destination, BlockPos origin,
            Direction.Axis axis, Map<BlockPos, BlockState> originals) {
        touchPortal(player, origin, axis, originals);
        player.changeDimension(destination);
    }

    private static void touchPortal(ServerPlayerEntity player, BlockPos origin,
            Direction.Axis axis, Map<BlockPos, BlockState> originals) {
        ServerWorld source = player.getServerWorld();
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                BlockPos pos = origin.add(axis == Direction.Axis.X ? x : 0, y,
                        axis == Direction.Axis.Z ? x : 0);
                originals.putIfAbsent(pos, source.getBlockState(pos));
                boolean frame = x == -1 || x == 2 || y == -1 || y == 3;
                source.setBlockState(pos, frame ? Blocks.OBSIDIAN.getDefaultState()
                        : Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis), 18);
            }
        }
        player.refreshPositionAndAngles(origin.getX() + 0.6D, origin.getY(), origin.getZ() + 0.6D, 0, 0);
        player.netherPortalCooldown = 0;
        player.setInNetherPortal(origin);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class TestPlayer extends ServerPlayerEntity {
        private TestPlayer(MinecraftServer server, ServerWorld world, GameProfile profile) {
            super(server, world, profile, new ServerPlayerInteractionManager(world));
            // Non-portal changeDimension calls also expect a valid previous portal direction in 1.16.1.
            this.lastNetherPortalDirectionVector = new Vec3d(0.5D, 0.0D, 0.0D);
            this.lastNetherPortalDirection = Direction.NORTH;
        }
    }

    private static final class TerrainFixture {
        private final ServerWorld world;
        private final BlockState[] original;
        private final int height;

        private TerrainFixture(ServerWorld world) {
            this.world = world;
            height = world.getDimensionHeight();
            original = new BlockState[41 * 41 * height];
            int index = 0;
            for (BlockPos pos : BlockPos.iterate(REFERENCE.add(-20, -REFERENCE.getY(), -20),
                    REFERENCE.add(20, height - 1 - REFERENCE.getY(), 20))) {
                original[index++] = world.getBlockState(pos);
            }
        }

        private Map<BlockPos, BlockState> changes() {
            Map<BlockPos, BlockState> changes = new HashMap<BlockPos, BlockState>();
            int index = 0;
            for (BlockPos pos : BlockPos.iterate(REFERENCE.add(-20, -REFERENCE.getY(), -20),
                    REFERENCE.add(20, height - 1 - REFERENCE.getY(), 20))) {
                BlockState current = world.getBlockState(pos);
                if (current != original[index++]) {
                    changes.put(pos.toImmutable(), current);
                }
            }
            return changes;
        }

        private void restore() {
            int index = 0;
            for (BlockPos pos : BlockPos.iterate(REFERENCE.add(-20, -REFERENCE.getY(), -20),
                    REFERENCE.add(20, height - 1 - REFERENCE.getY(), 20))) {
                BlockState saved = original[index++];
                if (world.getBlockState(pos) != saved) {
                    world.setBlockState(pos, saved, 18);
                }
            }
        }
    }
}
