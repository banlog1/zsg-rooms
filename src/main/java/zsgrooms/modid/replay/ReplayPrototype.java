package zsgrooms.modid.replay;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import zsgrooms.modid.ZsgRooms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Opt-in experimental recorder, configured through local replay settings. */
public final class ReplayPrototype {
    private static volatile ReplayPreferences preferences = new ReplayPreferences(false, "", false);
    private static Path gameDirectory;
    private static Path preferencesFile;
    private static final AtomicBoolean CHECKING = new AtomicBoolean();
    private static volatile String libraryStatus = "Libraries not checked";
    private static volatile String recordingStatus = "Waiting for next world";
    private static volatile boolean libraryReady;
    private static Path loadedDirectory;
    private static String loadedSelection;
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static final AtomicReference<Session> CURRENT = new AtomicReference<>();
    private static final java.util.Set<Session> WRITERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static Library library;
    private static volatile zsgrooms.modid.InGame returnedGame;
    private static String returnedRaceId;

    private ReplayPrototype() {
    }

    public static void initialize() {
        gameDirectory = MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize();
        preferencesFile = gameDirectory.resolve("config/zsg-rooms-replay.properties");
        preferences = ReplayPreferences.load(preferencesFile);
        if (!Files.exists(preferencesFile)) {
            // One-time compatibility with the earlier argument-only prototype.
            preferences = new ReplayPreferences(Boolean.getBoolean("zsgrooms.replayPrototype"),
                    System.getProperty("zsgrooms.replayPrototype.libraryDir", ""),
                    Boolean.getBoolean("zsgrooms.replayPrototype.allowReplayMod"));
        }
        ClientTickEvents.END_CLIENT_TICK.register(ReplayPrototype::tick);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(zsgrooms.modid.ui.ReplayHud::render);
        ReplaySmokeTest.initialize();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            for (Session session : WRITERS) {
                session.buffer.close();
                try {
                    session.worker.join(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        if (preferences.enabled) checkLibraries();
    }

    public static ReplayPreferences getPreferences() {
        return preferences;
    }

    public static String getLibraryDirectory() {
        return preferences.resolveLibraries(gameDirectory).toString();
    }

    public static String getLibraryStatus() {
        return libraryStatus;
    }

    public static boolean canConfigure() {
        return CURRENT.get() == null && WRITERS.isEmpty() && !CHECKING.get();
    }

    public static boolean isLibraryReady() {
        return libraryReady;
    }

    public static boolean isRecording() {
        Session session = CURRENT.get();
        return session != null && session.buffer.isOpen() && session.writerReady;
    }

    public static boolean isCapturingChestLoot() {
        Session session = CURRENT.get();
        return session != null && session.buffer.isOpen();
    }

    public static String getHudStatus() {
        Session session = CURRENT.get();
        if (session != null) {
            if (session.failure.get() != null) return "Replay stopped";
            if (!session.buffer.isOpen()) return "Saving replay...";
            if (!session.writerReady) return "Starting replay...";
            long seconds = (System.nanoTime() - session.originNanos) / 1000000000L;
            return "REC " + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        }
        if (!preferences.enabled) return null;
        return "Saved to replay_recordings".equals(recordingStatus) ? "Replay saved" : "Replay: Not recording";
    }

    public static String getRecordingStatus() {
        Session session = CURRENT.get();
        if (session != null) {
            if (session.failure.get() != null) return "Recording stopped: " + session.failure.get();
            if (!session.buffer.isOpen()) return "Saving recording...";
            return session.writerReady ? "Recording" : "Starting recording...";
        }
        if (!preferences.enabled) return "Recording is off";
        if (FabricLoader.getInstance().isModLoaded("replaymod") && !preferences.replayModRecordingDisabled) {
            return "ReplayMod confirmation required";
        }
        if (!libraryReady) return libraryStatus;
        return recordingStatus;
    }

    public static boolean configure(boolean enabled, String directory, boolean replayModRecordingDisabled) {
        if (!canConfigure()) return false;
        ReplayPreferences next = new ReplayPreferences(enabled, directory, replayModRecordingDisabled, preferences.soloTestGroup, preferences.showRecordingHud, preferences.performanceMode, preferences.keepSeedChanges);
        try {
            next.resolveLibraries(gameDirectory);
            next.save(preferencesFile);
        } catch (Exception e) {
            libraryStatus = "Settings not saved: " + e.getClass().getSimpleName();
            return false;
        }
        boolean changed = !next.libraryDirectory.equals(preferences.libraryDirectory);
        preferences = next;
        recordingStatus = "Waiting for next world";
        if (changed) libraryReady = false;
        if (enabled && !libraryReady) checkLibraries();
        return true;
    }

    public static void checkLibraries() {
        if (!canConfigure() || !CHECKING.compareAndSet(false, true)) return;
        libraryReady = false;
        libraryStatus = "Checking libraries...";
        Thread thread = new Thread(() -> {
            try {
                library();
                libraryReady = true;
                libraryStatus = "Libraries ready";
            } catch (Exception | LinkageError e) {
                libraryStatus = libraryFailure(e);
                ZsgRooms.LOGGER.warn("[ReplayPrototype] {}", libraryStatus);
            } finally {
                CHECKING.set(false);
            }
        }, "ZSG replay library check");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean configureTestGroup(String group) {
        if (!canConfigure()) return false;
        try {
            ReplayPreferences next = new ReplayPreferences(preferences.enabled, preferences.libraryDirectory,
                    preferences.replayModRecordingDisabled, group, preferences.showRecordingHud, preferences.performanceMode, preferences.keepSeedChanges);
            next.save(preferencesFile);
            preferences = next;
            return true;
        } catch (java.io.IOException | IllegalArgumentException error) {
            return false;
        }
    }

    public static void armRace(String raceId, boolean solo) {
        returnedGame = null;
        Session session = CURRENT.get();
        if (session == null) return;
        synchronized (session.connections) {
            session.testRaceId = raceId;
            session.prediction.joinedRoom();
            session.manifest.allowTemplePrediction(null);
            session.testGroup = solo ? preferences.soloTestGroup : "";
        }
    }

    public static boolean configureHud(boolean visible) {
        ReplayPreferences next = new ReplayPreferences(preferences.enabled, preferences.libraryDirectory,
                preferences.replayModRecordingDisabled, preferences.soloTestGroup, visible, preferences.performanceMode, preferences.keepSeedChanges);
        try { next.save(preferencesFile); preferences = next; return true; }
        catch (java.io.IOException error) { return false; }
    }

    public static boolean configurePerformance(boolean enabled) {
        if (!canConfigure()) return false;
        ReplayPreferences next = new ReplayPreferences(preferences.enabled, preferences.libraryDirectory,
                preferences.replayModRecordingDisabled, preferences.soloTestGroup, preferences.showRecordingHud, enabled, preferences.keepSeedChanges);
        try { next.save(preferencesFile); preferences = next; return true; }
        catch (java.io.IOException error) { return false; }
    }

    public static boolean configureSeedRetention(boolean keep) {
        ReplayPreferences next = new ReplayPreferences(preferences.enabled, preferences.libraryDirectory,
                preferences.replayModRecordingDisabled, preferences.soloTestGroup, preferences.showRecordingHud,
                preferences.performanceMode, keep);
        try { next.save(preferencesFile); preferences = next; return true; }
        catch (java.io.IOException error) { return false; }
    }

    private static String libraryFailure(Throwable error) {
        Throwable cause = error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
        if (cause instanceof java.nio.file.NoSuchFileException) return "Library folder not found";
        if (cause instanceof java.nio.file.NotDirectoryException) return "Library path is not a folder";
        if (cause instanceof ClassNotFoundException || cause instanceof NoClassDefFoundError) return "Required library JAR missing";
        if (cause instanceof NoSuchMethodException) return "Writer library needs updating";
        return "Library check failed: " + cause.getClass().getSimpleName();
    }

    public static void stopRecording() {
        Session session = CURRENT.get();
        if (session != null) {
            synchronized (session.connections) {
                session.manifest.closeInterval(session.timestamp());
                session.connections.stop();
                session.buffer.cancelPending();
                session.buffer.close();
            }
        }
    }

    public static void matchEnded() {
        Session session = CURRENT.get();
        if (session == null) return;
        synchronized (session.connections) {
            if (!session.buffer.isOpen()) return;
            session.prediction.matchEnded();
            session.manifest.allowTemplePrediction(session.prediction.releasedSeed());
        }
    }

    public static void returnedToRoom() {
        zsgrooms.modid.Room room = ZsgRooms.getActiveRoom();
        zsgrooms.modid.InGame game = room == null ? null : ZsgRooms.getGame(room.roomName);
        returnedRaceId = game == null ? null : game.getRaceId();
        returnedGame = game;
        // Release before closing the buffer: the writer may finalize immediately on close.
        matchEnded();
        stopRecording();
    }

    public static boolean hasReturnedToRoom() {
        zsgrooms.modid.InGame game = returnedGame;
        zsgrooms.modid.Room room = ZsgRooms.getActiveRoom();
        return game != null && room != null && ZsgRooms.getGame(room.roomName) == game
                && java.util.Objects.equals(returnedRaceId, game.getRaceId())
                && MinecraftClient.getInstance().getServer() == null;
    }

    public static void raceStarted(String raceId, UUID player, long startNanos) {
        Session session = CURRENT.get();
        if (session == null || raceId == null) return;
        synchronized (session.connections) {
            if (session.buffer.isOpen() && session.connections.current() != null) {
                session.prediction.joinedRoom();
                session.manifest.allowTemplePrediction(null);
                session.manifest.startRace(raceId, player, startNanos,
                        raceId.equals(session.testRaceId) ? session.testGroup : "", session.worldIndex);
            }
        }
    }

    public static void raceFinished(String raceId, long elapsedNanos, long igtMillis) {
        Session session = CURRENT.get();
        if (session == null) return;
        synchronized (session.connections) {
            if (session.buffer.isOpen()) {
                session.manifest.finishRace(raceId, elapsedNanos, igtMillis);
                session.retention.completed();
            }
        }
    }

    public static void beginReset() {
        Session session = CURRENT.get();
        if (session != null && session.buffer.isOpen()) session.connections.beginReset();
    }

    public static void cancelReset() {
        Session session = CURRENT.get();
        if (session == null) return;
        synchronized (session.connections) {
            if (session.connections.cancelReset()) session.buffer.close();
        }
    }

    public static void clientDisconnect() {
        Session session = CURRENT.get();
        if (session == null) return;
        synchronized (session.connections) {
            session.manifest.closeInterval(session.timestamp());
            boolean finished = session.connections.disconnect();
            if (session.buffer.isOpen()) {
                session.prediction.disconnected(finished);
                Long seed = session.prediction.releasedSeed();
                if (seed != null) session.manifest.allowTemplePrediction(seed);
            }
            session.buffer.cancelPending();
            session.player = null;
            session.worldChanging = true;
            if (!finished) session.manifest.recordLoading(session.timestamp(), true);
            if (finished) session.buffer.close();
        }
    }

    public static void openRecordings() {
        try {
            net.minecraft.util.Util.getOperatingSystem().open(prepareRecordingDirectory(gameDirectory).toFile());
        } catch (Exception e) {
            recordingStatus = "Cannot open recordings: " + e.getClass().getSimpleName();
        }
    }

    public static void connected(ClientConnection connection) {
        if (!preferences.enabled || MinecraftClient.getInstance().getServer() == null) return;
        long seed = MinecraftClient.getInstance().getServer().getOverworld().getSeed();
        Session previous = CURRENT.get();
        if (previous != null) {
            synchronized (previous.connections) {
                if (previous.buffer.isOpen()) {
                    if (!previous.connections.canAttach()) return;
                    ReplaySeedRetention.Action action = previous.retention.onReplacement(seed, preferences.keepSeedChanges);
                    if (action == ReplaySeedRetention.Action.CONTINUE) {
                        previous.connections.attach(connection);
                        previous.worldChanging = true;
                        previous.player = null;
                        previous.equipment = null;
                        previous.swingTicks = 0;
                        return;
                    }
                    previous.manifest.closeInterval(previous.timestamp());
                    previous.connections.stop();
                    previous.discard = action == ReplaySeedRetention.Action.DISCARD;
                    previous.buffer.cancelPending();
                    if (previous.discard) previous.buffer.discardQueued();
                    else previous.buffer.close();
                    ZsgRooms.LOGGER.info("[ReplayPrototype] Seed changed: {} previous recording",
                            previous.discard ? "discarding" : "saving");
                }
            }
            // The old writer owns its cleanup; it must not block the new world's login capture.
            CURRENT.compareAndSet(previous, null);
        }
        if (FabricLoader.getInstance().isModLoaded("replaymod")
                && !preferences.replayModRecordingDisabled) {
            ZsgRooms.LOGGER.warn("[ReplayPrototype] Skipped: ReplayMod installed; disable its recorder before opting in to coexistence");
            return;
        }
        if (!libraryReady || CHECKING.get()) {
            recordingStatus = "Not recorded: finish setup, then re-enter world";
            ZsgRooms.LOGGER.warn("[ReplayPrototype] Skipped world: recorder setup is not ready; re-enter after setup finishes");
            return;
        }
        Session session = new Session(connection, MinecraftClient.getInstance().runDirectory.toPath(), seed);
        if (!CURRENT.compareAndSet(null, session)) {
            ZsgRooms.LOGGER.warn("[ReplayPrototype] Skipped world: previous recording is still finalizing");
            return;
        }
        recordingStatus = "Recording";
        WRITERS.add(session);
        session.worker.start();
    }

    static boolean hasSession() {
        return CURRENT.get() != null;
    }

    static Object recordingIdentity() {
        return CURRENT.get();
    }

    static Path recordingFile() {
        Session session = CURRENT.get();
        return session == null ? null : session.root.resolve("replay_recordings").resolve(session.recordingId + ".mcpr");
    }

    static Path prepareRecordingDirectory(Path gameDirectory) throws java.io.IOException {
        Path directory = gameDirectory.resolve("replay_recordings");
        Files.createDirectories(directory);
        return directory;
    }

    public static void received(ClientConnection connection, Packet<?> packet) {
        Session session = CURRENT.get();
        if (session == null || !session.buffer.isOpen()) return;
        ReplayConnectionState.Attachment<ClientConnection> attachment = session.connections.current();
        if (attachment == null || attachment.connection != connection) return;
        NetworkState phase = NetworkState.getPacketHandlerState(packet);
        if (phase == NetworkState.LOGIN && !(packet instanceof LoginSuccessS2CPacket)) return;
        // Do not capture custom mod traffic, resource downloads, or authentication exchanges.
        if (phase != NetworkState.LOGIN && phase != NetworkState.PLAY
                || packet instanceof CustomPayloadS2CPacket || packet instanceof ResourcePackSendS2CPacket
                || packet instanceof DisconnectS2CPacket) return;
        capture(session, attachment, packet, packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket);
    }

    public static void chestInteraction(net.minecraft.client.world.ClientWorld world, net.minecraft.util.math.BlockPos pos) {
        Session session = CURRENT.get();
        if (session == null || !session.buffer.isOpen()) return;
        session.chests.clear();
        if (session.worldChanging || world != MinecraftClient.getInstance().world) return;
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.block.ChestBlock)) return;
        net.minecraft.util.math.BlockPos other = pos;
        if (state.get(net.minecraft.block.ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE) {
            other = pos.offset(net.minecraft.block.ChestBlock.getFacing(state));
            if (!world.isChunkLoaded(other) || world.getBlockState(other).getBlock() != state.getBlock()) return;
        }
        session.chests.interaction(session.timestamp(), session.worldIndex, world.getRegistryKey().getValue().toString(),
                pos.asLong(), other.asLong(), net.minecraft.block.Block.getRawIdFromState(state),
                net.minecraft.block.Block.getRawIdFromState(world.getBlockState(other)));
    }

    public static void clearChestInteraction() {
        Session session = CURRENT.get();
        if (session != null) session.chests.clear();
    }

    public static void worldApplied(ClientConnection connection) {
        Session session = CURRENT.get();
        ReplayConnectionState.Attachment<ClientConnection> attachment = session == null ? null : session.connections.current();
        if (attachment != null && attachment.connection == connection) {
            session.player = null;
            session.worldChanging = false;
            ZsgRooms.LOGGER.info("[ReplayPrototype] Buffer world-applied recording={} {}",
                    session.recordingId, session.buffer.snapshot());
        }
    }

    public static void disconnected(ClientConnection connection) {
        Session session = CURRENT.get();
        ReplayConnectionState.Attachment<ClientConnection> attachment = session == null ? null : session.connections.current();
        if (attachment != null && attachment.connection == connection) {
            MinecraftClient.getInstance().send(() -> {
                synchronized (session.connections) {
                    if (CURRENT.get() == session && session.connections.accepts(attachment)) clientDisconnect();
                }
            });
        }
    }

    private static void capture(Session session, Packet<?> packet, boolean worldChanging) {
        capture(session, session.connections.current(), packet, worldChanging);
    }

    private static void capture(Session session, ReplayConnectionState.Attachment<ClientConnection> attachment,
                                Packet<?> packet, boolean worldChanging) {
        if (!session.buffer.isOpen()) return;
        PacketByteBuf encoded = ReplayEncodingBuffers.acquire();
        ReplayBuffer.Record record = null;
        try {
            NetworkState phase = NetworkState.getPacketHandlerState(packet);
            Integer id = phase.getPacketId(NetworkSide.CLIENTBOUND, packet);
            if (id == null) throw new IllegalArgumentException("Unmapped clientbound packet");
            packet.write(encoded);
            synchronized (session.connections) {
                if (!session.connections.accepts(attachment)) return;
                record = session.buffer.reserve(phase.getId(), id, encoded.readableBytes());
                if (record == null) {
                    // Keep the rejected packet and its snapshot together across concurrent captures.
                    // An automatic finish may also close the buffer during encoding; that is not a failure.
                    if (session.buffer.isOpen()) session.fail("buffer capacity reached",
                            "packet=" + packet.getClass().getSimpleName() + " " + session.buffer.capacityFailure());
                    return;
                }
            }
            encoded.getBytes(encoded.readerIndex(), record.payload);
            ReplayBuffer.Record submitted = record;
            boolean login = packet instanceof LoginSuccessS2CPacket;
            GameJoinS2CPacket join = packet instanceof GameJoinS2CPacket ? (GameJoinS2CPacket) packet : null;
            Packet<?> connectionState = packet instanceof net.minecraft.network.packet.s2c.play.BossBarS2CPacket
                    || packet instanceof net.minecraft.network.packet.s2c.play.PlayerListS2CPacket ? packet : null;
            Runnable commit = () -> {
                synchronized (session.connections) {
                    if (!session.connections.accepts(attachment) || !session.buffer.isOpen()
                            || login && session.loggedIn) {
                        session.buffer.cancel(submitted);
                        return;
                    }
                    boolean replacingWorld = join != null && session.joined;
                    try {
                        if (replacingWorld) {
                            for (Packet<?> cleanup : session.worldReset.cleanup()) capture(session, attachment, cleanup, false);
                        }
                        long timestamp = session.timestamp();
                        if (!session.buffer.commit(submitted, timestamp)) return;
                        if (packet instanceof ReplayChestLootPacket) {
                            session.manifest.recordChestLoot(timestamp, ++session.chestChunks, session.worldIndex,
                                    ((ReplayChestLootPacket) packet).zsgRooms$getChestLoot());
                        }
                        if (session.trackedState != null && packet instanceof EntityTrackerUpdateS2CPacket) {
                            session.trackedState.observe((EntityTrackerUpdateS2CPacket) packet);
                        }
                        if (worldChanging) {
                            session.chests.clear();
                            session.manifest.closeInterval(timestamp);
                            session.manifest.recordLoading(timestamp, true);
                            session.worldChanging = true;
                        }
                        if (login) session.loggedIn = true;
                        if (packet instanceof net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket) {
                            net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket open =
                                    (net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket) packet;
                            net.minecraft.screen.ScreenHandlerType<?> type = open.getScreenHandlerType();
                            int slots = type == net.minecraft.screen.ScreenHandlerType.GENERIC_9X3 ? 27
                                    : type == net.minecraft.screen.ScreenHandlerType.GENERIC_9X6 ? 54 : 0;
                            session.chests.opened(session.manifest, timestamp, session.worldIndex, open.getSyncId(), slots);
                        }
                        if (join != null) {
                            session.joined = true;
                            session.worldIndex++;
                        }
                        session.worldReset.observe(connectionState);
                        if (replacingWorld) {
                            capture(session, attachment, ReplayWorldReset.refreshPlayer(join), false);
                        }
                    } catch (Exception e) {
                        session.buffer.cancel(submitted);
                        session.fail("world handoff " + e.getClass().getSimpleName());
                    }
                }
            };
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.isOnThread()) commit.run();
            else client.send(commit);
        } catch (Exception e) {
            if (record != null) session.buffer.cancel(record);
            session.fail("packet capture " + e.getClass().getSimpleName());
        } finally {
            ReplayEncodingBuffers.recycle(encoded);
        }
    }

    private static void tick(MinecraftClient client) {
        Session session = CURRENT.get();
        if (session != null && session.buffer.isOpen()) {
            long timestamp = session.timestamp();
            boolean loading = session.worldChanging || client.currentScreen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen
                    || client.currentScreen instanceof net.minecraft.client.gui.screen.LevelLoadingScreen;
            session.manifest.recordLoading(timestamp, loading);
            int state = loading || client.world == null || client.player == null ? 2 : client.isPaused() ? 1 : 0;
            int screen = state == 2 ? 0
                    : client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen
                            || client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen ? 1
                    : client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CraftingScreen ? 2 : 0;
            session.manifest.recordScreen(timestamp, screen);
            session.hudCapture.capture(session.hud, timestamp, session.worldIndex, state == 2 ? null : client.player);
            if (session.manifest.needsTiming(timestamp, session.worldIndex, state)) {
                long[] clocks = state == 2 ? ReplayTimerCapture.UNAVAILABLE : ReplayTimerCapture.read();
                session.manifest.recordTiming(timestamp, session.worldIndex, state, clocks[0], clocks[1]);
            }
        }
        ReplayConnectionState.Attachment<ClientConnection> attachment = session == null ? null : session.connections.current();
        if (session == null || !session.buffer.isOpen() || session.worldChanging || client.isPaused()
                || client.player == null || client.world == null || client.getNetworkHandler() == null
                || attachment == null || client.getNetworkHandler().getConnection() != attachment.connection
                || client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) == null) return;
        ClientPlayerEntity player = client.player;
        try {
            if (session.player != player) {
                session.player = player;
                if (session.selfId == -1) session.selfId = player.getEntityId();
                session.equipment = new ItemStack[SLOTS.length];
                if (session.trackedState != null) session.trackedState.reset();
                capture(session, new PlayerSpawnS2CPacket(player), false);
                if (session.buffer.isOpen()) {
                    session.manifest.openInterval(session.timestamp(), session.worldIndex,
                            player.getEntityId(), client.world.getRegistryKey().getValue().toString());
                }
            }
            capture(session, new EntityPositionS2CPacket(player), false);
            capture(session, new EntitySetHeadYawS2CPacket(player, (byte) (player.headYaw * 256.0F / 360.0F)), false);
            capture(session, new EntityVelocityUpdateS2CPacket(player), false);
            EntityTrackerUpdateS2CPacket tracked = copyTrackedState(player.getEntityId(), player.getDataTracker(), session.trackedState);
            if (tracked != null) capture(session, tracked, false);
            List<Pair<EquipmentSlot, ItemStack>> changed = session.trackedState == null ? new ArrayList<>() : null;
            for (int i = 0; i < SLOTS.length; i++) {
                ItemStack stack = player.getEquippedStack(SLOTS[i]);
                if (session.equipment[i] == null || !ItemStack.areEqual(stack, session.equipment[i])) {
                    session.equipment[i] = stack.copy();
                    if (changed == null) changed = new ArrayList<>();
                    changed.add(Pair.of(SLOTS[i], session.equipment[i]));
                }
            }
            if (changed != null && !changed.isEmpty()) capture(session, new EntityEquipmentUpdateS2CPacket(player.getEntityId(), changed), false);
            if (player.handSwinging && (session.swingTicks <= 0 || player.handSwingTicks < session.swingTicks)) {
                capture(session, new EntityAnimationS2CPacket(player,
                        player.preferredHand == net.minecraft.util.Hand.OFF_HAND ? 3 : 0), false);
            }
            session.swingTicks = player.handSwingTicks;
        } catch (Exception e) {
            session.fail("player capture " + e.getClass().getSimpleName());
        }
    }

    public static EntityTrackerUpdateS2CPacket copyTrackedState(int id, DataTracker tracker) throws java.io.IOException {
        return copyTrackedState(id, tracker, null);
    }

    static EntityTrackerUpdateS2CPacket copyTrackedState(int id, DataTracker tracker, ReplayTrackedState cache) throws java.io.IOException {
        PacketByteBuf snapshot = cache == null ? new PacketByteBuf(Unpooled.buffer()) : ReplayEncodingBuffers.acquireMetadata();
        try {
            // The normal full-update constructor clears dirty flags on the live tracker.
            snapshot.writeVarInt(id);
            DataTracker.entriesToPacket(tracker.getAllEntries(), snapshot);
            if (cache != null && cache.unchanged(snapshot)) return null;
            EntityTrackerUpdateS2CPacket packet = new EntityTrackerUpdateS2CPacket();
            packet.read(snapshot);
            if (cache != null) {
                snapshot.readerIndex(0);
                cache.remember(snapshot, packet);
            }
            return packet;
        } finally {
            if (cache == null) snapshot.release(); else ReplayEncodingBuffers.recycleMetadata(snapshot);
        }
    }

    private static synchronized Library library() throws Exception {
        Path directory = preferences.resolveLibraries(gameDirectory);
        if (library == null || !directory.equals(loadedDirectory) || !preferences.libraryDirectory.equals(loadedSelection)) {
            if (library != null) {
                library.loader.close();
                library = null;
            }
            List<Path> paths;
            if (preferences.libraryDirectory.isEmpty()) {
                paths = ReplayLibraryInstaller.prepare(directory, status -> libraryStatus = status);
            } else {
                try (Stream<Path> files = Files.list(directory)) {
                    paths = files.filter(path -> path.toString().endsWith(".jar")).sorted().collect(Collectors.toList());
                }
            }
            libraryStatus = "Loading replay libraries...";
            library = new Library(paths);
            loadedDirectory = directory;
            loadedSelection = preferences.libraryDirectory;
        }
        return library;
    }

    private static final class Library {
        private final URLClassLoader loader;
        private final Constructor<?> constructor;
        private final Method write;
        private final Method finish;
        private final Method writeManifest;
        private final Method writeHud;
        private final Method close;
        private final Method discard;

        private Library(List<Path> paths) throws Exception {
            List<URL> urls = new ArrayList<>();
            for (Path path : paths) urls.add(path.toUri().toURL());
            loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent());
            try {
                Class<?> writer = loader.loadClass("zsgrooms.replayprobe.ReplayStudioWriter");
                constructor = writer.getConstructor(Path.class, long.class);
                write = writer.getMethod("write", int.class, int.class, long.class, byte[].class);
                finish = writer.getMethod("finish", int.class, boolean.class);
                writeManifest = writer.getMethod("writeRaceManifest", String.class);
                Method optionalHud;
                try { optionalHud = writer.getMethod("writePlayerHud", byte[].class); }
                catch (NoSuchMethodException ignored) { optionalHud = null; }
                writeHud = optionalHud;
                close = writer.getMethod("close");
                discard = writer.getMethod("discard");
                writer.getMethod("verifyDependencies").invoke(null);
            } catch (Exception | LinkageError e) {
                loader.close();
                throw e;
            }
        }
    }

