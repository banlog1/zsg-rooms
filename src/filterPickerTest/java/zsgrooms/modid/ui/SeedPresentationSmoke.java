package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.Player;
import zsgrooms.modid.ZsgRooms;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

public final class SeedPresentationSmoke {
    private static boolean realLoad;
    private static boolean capturedLoad;
    private static boolean previewInitialized;
    private static boolean failed;
    private static int frames;
    private static LevelLoadingScreen pendingFrame;
    private int stage;
    private int ticks;
    private int clicks;
    private final long started = System.nanoTime();

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (failed) return;
        if (System.nanoTime() - started > 180_000_000_000L) { fail(client, new IllegalStateException("Timed out")); return; }
        if (client.getOverlay() != null || ++ticks < 30) return;
        ticks = 0;
        try {
            switch (stage++) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.maxFps = 60;
                    client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    RoomUiPreferences.setLoadingImagesEnabled(true);
                    break;
                case 1: client.openScreen(new HudScreen()); break;
                case 2:
                    screenshot(client, "seed-hud-desktop.png");
                    openLoading(client, "rooms-temple-v5");
                    break;
                case 3:
                    require(active(client), "Temple artwork missing");
                    screenshot(client, "loading-temple.png");
                    client.currentScreen.mouseClicked(9, 9, 0);
                    require(clicks == 0, "Hidden preview button received click");
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    require(clicks == 0, "Hidden preview button received keyboard input");
                    require(RoomLoadingArtwork.capture() == null, "Launch context leaked");
                    RoomUiPreferences.setLoadingImagesEnabled(false);
                    require(!active(client), "Loading toggle ignored");
                    client.currentScreen.mouseClicked(9, 9, 0);
                    require(clicks == 1, "Disabled artwork still blocked controls");
                    RoomUiPreferences.setLoadingImagesEnabled(true);
                    openLoading(client, "rooms-shipwreck-v5");
                    break;
                case 4:
                    screenshot(client, "loading-shipwreck.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 5: openLoading(client, "rooms-village-v5"); break;
                case 6:
                    screenshot(client, "loading-village-small.png");
                    client.openScreen(new HudScreen());
                    break;
                case 7:
                    screenshot(client, "seed-hud-small.png");
                    client.openScreen(new RoomSettingsScreen(new TitleScreen()));
                    break;
                case 8:
                    screenshot(client, "loading-settings-small.png");
                    client.getResourcePackManager().setEnabledProfiles(Collections.singleton("vanilla"));
                    client.reloadResources();
                    break;
                case 9:
                    openLoading(client, "rooms-temple-v5");
                    require(!active(client), "Missing pack did not fall back");
                    break;
                case 10:
                    screenshot(client, "loading-no-pack.png");
                    client.getResourcePackManager().setEnabledProfiles(java.util.Arrays.asList("vanilla", "file/zsg-rooms-filter-gallery.zip"));
                    client.reloadResources();
                    break;
                case 11:
                    openLoading(client, "manual");
                    require(!active(client), "Manual seed received unrelated artwork");
                    client.openScreen(new LevelLoadingScreen(tracker()));
                    require(!active(client), "Ordinary world load received stale artwork");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    break;
                case 12:
                    client.options.viewDistance = 3;
                    RoomLoadingArtwork.prepare("12345|structure:rooms-temple-v5|selection:rooms-mix");
                    realLoad = true;
                    Properties properties = new Properties();
                    properties.setProperty("level-seed", "12345");
                    properties.setProperty("generate-structures", "false");
                    client.method_29607("loading-art-smoke-" + UUID.randomUUID(),
                            new LevelInfo("Loading artwork smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), DataPackSettings.SAFE_MODE),
                            RegistryTracker.create(), GeneratorOptions.fromProperties(properties));
                    break;
                default:
                    if (client.world == null || client.player == null) return;
                    require(capturedLoad, "Real loading screen was not captured");
                    require(!FabricLoader.getInstance().isModLoaded("worldpreview") || previewInitialized,
                            "WorldPreview did not initialize beneath artwork");
                    client.world.disconnect();
                    client.disconnect();
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: seed HUD, loading art, missing pack, input, resize and real generation; WorldPreview={}", previewInitialized);
                    client.scheduleStop();
                    failed = true;
            }
        } catch (Throwable error) { fail(client, error); }
    }

    public static void loadingFrame(LevelLoadingScreen screen) {
        pendingFrame = screen;
    }

    public static void completedFrame() {
        LevelLoadingScreen screen = pendingFrame;
        pendingFrame = null;
        if (screen == null) return;
        if (!realLoad || capturedLoad || failed) return;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (FabricLoader.getInstance().isModLoaded("worldpreview")) {
                Object properties = Class.forName("me.voidxwalker.worldpreview.WorldPreview").getField("properties").get(null);
                previewInitialized = properties != null && (Boolean) properties.getClass().getMethod("isInitialized").invoke(properties);
            }
            if (++frames < 60 || FabricLoader.getInstance().isModLoaded("worldpreview") && !previewInitialized) return;
            require(((RoomLoadingArtwork.ScreenState) screen).zsgRooms$hasLoadingArtwork(), "Real load lost artwork context");
            screenshot(client, "loading-real-world.png");
            capturedLoad = true;
        } catch (Throwable error) { fail(client, error); }
    }

    private void openLoading(MinecraftClient client, String filter) {
        RoomLoadingArtwork.prepare("12345|structure:" + filter + "|selection:rooms-mix");
        client.openScreen(new LevelLoadingScreen(tracker()) {
            @Override protected void init() {
                super.init();
                addButton(new ButtonWidget(8, 8, 40, 20, new LiteralText("Test"), button -> clicks++));
            }
        });
    }

    private static WorldGenerationProgressTracker tracker() {
        WorldGenerationProgressTracker tracker = new WorldGenerationProgressTracker(11);
        tracker.start();
        tracker.start(new ChunkPos(0, 0));
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) tracker.setChunkStatus(new ChunkPos(x, z), ChunkStatus.FULL);
        return tracker;
    }

    private static boolean active(MinecraftClient client) {
        return ((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasLoadingArtwork();
    }

    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void fail(MinecraftClient client, Throwable error) {
        failed = true;
        ZsgRooms.LOGGER.error("[SeedVisualSmoke] FAILED", error);
        client.scheduleStop();
    }

    private static final class HudScreen extends Screen {
        private HudScreen() { super(new LiteralText("HUD visual test")); }
        @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            renderBackground(matrices);
            String[] filters = {"zsgop", "rooms-temple-v5", "rooms-village-v5", "rooms-shipwreck-v5", "rpseedbank", "manual"};
            int columns = width >= 500 ? 3 : 1;
            int count = columns == 1 ? 2 : filters.length;
            Player[] players = {new Player("Runner", true, true), new Player("Opponent", true, false)};
            for (int i = 0; i < count; i++) {
                MatchHudPreferences prefs = new MatchHudPreferences();
                prefs.header = i % 2 == 0;
                prefs.opacity = i % 2 == 0 ? 70 : 0;
                float scale = i % 3 == 0 ? 0.75F : i % 3 == 1 ? 1 : 1.2F;
                MatchHud.drawPanel(matrices, client, prefs, players, Collections.emptyMap(), Collections.emptyMap(),
                        "Runner", 10 + (i % columns) * (width / columns), 12 + (i / columns) * 100,
                        scale, 0, filters[i]);
            }
        }
    }
}
