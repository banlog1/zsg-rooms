package zsgrooms.modid.filter;

import com.mojang.serialization.Lifecycle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import zsgrooms.modid.filter.mixin.ProbeServerAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One candidate at a time, with a fresh seed, world properties, chunk managers and save session. */
final class ProbeWorlds implements AutoCloseable {
    private final ProbeServerAccessor access;
    private final SaveProperties previousProperties;
    private final Map<RegistryKey<World>, ServerWorld> previousWorlds;
    private final LevelStorage.Session session;
    private final Path directory;
    private final Path root;
    private boolean closed;
    private boolean installed;
    private final long seed;

    ProbeWorlds(MinecraftServer server, long seed, String id) throws Exception {
        if (!server.isOnThread()) throw new IllegalStateException("Offline world changes require the server thread");
        access = (ProbeServerAccessor) server;
        this.seed = seed;
        previousProperties = server.getSaveProperties();
        previousWorlds = new LinkedHashMap<RegistryKey<World>, ServerWorld>(access.zsgRooms$worlds());
        root = java.nio.file.Paths.get("bank-worlds").toAbsolutePath().normalize();
        Files.createDirectories(root);
        directory = root.resolve(java.util.UUID.fromString(id).toString());
        if (Files.exists(directory)) throw new IOException("Candidate directory already exists");
        session = new LevelStorage(root, root.resolve("backups"), server.getDataFixer()).createSession(id);
        try {
            GenerationEvidence.clear();
            RavineEvidence.clear(seed);
            GeneratorOptions options = new GeneratorOptions(seed, true, false,
                    GeneratorOptions.method_28608(DimensionType.method_28517(seed), GeneratorOptions.createOverworldGenerator(seed)));
            LevelInfo info = new LevelInfo("Offline filter candidate", GameMode.SURVIVAL, false, Difficulty.NORMAL,
                    false, new GameRules(), previousProperties.method_29589());
            LevelProperties properties = new LevelProperties(info, options, Lifecycle.stable());
            access.zsgRooms$saveProperties(properties);
            access.zsgRooms$worlds().clear();
            installed = true;
            WorldGenerationProgressListener progress = new WorldGenerationProgressListener() {
                public void start(ChunkPos spawnPos) { }
                public void setChunkStatus(ChunkPos pos, ChunkStatus status) { }
                public void stop() { }
            };
            for (RegistryKey<World> key : java.util.Arrays.asList(World.OVERWORLD, World.NETHER)) {
                boolean overworld = key == World.OVERWORLD;
                DimensionOptions dimension = options.getDimensionMap().get(overworld ? DimensionOptions.OVERWORLD : DimensionOptions.NETHER);
                ServerWorld world = new ServerWorld(server, access.zsgRooms$workerExecutor(), session, properties, key,
                        overworld ? DimensionType.OVERWORLD_REGISTRY_KEY : DimensionType.THE_NETHER_REGISTRY_KEY,
                        dimension.getDimensionType(), progress, dimension.getChunkGenerator(), false,
                        BiomeAccess.hashSeed(seed), Collections.emptyList(), overworld);
                access.zsgRooms$worlds().put(key, world);
                if (world.getSeed() != seed) throw new AssertionError("Offline world seed mismatch");
            }
        } catch (Exception | Error failure) {
            try { close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    static void determineSpawn(MinecraftServer server) {
        LevelProperties properties = (LevelProperties) server.getSaveProperties();
        if (properties.isInitialized()) return;
        ProbeServerAccessor.zsgRooms$setupSpawn(server.getOverworld(), properties, false, false, true);
        properties.setInitialized(true);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException error = null;
        try {
            if (installed) for (ServerWorld world : access.zsgRooms$worlds().values()) {
                try { world.close(); } catch (IOException failure) {
                    if (error == null) error = failure; else error.addSuppressed(failure);
                } finally {
                    GenerationEvidence.discard(world);
                }
            }
        } finally {
            access.zsgRooms$worlds().clear();
            access.zsgRooms$worlds().putAll(previousWorlds);
            access.zsgRooms$saveProperties(previousProperties);
            session.close();
            RavineEvidence.clear(seed);
        }
        if (error != null) throw error;
        // Only this freshly created UUID directory is disposable. Do not follow filesystem links.
        if (!directory.normalize().startsWith(root) || Files.isSymbolicLink(directory)
                || !directory.toRealPath().getParent().equals(root.toRealPath())) {
            throw new IOException("Refusing cleanup outside the owned candidate directory");
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