    private static final class Session {
        private final ReplayConnectionState<ClientConnection> connections;
        private final ReplaySeedRetention retention;
        private final ReplayPredictionPolicy prediction;
        private volatile boolean discard;
        private final ReplayWorldReset worldReset = new ReplayWorldReset();
        private boolean loggedIn;
        private boolean joined;
        private final ReplayBuffer buffer = new ReplayBuffer();
        private final long originNanos = System.nanoTime();
        private final long epochMillis = System.currentTimeMillis();
        private final UUID recordingId = UUID.randomUUID();
        private final ReplayRaceManifest manifest;
        private final ReplayChestCapture chests = new ReplayChestCapture();
        private final ReplayHudTrack hud = new ReplayHudTrack();
        private final boolean performance = preferences.performanceMode;
        private final ReplayHudCapture hudCapture = new ReplayHudCapture(performance);
        private int worldIndex = -1;
        private int chestChunks;
        private String testRaceId = "";
        private String testGroup = "";
        private final Path root;
        private final Thread worker;
        private final AtomicReference<String> failure = new AtomicReference<>();
        private volatile int selfId = -1;
        private volatile boolean writerReady;
        private boolean worldChanging = true;
        private ClientPlayerEntity player;
        private final ReplayTrackedState trackedState = performance ? new ReplayTrackedState() : null;
        private ItemStack[] equipment;
        private int swingTicks;

