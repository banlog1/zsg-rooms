package zsgrooms.modid;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.level.LevelInfo;
import zsgrooms.modid.net.RoomSnapshot;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.UUID;

/** Tests the real, mixin-transformed natural spawn check; excluded from the release JAR. */
public final class TempleSpawnSmoke {
    private boolean launched;
    private boolean checking;
    private boolean finished;
    private volatile boolean checked;
    private volatile Throwable failure;
    private final long started = System.nanoTime();

    public void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished || client.getOverlay() != null) return;
        try {
            if (System.nanoTime() - started > 240_000_000_000L) throw new IllegalStateException("Timed out");
            if (failure != null) throw new IllegalStateException("Server test failed", failure);
            if (!launched) {
                launched = true;
                client.options.pauseOnLostFocus = false;
                client.options.maxFps = 60;
                client.options.viewDistance = 3;
                Room room = new Room("temple-spawn-smoke", "12345|structure:manual",
                        new Player(client.getSession().getUsername(), true, true), 2);
                InGame game = new InGame(room.seed, room.roomName, InGame.SeedType.FIXED, false);
                game.setPreventTempleHostileSpawns(true);
                ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                Properties properties = new Properties();
                properties.setProperty("level-seed", "12345");
                properties.setProperty("generate-structures", "true");
                client.method_29607("temple-spawn-smoke-" + UUID.randomUUID(),
                        new LevelInfo("Temple spawning smoke", GameMode.CREATIVE, false, Difficulty.NORMAL,
                                true, new GameRules(), DataPackSettings.SAFE_MODE),
                        RegistryTracker.create(), GeneratorOptions.fromProperties(properties));
            } else if (client.player != null && !checking) {
                checking = true;
                client.getServer().execute(() -> {
                    try { check(client.getServer().getOverworld()); }
                    catch (Throwable error) { failure = error; }
                    finally { checked = true; }
                });
            } else if (checked) {
                finished = true;
                client.world.disconnect();
                client.disconnect();
                ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: temple room rule, actual natural spawn rejection, chamber bounds, outside/passive/Nether bypass, disable and lifecycle reset");
                client.scheduleStop();
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[TempleSpawnSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private static void check(ServerWorld world) throws Exception {
        BlockPos located = world.locateStructure(StructureFeature.DESERT_PYRAMID, world.getSpawnPos(), 160, false);
        require(located != null, "Temple fixture not found");
        world.getChunk(located);
        StructureStart<?> start = world.getStructureAccessor().getStructureStart(
                net.minecraft.util.math.ChunkSectionPos.from(located), StructureFeature.DESERT_PYRAMID, world.getChunk(located));
        require(start.hasChildren(), "Temple start missing");
        BlockBox initial = start.getChildren().get(0).getBoundingBox();
        for (int x = initial.minX >> 4; x <= initial.maxX >> 4; x++) {
            for (int z = initial.minZ >> 4; z <= initial.maxZ >> 4; z++) world.getChunk(x, z);
        }
        BlockBox box = start.getChildren().get(0).getBoundingBox();
        BlockPos chamber = new BlockPos(box.minX + 9, box.minY - 11, box.minZ + 9);
        require(TempleSpawnControl.shouldReject(world, SpawnGroup.MONSTER, chamber), "Room rule did not protect chamber");
        require(TempleSpawnControl.shouldReject(world, SpawnGroup.MONSTER, new BlockPos(box.minX + 10, box.minY + 2, box.minZ + 10)), "Main temple not protected");
        require(!TempleSpawnControl.shouldReject(world, SpawnGroup.CREATURE, chamber), "Passive spawn affected");
        require(!TempleSpawnControl.shouldReject(world.getServer().getWorld(net.minecraft.world.World.NETHER), SpawnGroup.MONSTER, chamber), "Nether affected");
        require(!TempleSpawnControl.shouldReject(world, SpawnGroup.MONSTER, new BlockPos(box.maxX + 1, box.minY, box.minZ)), "Outside affected");
        Method spawn = SpawnHelper.class.getDeclaredMethod("canSpawn", ServerWorld.class, SpawnGroup.class,
                StructureAccessor.class, ChunkGenerator.class, Biome.SpawnEntry.class, BlockPos.Mutable.class, double.class);
        spawn.setAccessible(true);
        Biome.SpawnEntry zombie = world.getBiome(chamber).getEntitySpawnList(SpawnGroup.MONSTER).stream()
                .filter(entry -> entry.type == EntityType.ZOMBIE).findFirst()
                .orElseThrow(() -> new IllegalStateException("Fixture biome has no zombie spawn entry"));
        BlockPos.Mutable candidate = new BlockPos.Mutable().set(chamber);
        TempleSpawnControl.configure(world.getServer(), false);
        boolean vanillaAllowed = false;
        for (int i = 0; i < 100 && !vanillaAllowed; i++) vanillaAllowed = allows(spawn, world, zombie, candidate);
        require(vanillaAllowed, "Fixture never passed vanilla natural spawning");
        TempleSpawnControl.configure(world.getServer(), true);
        for (int i = 0; i < 100; i++) require(!allows(spawn, world, zombie, candidate), "Natural spawn bypassed mixin");
        net.minecraft.entity.mob.ZombieEntity summoned = EntityType.ZOMBIE.create(world);
        summoned.refreshPositionAndAngles(chamber.getX() + 0.5, chamber.getY(), chamber.getZ() + 0.5, 0, 0);
        require(world.spawnEntity(summoned), "Explicit entity spawn was blocked");
        summoned.remove();
        TempleSpawnControl.stop(world.getServer());
        require(!TempleSpawnControl.shouldReject(world, SpawnGroup.MONSTER, chamber), "Stopped server leaked rule");
    }

    private static boolean allows(Method spawn, ServerWorld world, Biome.SpawnEntry entry, BlockPos.Mutable pos) throws Exception {
        return (Boolean) spawn.invoke(null, world, SpawnGroup.MONSTER, world.getStructureAccessor(),
                world.getChunkManager().getChunkGenerator(), entry, pos, 1600.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
