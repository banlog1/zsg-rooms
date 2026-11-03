package zsgrooms.modid.replay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ui.ReplaySettingsScreen;
import zsgrooms.modid.ui.RoomSettingsScreen;
import zsgrooms.modid.ui.ZsgInGameActions;

import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

/** Automated capture smoke test. Requires the explicit flag AND a development launch. */
final class ReplaySmokeTest {
    private static int stage;
    private static int ticks;
    private static long started;
    private static long pauseUntil;
    private static long screenUntil;
    private static long writerWaitStarted;
    private static int menuTicks;
    private static int resets;
    private static Object recording;
    private static java.nio.file.Path firstRecording;
    private static boolean replacedRecording;
    private static volatile long raceStart;
    private static net.minecraft.network.ClientConnection oldConnection;
    private static ReplayChestSmoke chestSmoke;
    private static ReplayMiningSoundSmoke miningSmoke;
    private static ReplayBarterSmoke barterSmoke, completedBarterSmoke;

    private ReplaySmokeTest() {
    }

    static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()
                || !Boolean.getBoolean("zsgrooms.replayPrototype.smokeTest")) return;
        ReplayPrototype.configure(true, "", false);
        started = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(ReplaySmokeTest::tick);
    }

    private static void tick(MinecraftClient client) {
        if (stage == 5) return;
        if (System.nanoTime() - started > 240000000000L) {
            fail(client, "timed out");
            return;
        }
        try {
            if (miningSmoke != null) {
                if (!miningSmoke.tick(client)) return;
                miningSmoke = null;
            }
            if (barterSmoke != null) {
                if (!barterSmoke.tick(client)) return;
                completedBarterSmoke = barterSmoke;
                barterSmoke = null;
            }
            if (chestSmoke != null) {
                if (!chestSmoke.tick(client)) return;
                chestSmoke = null;
            }
            if (screenUntil != 0L) {
                if (System.nanoTime() < screenUntil) return;
                client.openScreen(null);
                screenUntil = 0L;
            }
            if (pauseUntil != 0L) {
                if (System.nanoTime() < pauseUntil) return;
                if (!client.isPaused()) throw new IllegalStateException("Pause test did not pause the player");
                client.openScreen(null);
                pauseUntil = 0L;
            }
            if (stage == 0) {
                if (client.getOverlay() != null || client.currentScreen == null) return;
                menuTicks++;
                if (menuTicks == 1) client.openScreen(new RoomSettingsScreen(new TitleScreen()));
                if (menuTicks == 2 && (Integer.getInteger("zsgrooms.replayPrototype.smokeCameraHz", 0) != 0
                        || Boolean.getBoolean("zsgrooms.replayPrototype.smokeSuperDetailed"))) {
                    Class<?> page = Class.forName("zsgrooms.modid.ui.RoomSettingsScreen$Page");
                    java.lang.reflect.Method select = RoomSettingsScreen.class.getDeclaredMethod("select", page);
                    select.setAccessible(true);
                    select.invoke(client.currentScreen, java.lang.Enum.valueOf(page.asSubclass(Enum.class), "RECORDING"));
                    ReplayPrototype.configureCameraRate(ReplayPreferences.CameraRate.HZ_240);
                    if (Boolean.getBoolean("zsgrooms.replayPrototype.smokeSuperDetailed")) {
                        ReplayPrototype.configureRecordingDetail(ReplayPreferences.RecordingDetail.SUPER_DETAILED);
                        ReplayPrototype.configureCaptureHealth(true);
                    }
                    java.lang.reflect.Method scroll = RoomSettingsScreen.class.getDeclaredMethod("scroll", int.class);
                    scroll.setAccessible(true);
                    scroll.invoke(client.currentScreen, 2);
                }
                if (menuTicks == 8) screenshot(client, "room-settings");
                if (menuTicks == 9) client.openScreen(new ReplaySettingsScreen(new TitleScreen()));
                if (menuTicks == 10) client.currentScreen.mouseClicked(client.getWindow().getScaledWidth() / 2 + 50, 58, 0);
                if (menuTicks == 18) screenshot(client, "replay-setup");
                if (menuTicks == 19) client.currentScreen.mouseClicked(client.getWindow().getScaledWidth() / 2 - 50, 58, 0);
                if (menuTicks == 27) screenshot(client, "replay-recording");
                if (menuTicks == 28) client.openScreen(new zsgrooms.modid.ui.ReplayTestSettingsScreen(new TitleScreen()));
                if (menuTicks == 29) click(client, "New Group");
                if (menuTicks == 30) click(client, "Apply");
                if (menuTicks == 35) {
                    if (ReplayPrototype.getPreferences().soloTestGroup.isEmpty()) throw new IllegalStateException("Solo group not saved from UI");
                    screenshot(client, "replay-solo-testing");
                }
                if (menuTicks < 36) return;
                if (!ReplayPrototype.isLibraryReady()) return;
                if (!ReplayPrototype.configurePerformance(Boolean.getBoolean("zsgrooms.replayPrototype.smokePerformance"))) {
                    throw new IllegalStateException("Cannot configure replay performance mode");
                }
                ReplayPreferences.RecordingDetail detail = Boolean.getBoolean("zsgrooms.replayPrototype.smokeSuperDetailed")
                        ? ReplayPreferences.RecordingDetail.SUPER_DETAILED
                        : Boolean.getBoolean("zsgrooms.replayPrototype.smokeDetailed") ? ReplayPreferences.RecordingDetail.DETAILED
                        : ReplayPreferences.RecordingDetail.STANDARD;
                if (!ReplayPrototype.configureRecordingDetail(detail)) {
                    throw new IllegalStateException("Cannot configure detailed capture");
                }
                int cameraHz = Integer.getInteger("zsgrooms.replayPrototype.smokeCameraHz", 0);
                ReplayPreferences.CameraRate cameraRate = cameraHz == 60 ? ReplayPreferences.CameraRate.HZ_60
                        : cameraHz == 120 ? ReplayPreferences.CameraRate.HZ_120
                        : cameraHz == 240 ? ReplayPreferences.CameraRate.HZ_240 : ReplayPreferences.CameraRate.STANDARD;
                if (!ReplayPrototype.configureCameraRate(cameraRate)) throw new IllegalStateException("Cannot configure camera sampling");
                if (!ReplayPrototype.configureSeedRetention(Boolean.getBoolean("zsgrooms.replayPrototype.smokeKeepSeedChanges"))) {
                    throw new IllegalStateException("Cannot configure replay retention");
                }
                stage = 1;
                client.options.pauseOnLostFocus = false;
                client.options.viewDistance = 4;
                client.options.maxFps = Math.max(60, cameraHz);
                createWorld(client);
                return;
            }
            if (stage == 4) {
                if (ReplayPrototype.canConfigure()) {
                    if (!ReplayPrototype.getRecordingStatus().startsWith("Saved")) {
                        fail(client, "recording did not save automatically");
                        return;
                    }
                    stage = 5;
                    if (completedBarterSmoke != null) completedBarterSmoke.verify();
                    if (ReplayPrototype.getPreferences().cameraRate.hz != 0 && resets < 2) verifyCameraTrack();
                    if (ReplayPrototype.getPreferences().recordingDetail == ReplayPreferences.RecordingDetail.SUPER_DETAILED && resets < 2)
                        verifySuperDetailedTracks();
                    if (resets >= 2 && java.nio.file.Files.exists(firstRecording) != ReplayPrototype.getPreferences().keepSeedChanges) {
                        throw new IllegalStateException("Previous seed replay retention mismatch");
                    }
                    if (!ReplayPrototype.getPreferences().enabled) throw new IllegalStateException("Recording preference disabled by finish");
                    if (!ReplayPrototype.configureTestGroup("")) throw new IllegalStateException("Cannot disable test grouping after save");
                    if (client.world != null) {
                        client.world.disconnect();
                        client.disconnect();
                    }
                    ZsgRooms.LOGGER.info("[ReplaySmoke] PASS: capture completed; inspect MCPR before claiming playback compatibility");
                    client.scheduleStop();
                }
                return;
            }
            if (client.player == null || client.world == null || client.getNetworkHandler() == null
                    || client.currentScreen != null) return;
            if (!ReplayPrototype.hasSession()) {
                fail(client, "recording session missing");
                return;
            }
            if (recording == null) {
                recording = ReplayPrototype.recordingIdentity();
                firstRecording = ReplayPrototype.recordingFile();
            }
            if (resets == 2 && !replacedRecording) {
                if (recording == ReplayPrototype.recordingIdentity()) throw new IllegalStateException("New seed reused old recording");
                recording = ReplayPrototype.recordingIdentity();
                replacedRecording = true;
                ZsgRooms.LOGGER.info("[ReplaySmoke] New seed started a fresh recording");
            }
            if (recording != ReplayPrototype.recordingIdentity()) throw new IllegalStateException("Reset started another recording");
            if (stage == 1) {
                ticks++;
                if (ticks == 40 && resets == 0 && Boolean.getBoolean("zsgrooms.replayPrototype.benchmark")) {
                    ReplayCaptureBenchmark.run();
                }
                if (ticks == 1) {
                    onServer(client, player -> {
                        player.setGameMode(GameMode.CREATIVE);
                        // Synthetic race markers exercise the real writer across a local reset
                        // and a fresh round; LocalRaceClock's exact marker is tested separately.
                        if (resets != 1) raceStart = System.nanoTime();
                        ReplayPrototype.armRace(resets < 2 ? "smoke-first" : "smoke-second", resets < 2);
                        ReplayPrototype.raceStarted(resets < 2 ? "smoke-first" : "smoke-second",
                                player.getUuid(), raceStart);
                    });
                    if (oldConnection != null) ReplayPrototype.disconnected(oldConnection);
                }
                if (ticks == 20) {
                    // Writer startup is asynchronous; slow cold disks must not race the test's tick counter.
                    if (!ReplayPrototype.isRecording()) {
                        if (writerWaitStarted == 0L) writerWaitStarted = System.nanoTime();
                        if (System.nanoTime() - writerWaitStarted > 15000000000L)
                            throw new IllegalStateException("Writer did not start: " + ReplayPrototype.getRecordingStatus());
                        ticks--;
                        return;
                    }
                    writerWaitStarted = 0L;
                    boolean dirty = client.player.getDataTracker().isDirty();
                    ReplayPrototype.copyTrackedState(client.player.getEntityId(), client.player.getDataTracker());
                    ReplayTrackedState cache = new ReplayTrackedState();
                    if (ReplayPrototype.copyTrackedState(client.player.getEntityId(), client.player.getDataTracker(), cache) == null
                            || ReplayPrototype.copyTrackedState(client.player.getEntityId(), client.player.getDataTracker(), cache) != null) {
                        throw new IllegalStateException("Metadata cache did not suppress unchanged state");
                    }
                    if (dirty != client.player.getDataTracker().isDirty()) throw new IllegalStateException("Tracker mutated");
                    onServer(client, player -> {
                        ServerWorld world = player.getServerWorld();
                        BlockPos pos = player.getBlockPos().add(2, 0, 0);
                        world.setBlockState(pos, Blocks.DIAMOND_BLOCK.getDefaultState());
                        PigEntity pig = EntityType.PIG.create(world);
                        if (pig != null) {
                            pig.refreshPositionAndAngles(pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0);
                            pig.setCustomName(new LiteralText("Replay smoke test"));
                            world.spawnEntity(pig);
                        }
                        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                        player.inventory.setStack(1, new ItemStack(Items.OBSIDIAN, 12));
                        player.inventory.setStack(9, new ItemStack(Items.ENDER_PEARL, 7));
                        player.inventory.setStack(18, new ItemStack(Items.BLAZE_ROD, 6));
                        player.inventory.setStack(27, new ItemStack(Items.BREAD, 23));
                        player.inventory.setStack(38, new ItemStack(Items.IRON_CHESTPLATE));
                        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
                        player.setHealth(14);
                        player.getHungerManager().setFoodLevel(13);
                        player.addExperience(42);
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 3600));
                    });
                    client.options.keyForward.setPressed(true);
                    if (Boolean.getBoolean("zsgrooms.replayPrototype.smokeSuperDetailed")) {
                        client.options.fov = 92;
                        client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_STONE_STEP, .4F, 1.25F);
                        client.world.syncWorldEvent(client.player, 2001, client.player.getBlockPos(),
                                net.minecraft.block.Block.getRawIdFromState(Blocks.STONE.getDefaultState()));
                    }
                }
                if (ticks == 60) {
                    screenshot(client, "replay-hud");
                    client.options.keyForward.setPressed(false);
                    client.player.swingHand(Hand.MAIN_HAND);
                    if (Boolean.getBoolean("zsgrooms.replayPrototype.smokeSuperDetailed")) {
                        client.options.fov = 70;
                        client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EAT, .5F, 1.1F);
                    }
                    onServer(client, player -> {
                        // Keep status screenshots clear of the spawn's trees and camera collisions.
                        BlockPos platform = player.getBlockPos().add(0, 16, 0);
                        for (int x = -2; x <= 2; x++) {
                            for (int z = -2; z <= 2; z++) player.getServerWorld().setBlockState(
                                    platform.add(x, 0, z), Blocks.STONE.getDefaultState());
                        }
                        player.teleport(player.getServerWorld(), platform.getX() + 0.5,
                                platform.getY() + 1, platform.getZ() + 0.5, 0, 0);
                    });
                }
                if (ticks == 65 && Boolean.getBoolean("zsgrooms.replayPrototype.smokeSuperDetailed"))
                    miningSmoke = new ReplayMiningSoundSmoke();
                if (ticks == 70) {
                    client.openScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
                    pauseUntil = System.nanoTime() + 2000000000L;
                }
                if (ticks == 80) {
                    client.openScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
                    screenUntil = System.nanoTime() + 2000000000L;
                }
                if (ticks == 85) {
                    client.openScreen(new net.minecraft.client.gui.screen.ingame.CraftingScreen(
                            new net.minecraft.screen.CraftingScreenHandler(1, client.player.inventory),
                            client.player.inventory, new LiteralText("Crafting")));
                    screenUntil = System.nanoTime() + 2000000000L;
                }
                if (ticks == 90) chestSmoke = new ReplayChestSmoke();
                if (ticks == 95) barterSmoke = new ReplayBarterSmoke();
                if (ticks >= 100) {
                    stage = 2;
                    ticks = 0;
                    onServer(client, player -> player.teleport(player.getServer().getWorld(World.NETHER),
                            8.5, 100, 8.5, 0, 0));
                }
            } else if (stage == 2 && client.world.getRegistryKey() == World.NETHER) {
                if (++ticks >= 60) {
                    stage = 3;
                    ticks = 0;
                    onServer(client, player -> {
                        ServerWorld world = player.getServer().getOverworld();
                        BlockPos pos = world.getSpawnPos();
                        player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                    });
                }
            } else if (stage == 3 && client.world.getRegistryKey() == World.OVERWORLD && ++ticks >= 60) {
                if (resets < Integer.getInteger("zsgrooms.replayPrototype.smokeResets", 0)) {
                    resets++;
                    stage = 1;
                    ticks = 0;
                    oldConnection = client.getNetworkHandler().getConnection();
                    if (FabricLoader.getInstance().isModLoaded("atum")) {
                        if (!zsgrooms.modid.ZsgSeedBridge.launchSeedWithAtum(resets == 1 ? "12345" : "67890")) {
                            throw new IllegalStateException("Atum reset rejected");
                        }
                    } else {
                        ReplayPrototype.beginReset();
                        client.world.disconnect();
                        client.disconnect();
                        client.openScreen(new TitleScreen());
                        createWorld(client);
                    }
                    if (!ReplayPrototype.hasSession()) throw new IllegalStateException("Reset lost recording session");
                    ZsgRooms.LOGGER.info("[ReplaySmoke] Awaiting reset {} replacement world", resets);
                    return;
                }
                stage = 4;
                ReplayPrototype.raceFinished(resets < 2 ? "smoke-first" : "smoke-second",
                        System.nanoTime() - raceStart, 11000L);
                String end = System.getProperty("zsgrooms.replayPrototype.smokeEnd", "quit");
                if ("quit".equals(end)) {
                    client.world.disconnect();
                    client.disconnect();
                    client.openScreen(new TitleScreen());
                } else if ("room".equals(end)) {
                    ZsgRooms.createRoom("Replay smoke room", 2, 1, "12345", client.getSession().getUsername());
                    ZsgRooms.getGame("Replay smoke room").startGame();
                    ZsgInGameActions.returnToRoom(client);
                    if (!ReplayPrototype.hasReturnedToRoom()) throw new IllegalStateException("Room return kept prediction blocked");
                    if (!ZsgRooms.getGame("Replay smoke room").getIsInGame()) throw new IllegalStateException("Room return changed shared race state");
                } else {
                    String winner = "victory".equals(end) ? client.getSession().getUsername() : "Other player";
                    String reason;
                    if ("draw".equals(end)) reason = zsgrooms.modid.net.RaceFinishArbiter.DRAW_REASON;
                    else if ("forfeit".equals(end)) reason = "Forfeit";
                    else if ("victory".equals(end) || "loss".equals(end)) reason = "Beat the seed in 00:11.000 IGT";
                    else throw new IllegalArgumentException("Unknown smoke end mode");
                    ZsgInGameActions.showMatchResult(client, winner, reason);
                }
                if (ReplayPrototype.isRecording()) throw new IllegalStateException("End event left recording open");
                ZsgRooms.LOGGER.info("[ReplaySmoke] Automatic finish triggered: {}", end);
            }
        } catch (Exception e) {
            fail(client, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void verifySuperDetailedTracks() throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(firstRecording.toFile())) {
            for (String name : new String[]{"presentation", "local-sounds"}) {
                java.util.zip.ZipEntry entry = zip.getEntry("zsg-rooms/" + name + ".bin");
                if (entry == null || entry.getSize() <= 20) throw new IllegalStateException("Missing Super Detailed " + name);
                java.util.zip.ZipEntry metadata = zip.getEntry("zsg-rooms/" + name + ".json");
                if (metadata == null) throw new IllegalStateException("Missing Super Detailed coverage " + name);
                try (java.io.Reader reader = new java.io.InputStreamReader(zip.getInputStream(metadata), java.nio.charset.StandardCharsets.UTF_8)) {
                    com.google.gson.JsonObject coverage = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
                    String status = coverage.get("status").getAsString();
                    if (!"complete".equals(status) && !("presentation".equals(name) && "gaps".equals(status)))
                        throw new IllegalStateException("Super Detailed " + name + " failed: " + coverage);
                    if (coverage.get("presentation".equals(name) ? "frames" : "events").getAsLong() <= 0)
                        throw new IllegalStateException("Super Detailed " + name + " was empty");
                    ZsgRooms.LOGGER.info("[ReplaySuperSmoke] {} {}", name, coverage);
                }
            }
            verifyMiningSounds(zip);
        }
    }

    private static void verifyMiningSounds(java.util.zip.ZipFile zip) throws Exception {
        int hits = 0;
        try (java.io.DataInputStream input = new java.io.DataInputStream(new java.io.BufferedInputStream(
                zip.getInputStream(zip.getEntry("zsg-rooms/local-sounds.bin"))))) {
            if (input.readInt() != 0x5A534E44 || input.readInt() != 1 || input.readInt() != 127)
                throw new IllegalStateException("Unexpected sound track header");
            while (true) {
                int kind = input.readUnsignedByte(); input.readInt();
                if (kind == 0) break;
                input.readInt(); input.readInt();
                if (kind == 1) { input.readInt(); continue; }
                if (kind != 2) throw new IllegalStateException("Unknown sound track record");
                int channel = input.readUnsignedByte(), category = input.readUnsignedByte();
                float volume = input.readFloat(), pitch = input.readFloat();
                double x = input.readDouble(), y = input.readDouble(), z = input.readDouble();
                String id = input.readUTF();
                if ("minecraft:block.stone.hit".equals(id)) {
                    if (channel != ReplayLocalSoundTrack.MINING || category != net.minecraft.sound.SoundCategory.BLOCKS.ordinal()
                            || volume != .25F || pitch != .5F || x - Math.floor(x) != .5 || y - Math.floor(y) != .5 || z - Math.floor(z) != .5)
                        throw new IllegalStateException("Mining sound did not preserve constructor inputs");
                    hits++;
                }
            }
        }
        if (hits < 2) throw new IllegalStateException("Missing normal/muted survival mining sound capture");
        ZsgRooms.LOGGER.info("[ReplaySuperSmoke] Survival mining: {} raw hit events, including muted audio", hits);
    }

    private static void verifyCameraTrack() throws Exception {
        int frames = 0, fastIntervals = 0, previous = -1;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(firstRecording.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("zsg-rooms/camera.bin");
            if (entry == null) throw new IllegalStateException("Camera track missing");
            try (java.io.DataInputStream input = new java.io.DataInputStream(new java.io.BufferedInputStream(zip.getInputStream(entry)))) {
                if (input.readInt() != 0x5A43414D || input.readInt() != 1
                        || input.readInt() != ReplayPrototype.getPreferences().cameraRate.hz)
                    throw new IllegalStateException("Invalid camera header");
                long remaining = entry.getSize() - 12;
                if (remaining < 24 || remaining % 24 != 0) throw new IllegalStateException("Invalid camera length");
                for (; remaining > 0; remaining -= 24) {
                    int time = input.readInt(), world = input.readInt(), entity = input.readInt(), flags = input.readInt();
                    float yaw = input.readFloat(), pitch = input.readFloat();
                    if (time <= previous || world < -1 || entity < -1 || !Float.isFinite(yaw) || !Float.isFinite(pitch))
                        throw new IllegalStateException("Invalid camera sample");
                    if (flags == 1 && previous >= 0 && time - previous < 50) fastIntervals++;
                    frames++;
                    previous = time;
                }
            }
        }
        if (frames < 100 || fastIntervals < 50) throw new IllegalStateException("Render-rate camera sampling was not exercised");
        ZsgRooms.LOGGER.info("[ReplaySmoke] Camera track PASS: {} frames, {} sub-tick intervals", frames, fastIntervals);
    }

    private static void click(MinecraftClient client, String label) {
        for (net.minecraft.client.gui.Element element : client.currentScreen.children()) {
            if (element instanceof net.minecraft.client.gui.widget.ButtonWidget) {
                net.minecraft.client.gui.widget.ButtonWidget button = (net.minecraft.client.gui.widget.ButtonWidget) element;
                if (button.getMessage().getString().equals(label)) {
                    if (!button.active) throw new IllegalStateException("Inactive button: " + label);
                    button.onPress();
                    return;
                }
            }
        }
        throw new IllegalStateException("Missing button: " + label);
    }

    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name + ".png", client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void createWorld(MinecraftClient client) {
        Properties properties = new Properties();
        properties.setProperty("level-seed", resets < 2 ? "12345" : "67890");
        properties.setProperty("generate-structures", "false");
        client.method_29607("replay-smoke-" + UUID.randomUUID(),
                new LevelInfo("Replay smoke test", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), DataPackSettings.SAFE_MODE),
                RegistryTracker.create(), GeneratorOptions.fromProperties(properties));
    }

    private static void onServer(MinecraftClient client, Consumer<ServerPlayerEntity> action) {
        UUID id = client.player.getUuid();
        IntegratedServer server = client.getServer();
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    private static void fail(MinecraftClient client, String reason) {
        stage = 5;
        ZsgRooms.LOGGER.error("[ReplaySmoke] FAIL: {}", reason);
        client.options.keyForward.setPressed(false);
        client.scheduleStop();
    }
}