        private Session(ClientConnection connection, Path root, long seed) {
            this.connections = new ReplayConnectionState<>(connection);
            this.retention = new ReplaySeedRetention(seed);
            this.prediction = new ReplayPredictionPolicy(seed, ZsgRooms.getActiveRoom() != null);
            com.mojang.authlib.GameProfile profile = MinecraftClient.getInstance().getSession().getProfile();
            manifest = new ReplayRaceManifest(recordingId, profile.getId(), profile.getName(), originNanos);
            manifest.recordStewOrder(ReplayStewOrder.vanilla());
            manifest.recordLoading(0, true);
            this.root = root;
            worker = new Thread(this::writeReplay, "ZSG replay prototype writer");
            worker.setDaemon(true);
        }

        private long timestamp() {
            return (System.nanoTime() - originNanos) / 1000000L;
        }

        private void fail(String reason) {
            fail(reason, "");
        }

        private void fail(String reason, String diagnostics) {
            if (CURRENT.get() == this) recordingStatus = "Recording stopped: " + reason;
            if (failure.compareAndSet(null, reason)) {
                ZsgRooms.LOGGER.warn("[ReplayPrototype] Recording stopped: {} recording={} {}",
                        reason, recordingId, diagnostics);
            }
            buffer.close();
        }

        private void writeReplay() {
            Library access = null;
            Object writer = null;
            long packets = 0L;
            long bytes = 0L;
            long durationMillis = 0L;
            String stage = "loading replay libraries";
            try {
                access = library();
                libraryReady = true;
                libraryStatus = "Libraries ready";
                stage = "creating replay_recordings folder";
                Path directory = prepareRecordingDirectory(root);
                stage = "opening replay file";
                writer = access.constructor.newInstance(directory.resolve(recordingId + ".mcpr"), epochMillis);
                writerReady = true;
                stage = "writing replay packets";
                ReplayBuffer.Record record;
                while ((record = buffer.take()) != null) {
                    try {
                        access.write.invoke(writer, record.phase, record.packetId, record.timestamp, record.payload);
                        packets++;
                        bytes += record.payload.length;
                        durationMillis = record.timestamp;
                    } finally {
                        buffer.complete(record);
                    }
                }
                if (discard) {
                    stage = "discarding replay file";
                    access.discard.invoke(writer);
                    writer = null;
                    ZsgRooms.LOGGER.info("[ReplayPrototype] Discarded recording after seed change");
                    return;
                }
                stage = "finalizing replay file";
                access.writeManifest.invoke(writer, manifest.finish(durationMillis, failure.get() == null));
                if (access.writeHud != null) access.writeHud.invoke(writer, (Object) hud.finish(durationMillis));
                access.finish.invoke(writer, selfId, failure.get() == null);
                if (failure.get() == null && CURRENT.get() == this) recordingStatus = "Saved to replay_recordings";
                ZsgRooms.LOGGER.info("[ReplayPrototype] Finalized {} packets, {} bytes; complete={}",
                        packets, bytes, failure.get() == null);
            } catch (Exception | LinkageError e) {
                Throwable cause = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null
                        ? e.getCause() : e;
                fail(stage + ": " + cause.getClass().getSimpleName());
                if (access == null) {
                    libraryReady = false;
                    libraryStatus = libraryFailure(cause);
                    ZsgRooms.LOGGER.warn("[ReplayPrototype] Retry setup in Room Settings > Replays > Setup.");
                }
            } finally {
                ZsgRooms.LOGGER.info("[ReplayPrototype] Buffer final recording={} {}", recordingId, buffer.snapshot());
                buffer.discardQueued();
                if (access != null && writer != null) {
                    try {
                        access.close.invoke(writer);
                    } catch (Exception ignored) {
                        ZsgRooms.LOGGER.warn("[ReplayPrototype] Could not close recording; temporary data may remain");
                    }
                }
                CURRENT.compareAndSet(this, null);
                WRITERS.remove(this);
            }
        }
    }
}
