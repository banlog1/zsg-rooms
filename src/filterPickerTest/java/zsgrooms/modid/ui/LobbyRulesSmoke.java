package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Player;
import zsgrooms.modid.Room;
import zsgrooms.modid.RoomRuleSettings;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.net.RoomSnapshot;

final class LobbyRulesSmoke {
    private static final String ROOM = "ui-rule-test";
    private int stage;
    private int ticks;
    private boolean finished;
    private RoomLobbyScreen lobby;
    private String host;

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
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1920, 1080);
                    host = client.getSession().getUsername();
                    Room room = new Room(ROOM, "123|structure:rooms-temple-v5", new Player(host, true, true), 4);
                    room.addPlayer(new Player("Runner 2", true, false));
                    InGame game = new InGame(room.seed, ROOM, InGame.SeedType.FIXED, false);
                    ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                    lobby = new RoomLobbyScreen(new TitleScreen(), ROOM);
                    client.openScreen(lobby);
                    break;
                case 1:
                    capture(client, "lobby-rules-desktop.png");
                    click(client, "Game Rules");
                    break;
                case 2:
                    capture(client, "rules-host-desktop.png");
                    click(client, "Off");
                    require(!ZsgRooms.getGame(ROOM).areCheatsAllowed(), "Draft applied before Done");
                    click(client, "Done");
                    require(ZsgRooms.getGame(ROOM).areCheatsAllowed(), "Host edit did not save");
                    click(client, "Game Rules");
                    require(find(client, "On").active, "Saved rules did not reopen");
                    click(client, "Done");
                    ZsgRooms.getRoom(ROOM).host = new Player("OtherHost", true, true);
                    click(client, "Game Rules");
                    break;
                case 3:
                    capture(client, "rules-guest-desktop.png");
                    require(!find(client, "On").active && !find(client, "Off").active, "Guest can edit");
                    require(find(client, "?").active, "Read-only help disabled");
                    RoomRuleSettings update = RoomRuleSettings.capture(ZsgRooms.getGame(ROOM));
                    update.allowCheats = false;
                    require(ZsgRooms.changeRoomRules(ROOM, "OtherHost", update.toJson()), "Host update failed");
                    break;
                case 4:
                    require(find(client, "On") == null, "Guest screen did not refresh");
                    click(client, "Done");
                    require(!ZsgRooms.getGame(ROOM).areCheatsAllowed(), "Guest close overwrote host");
                    ZsgRooms.getRoom(ROOM).host = new Player(host, true, true);
                    click(client, "Game Rules");
                    click(client, "Off");
                    ZsgRooms.getGame(ROOM).setInGame(true);
                    click(client, "Done");
                    require(!ZsgRooms.getGame(ROOM).areCheatsAllowed(), "Stale screen changed active race");
                    ZsgRooms.getGame(ROOM).endGame();
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 5:
                    capture(client, "lobby-rules-small.png");
                    click(client, "Rules");
                    break;
                case 6:
                    capture(client, "rules-host-small.png");
                    click(client, "World & Performance");
                    stage = 10;
                    break;
                case 10:
                    capture(client, "rules-world-small.png");
                    click(client, "Done");
                    client.openScreen(new MatchHudSettingsScreen(lobby));
                    stage = 7;
                    break;
                case 7:
                    while (slider(client) == null) click(client, ">");
                    capture(client, "seed-header-slider-small.png");
                    SliderWidget slider = slider(client);
                    slider.mouseClicked(slider.x + slider.getWidth() - 5, slider.y + 5, 0);
                    slider.mouseReleased(slider.x + slider.getWidth() - 5, slider.y + 5, 0);
                    require(RoomUiPreferences.getMatchHud().seedTypeScale == 150, "Slider maximum failed");
                    click(client, "Done");
                    client.openScreen(new MatchHudSettingsScreen(lobby));
                    require(RoomUiPreferences.getMatchHud().seedTypeScale == 150, "Size not retained");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    break;
                case 8:
                    capture(client, "seed-header-large.png");
                    RoomUiPreferences.getMatchHud().seedTypeScale = 50;
                    break;
                case 9:
                    capture(client, "seed-header-small.png");
                    RoomUiPreferences.getMatchHud().seedTypeScale = 100;
                    click(client, "Done");
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: lobby host edits, guest view/live updates, race guard, header scaling and layouts");
                    finished = true;
                    client.scheduleStop();
                    break;
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[LobbyRulesSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private static SliderWidget slider(MinecraftClient client) {
        for (Element element : client.currentScreen.children()) {
            if (element instanceof SliderWidget && ((SliderWidget) element).getMessage().getString().startsWith("Seed header size:")) {
                return (SliderWidget) element;
            }
        }
        return null;
    }

    private static ButtonWidget find(MinecraftClient client, String label) {
        for (Element element : client.currentScreen.children()) {
            if (element instanceof ButtonWidget && ((ButtonWidget) element).getMessage().getString().equals(label)) return (ButtonWidget) element;
        }
        return null;
    }

    private static void click(MinecraftClient client, String label) {
        ButtonWidget button = find(client, label);
        require(button != null && button.active, "Missing/disabled button: " + label);
        button.onPress();
    }

    private static void capture(MinecraftClient client, String name) {
        Screen screen = client.currentScreen;
        for (Element element : screen.children()) {
            if (!(element instanceof ButtonWidget)) continue;
            ButtonWidget button = (ButtonWidget) element;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= screen.width
                    && button.y + button.getHeight() <= screen.height, "Control outside viewport: " + button.getMessage().getString());
            require(client.textRenderer.getWidth(button.getMessage()) <= button.getWidth() - 2,
                    "Label overflow: " + button.getMessage().getString());
        }
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
