package zsgrooms.modid;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.level.LevelInfo;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.Properties;
import java.util.UUID;

/** Real startup regression test, never included in the mod JAR. */
public final class SpawnPreparationSmoke {
    private static volatile BlockPos original;
    private static volatile BlockPos preload;
    private static volatile Throwable failure;
    private static volatile int attempt;
    private static BlockPos firstDestination;
    private boolean loading;
    private boolean finished;
    private int ticks;
    private final long started = System.nanoTime();

    public void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    public static void original(MinecraftServer server) {
        if (Boolean.getBoolean("zsgrooms.spawnPreparationSmoke")) original = server.getOverworld().getSpawnPos();
    }

    public static void preload(MinecraftServer server) {
        if (!Boolean.getBoolean("zsgrooms.spawnPreparationSmoke")) return;
        try {
            ServerWorld world = server.getOverworld();
            preload = world.getSpawnPos();
            require(original != null, "Original spawn was not captured");
            long reference = SharedNetherEntryState.get(world).toTag(new CompoundTag()).getLong("Reference");
            require(reference == SharedNetherEntryState.referenceForSpawn(original).asLong(), "Nether reference changed");
            if (attempt < 2) {
                BlockPos temple = world.locateStructure(StructureFeature.DESERT_PYRAMID, original, 160, false);
                require(temple != null, "Test seed has no temple");
                require(StructureSpawnProximity.needsRelocation(original, temple, "zsgtemple"), "Fixture did not need relocation");
                require(!StructureSpawnProximity.needsRelocation(preload, temple, "zsgtemple"), "Destination exceeds 48 blocks");
                require(StructureSpawnProximity.hasPreparedSpawn(), "Spawn was not prepared before preload");
                require(world.getFluidState(preload).isEmpty() && world.getBlockState(preload).isAir(), "Unsafe destination");
                if (attempt == 0) firstDestination = preload;
                else require(firstDestination.equals(preload), "Reset changed destination");
            } else {
                require(original.equals(preload), "Disabled rule moved spawn");
                require(!StructureSpawnProximity.hasPreparedSpawn(), "Disabled rule reused prepared spawn");
            }
            ZsgRooms.LOGGER.info("[SpawnPreparationSmoke] attempt={} original={} preload={}", attempt, original, preload);
        } catch (Throwable error) { failure = error; }
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        if (System.nanoTime() - started > 240_000_000_000L) failure = new IllegalStateException("Timed out");
        if (client.getOverlay() != null || ++ticks < 20) return;
        ticks = 0;
        try {
            if (failure != null) throw new IllegalStateException("Server assertion failed", failure);
            if (!loading) {
                client.options.pauseOnLostFocus = false;
                client.options.maxFps = 60;
                client.options.viewDistance = 3;
                Room room = new Room("spawn-smoke", "12345|structure:manual",
                        new Player(client.getSession().getUsername(), true, true), 2);
                InGame game = new InGame(room.seed, room.roomName, InGame.SeedType.FIXED, false);
                game.setSpawnNearFilterStructure(attempt < 2);
                game.setSharedNetherEntry(true);
                game.setRngStandardized(false);
                ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                StructureSpawnProximity.prepareNextLaunch("12345|structure:zsgtemple");
                original = null;
                preload = null;
                Properties properties = new Properties();
                properties.setProperty("level-seed", "12345");
                properties.setProperty("generate-structures", "true");
                loading = true;
                client.method_29607("spawn-smoke-" + UUID.randomUUID(),
                        new LevelInfo("Spawn smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), DataPackSettings.SAFE_MODE),
                        RegistryTracker.create(), GeneratorOptions.fromProperties(properties));
            } else if (client.world != null && client.player != null) {
                require(preload != null, "Preload hook was not reached");
                if (attempt < 2) require(client.player.getBlockPos().equals(preload), "Player did not join at preload center with RNG off");
                client.world.disconnect();
                client.disconnect();
                loading = false;
                if (++attempt == 3) {
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: early spawn preload, 48-block range, cached reset, disabled rule, original Nether reference");
                    finished = true;
                    client.scheduleStop();
                }
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[SpawnPreparationSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
