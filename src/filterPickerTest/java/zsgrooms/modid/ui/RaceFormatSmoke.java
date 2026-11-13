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
    private String optionsBefore;

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
                    client.openScreen(new RoomOptionsScreen(new TitleScreen(), "format-smoke"));
                    optionsBefore = ZsgRooms.createRoomSnapshot("format-smoke");
                    require(fields(client).size() == 1, "Non-manual filter shows a seed field");
                    fields(client).get(0).setText("6");
                    break;
                case 9:
                    capture(client, "room-options-minimal-small.png");
                    selectFilter(client, "manual");
                    require(fields(client).size() == 2, "Manual seed field missing");
                    fields(client).get(0).setText("935645362601973608");
                    selectFilter(client, "rooms-mix");
                    require(fields(client).size() == 1, "Hidden manual field is still interactive");
                    require(fields(client).get(0).getText().equals("6"), "Switch lost capacity draft");
                    selectFilter(client, "manual");
                    require(fields(client).get(0).getText().equals("935645362601973608"), "Switch lost manual seed draft");
                    break;
                case 10:
                    capture(client, "room-options-manual-small.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    break;
                case 11:
                    capture(client, "room-options-manual-desktop.png");
                    require(fields(client).get(0).getText().equals("935645362601973608"), "Resize lost manual seed draft");
                    selectFilter(client, "rooms-mix");
                    break;
                case 12:
                    capture(client, "room-options-minimal-desktop.png");
                    selectFilter(client, AaThunderless.FILTER);
                    require(fields(client).size() == 1, "AA contains a manual field");
                    break;
                case 13:
                    capture(client, "room-options-aa-desktop.png");
                    selectFilter(client, "manual");
                    require(optionsBefore.equals(ZsgRooms.createRoomSnapshot("format-smoke")), "Filter preview changed room before Apply");
                    click(client, "Apply");
                    require(ZsgRooms.getGame("format-smoke").targetStructure.equals("manual:935645362601973608"), "Manual draft not applied");
                    require(ZsgRooms.getRoom("format-smoke").maxPlayers == 6, "Capacity draft not applied");
                    finished = true;
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: race-format editing; manual-only seed field, draft preservation, filter switching, AA details, lobby apply and small/desktop layouts");
                    client.scheduleStop();
                    break;
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[RaceFormatSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private static void selectFilter(MinecraftClient client, String id) {
        ((ButtonWidget) client.currentScreen.children().get(0)).onPress();
        require(client.currentScreen instanceof FilterPickerScreen, "Filter picker missing");
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget b = (ButtonWidget) child;
            if (b.getMessage().getString().equals(FilterCatalog.find(id).group.label) && b.active) { b.onPress(); break; }
        }
        String label = ZsgSeedBridge.seedTypeLabel(id);
        for (int page = 0; page < 10; page++) {
            for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
                ButtonWidget b = (ButtonWidget) child;
                if (b.getMessage().getString().equals(label)) { b.onPress(); return; }
            }
            click(client, ">");
        }
        throw new AssertionError("Filter missing: " + id);
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
        if (client.currentScreen instanceof RoomOptionsScreen) {
            List<net.minecraft.client.gui.widget.AbstractButtonWidget> widgets = new ArrayList<>();
            for (Element child : client.currentScreen.children()) if (child instanceof net.minecraft.client.gui.widget.AbstractButtonWidget) {
                net.minecraft.client.gui.widget.AbstractButtonWidget b = (net.minecraft.client.gui.widget.AbstractButtonWidget) child;
                require(b.x >= 0 && b.y >= 0 && b.x + b.getWidth() <= client.currentScreen.width
                        && b.y + b.getHeight() <= client.currentScreen.height, "Options control outside viewport");
                for (net.minecraft.client.gui.widget.AbstractButtonWidget other : widgets)
                    require(b.x >= other.x + other.getWidth() || other.x >= b.x + b.getWidth()
                            || b.y >= other.y + other.getHeight() || other.y >= b.y + b.getHeight(), "Options controls overlap");
                widgets.add(b);
            }
            ButtonWidget filter = (ButtonWidget) client.currentScreen.children().get(0);
            int expectedGap = RoomOptionsScreen.hasFilterDetails(ZsgSeedBridge.normalizeSeedType(filterId(filter))) ? 2 : 1;
            for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
                ButtonWidget b = (ButtonWidget) child;
                if (b.getMessage().getString().equals("2 seeds, 3 finishers"))
                    require(b.y - filter.y == expectedGap * (client.currentScreen.height < 240 ? 22 : 26), "Blank filter row remains");
            }
        }
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= client.currentScreen.width
                    && button.y + button.getHeight() <= client.currentScreen.height, "Control outside viewport");
            require(client.textRenderer.getWidth(button.getMessage()) <= button.getWidth() - 2, "Label overflow");
        }
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static String filterId(ButtonWidget button) {
        for (FilterCatalog.Entry entry : FilterCatalog.ENTRIES)
            if (ZsgSeedBridge.seedTypeLabel(entry.id).equals(button.getMessage().getString())) return entry.id;
        throw new AssertionError("Unknown filter label");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
