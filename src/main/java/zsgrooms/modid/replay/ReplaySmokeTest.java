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
    private static int menuTicks;
    private static int resets;
    private static Object recording;
    private static volatile long raceStart;
    private static net.minecraft.network.ClientConnection oldConnection;

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
                stage = 1;
                client.options.pauseOnLostFocus = false;
                client.options.viewDistance = 4;
                client.options.maxFps = 60;
                createWorld(client);
                return;
            }
            if (stage == 4) {
                if (!ReplayPrototype.hasSession()) {
                    if (!"Saved to replay_recordings".equals(ReplayPrototype.getRecordingStatus())) {
                        fail(client, "recording did not save automatically");
                        return;
                    }
                    stage = 5;
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
            if (recording == null) recording = ReplayPrototype.recordingIdentity();
            if (recording != ReplayPrototype.recordingIdentity()) throw new IllegalStateException("Reset started another recording");
            if (stage == 1) {
                ticks++;
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
                    boolean dirty = client.player.getDataTracker().isDirty();
                    ReplayPrototype.copyTrackedState(client.player.getEntityId(), client.player.getDataTracker());
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
                    });
                    client.options.keyForward.setPressed(true);
                }
                if (ticks == 60) {
                    screenshot(client, "replay-hud");
                    client.options.keyForward.setPressed(false);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
                if (ticks == 70) {
                    client.openScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
                    pauseUntil = System.nanoTime() + 2000000000L;
                }
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
                    if (!ReplayPrototype.isRecording()) throw new IllegalStateException("Reset ended recording");
                    ZsgRooms.LOGGER.info("[ReplaySmoke] Continued reset {} in the same recording", resets);
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
                    ZsgInGameActions.returnToRoom(client);
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
            fail(client, e.getClass().getSimpleName());
        }
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
