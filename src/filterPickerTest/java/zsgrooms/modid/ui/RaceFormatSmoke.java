package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.*;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.ArrayList;
import java.util.List;

final class RaceFormatSmoke {
    private int stage;
    private int ticks;
    private boolean finished;

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished || client.getOverlay() != null || ++ticks < 25) return;
        ticks = 0;
        try {
            switch (stage++) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.maxFps = 60;
                    client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    client.openScreen(new RoomSetupScreen(new TitleScreen(), true));
                    break;
                case 1:
                    click(client, "1 seed, 1 finisher");
                    break;
                case 2:
                    capture(client, "race-format-desktop.png");
                    fields(client).get(0).setText("3");
                    fields(client).get(1).setText("2");
                    click(client, "Done");
                    click(client, "3 seeds, 2 finishers");
                    require(fields(client).get(0).getText().equals("3"), "Seed count not preserved");
                    require(fields(client).get(1).getText().equals("2"), "Finisher count not preserved");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 3:
                    moveMouse(client, 0, 0);
                    break;
                case 4:
                    capture(client, "race-format-small.png");
                    TextFieldWidget seed = fields(client).get(0);
                    moveMouse(client, seed.x + 10, seed.y + 10);
                    break;
                case 5:
                    capture(client, "race-format-seed-tooltip-small.png");
                    TextFieldWidget finisher = fields(client).get(1);
                    moveMouse(client, finisher.x + 10, finisher.y + 10);
                    break;
                case 6:
                    capture(client, "race-format-finisher-tooltip-small.png");
                    click(client, "Done");
                    moveMouse(client, 0, 0);
                    break;
                case 7:
                    capture(client, "race-format-setup-small.png");
                    Room room = new Room("format-smoke", "123|structure:zsg", new Player(ZsgRoomsClient.localPlayerName(client), true, true), 4);
                    InGame game = new InGame(room.seed, room.roomName, InGame.SeedType.FIXED, false);
                    ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                    client.openScreen(new RoomOptionsScreen(new TitleScreen(), room.roomName));
                    click(client, "1 seed, 1 finisher");
                    fields(client).get(0).setText("2");
                    fields(client).get(1).setText("3");
                    click(client, "Done");
                    break;
                case 8:
                    capture(client, "race-format-options-small.png");
                    click(client, "Apply");
                    require(ZsgRooms.getGame("format-smoke").getFinishGoal() == 2, "Lobby seed count not applied");
                    require(ZsgRooms.getGame("format-smoke").getFinisherLimit() == 3, "Lobby finisher limit not applied");
                    finished = true;
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: separate race-format defaults, editing, lobby apply, small/desktop layouts and tooltips");
                    client.scheduleStop();
                    break;
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[RaceFormatSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private static List<TextFieldWidget> fields(MinecraftClient client) {
        List<TextFieldWidget> result = new ArrayList<>();
        for (Element child : client.currentScreen.children()) if (child instanceof TextFieldWidget) result.add((TextFieldWidget) child);
        return result;
    }

    private static void click(MinecraftClient client, String label) {
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (button.getMessage().getString().equals(label) && button.active) { button.onPress(); return; }
        }
        throw new IllegalStateException("Missing button: " + label);
    }

    private static void moveMouse(MinecraftClient client, int x, int y) throws Exception {
        GLFW.glfwSetCursorPos(client.getWindow().getHandle(), x * client.getWindow().getScaleFactor(), y * client.getWindow().getScaleFactor());
        java.lang.reflect.Method cursor = client.mouse.getClass().getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
        cursor.setAccessible(true);
        cursor.invoke(client.mouse, client.getWindow().getHandle(), x * client.getWindow().getScaleFactor(), y * client.getWindow().getScaleFactor());
    }

    private static void capture(MinecraftClient client, String name) {
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= client.currentScreen.width
                    && button.y + button.getHeight() <= client.currentScreen.height, "Control outside viewport");
            require(client.textRenderer.getWidth(button.getMessage()) <= button.getWidth() - 2, "Label overflow");
        }
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
