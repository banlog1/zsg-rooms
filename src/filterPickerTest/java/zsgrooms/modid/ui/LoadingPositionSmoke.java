package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

final class LoadingPositionSmoke {
    private int stage;
    private int ticks;
    private int image;
    private boolean finished;

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished || client.getOverlay() != null || ++ticks < 15) return;
        ticks = 0;
        try {
            if (stage == 0) {
                client.options.pauseOnLostFocus = false;
                client.options.guiScale = 2;
                client.options.maxFps = 60;
                RoomUiPreferences.setLoadingImagesEnabled(true);
                RoomUiPreferences.setLoadingIndicatorStyle(LoadingIndicatorStyle.VANILLA);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                client.openScreen(new RoomSettingsScreen(new TitleScreen()));
                stage++;
            } else if (stage == 1) {
                click(client, "Loading Screen...");
                require(client.currentScreen instanceof LoadingSettingsScreen, "Settings link missing");
                click(client, "ZSG");
                require(RoomUiPreferences.getLoadingIndicatorStyle() == LoadingIndicatorStyle.ZSG, "Style did not save");
                click(client, "Progress: Bottom Right");
                require(RoomUiPreferences.getLoadingProgressPosition() == LoadingProgressPosition.BOTTOM_RIGHT, "Preset did not save");
                stage++;
            } else if (stage == 2) {
                capture(client, "loading-settings-presets-large.png");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 360);
                stage++;
            } else if (stage == 3) {
                for (Element element : client.currentScreen.children()) {
                    if (!(element instanceof ButtonWidget)) continue;
                    ButtonWidget button = (ButtonWidget) element;
                    require(button.y >= 0 && button.y + button.getHeight() <= client.currentScreen.height, "Button outside viewport");
                    require(client.textRenderer.getWidth(button.getMessage()) < button.getWidth(), "Button text clipped");
                }
                capture(client, "loading-settings-presets-small.png");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                stage++;
            } else if (stage == 4) {
                if (image > 0) capture(client, "loading-position-" + (image - 1) + ".png");
                if (image == 5) {
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 360);
                    stage = 5;
                    return;
                }
                if (image == 10) {
                    RoomUiPreferences.setLoadingImagesEnabled(false);
                    require(!((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasLoadingArtwork(), "Disabled art still active");
                    require(((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasCustomLoadingScreen(), "Logo requires artwork");
                    stage = 6;
                    return;
                }
                showLoading(client);
            } else if (stage == 5) {
                showLoading(client);
                stage = 4;
            } else if (stage == 6) {
                capture(client, "loading-logo-without-art.png");
                RoomUiPreferences.setLoadingIndicatorStyle(LoadingIndicatorStyle.VANILLA);
                require(!((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasCustomLoadingScreen(), "Vanilla fallback still overridden");
                stage++;
            } else if (stage == 7) {
                capture(client, "loading-vanilla-without-art.png");
                RoomUiPreferences.setLoadingImagesEnabled(true);
                stage++;
            } else if (stage == 8) {
                require(((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasLoadingArtwork(), "Vanilla lost background");
                capture(client, "loading-vanilla-with-art.png");
                RoomUiPreferences.setLoadingIndicatorStyle(LoadingIndicatorStyle.ZSG);
                RoomLoadingArtwork.cancel();
                client.openScreen(new LevelLoadingScreen(new WorldGenerationProgressTracker(11)));
                require(!((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasCustomLoadingScreen(), "Unrelated world got room logo");
                client.openScreen(new TitleScreen());
                client.getResourcePackManager().setEnabledProfiles(java.util.Collections.singleton("vanilla"));
                client.reloadResources();
                stage++;
            } else if (stage == 9) {
                RoomUiPreferences.setLoadingProgressPosition(LoadingProgressPosition.CENTER);
                RoomLoadingArtwork.prepare("12345|structure:rooms-temple-v5");
                client.openScreen(new LevelLoadingScreen(new WorldGenerationProgressTracker(11)));
                require(!((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasLoadingArtwork(), "Pack did not unload");
                require(((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasCustomLoadingScreen(), "Packless logo inactive");
                require(client.getResourceManager().containsResource(new net.minecraft.util.Identifier("zsg-rooms", "textures/gui/loading_logo.png")), "Bundled logo missing");
                stage++;
            } else {
                capture(client, "loading-logo-without-pack.png");
                ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: indicator selection, settings sizes, ten logo layouts at 0-100%, vanilla fallback, unrelated-world isolation and logo without image pack");
                finished = true;
                client.scheduleStop();
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[LoadingPositionSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private void showLoading(MinecraftClient client) {
        RoomUiPreferences.setLoadingProgressPosition(LoadingProgressPosition.values()[image % 5]);
        RoomLoadingArtwork.prepare("12345|structure:rooms-temple-v5");
        WorldGenerationProgressTracker tracker = new WorldGenerationProgressTracker(11);
        tracker.start();
        tracker.start(new ChunkPos(0, 0));
        int remaining = (23 * 23 * (image % 5) + 3) / 4;
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            if (remaining-- > 0) tracker.setChunkStatus(new ChunkPos(x, z), ChunkStatus.FULL);
        }
        client.openScreen(new LevelLoadingScreen(tracker));
        require(((RoomLoadingArtwork.ScreenState) client.currentScreen).zsgRooms$hasLoadingArtwork(), "Artwork missing");
        image++;
    }

    private static void click(MinecraftClient client, String label) {
        for (Element element : client.currentScreen.children()) {
            if (element instanceof ButtonWidget && ((ButtonWidget) element).getMessage().getString().equals(label)) {
                ((ButtonWidget) element).onPress();
                return;
            }
        }
        throw new IllegalStateException("Missing button: " + label);
    }

    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
