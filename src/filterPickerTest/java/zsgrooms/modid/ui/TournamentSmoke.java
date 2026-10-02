package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.*;
import zsgrooms.modid.net.RoomSnapshot;
import java.util.*;

final class TournamentSmoke {
    private int stage, ticks;
    private boolean done;
    private static final String ROOM = "tournament-smoke";
    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient client) {
        if (done || client.getOverlay() != null || ++ticks < 25) return;
        ticks = 0;
        try {
            switch (stage++) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.maxFps = 60; client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    Room room = new Room(ROOM, "123|structure:zsg", new Player(ZsgRoomsClient.localPlayerName(client), true, true), 8);
                    for (String name : Arrays.asList("A", "B", "C", "D", "E")) room.addPlayer(new Player(name, true, false));
                    InGame game = new InGame(room.seed, ROOM, InGame.SeedType.FIXED, false);
                    require(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()), "Setup snapshot");
                    client.openScreen(new RoomOptionsScreen(new TitleScreen(), ROOM));
                    click(client, "Tournament");
                    break;
                case 1:
                    for (Element child : client.currentScreen.children()) if (child instanceof CheckboxWidget) ((CheckboxWidget) child).onPress();
                    fields(client).get(0).setText("4");
                    fields(client).get(1).setText("12,8,4,1");
                    capture(client, "tournament-points-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 2:
                    capture(client, "tournament-points-small.png");
                    require(fields(client).get(0).getText().equals("4"), "Rounds lost on resize");
                    require(fields(client).get(1).getText().equals("12,8,4,1"), "Points lost on resize");
                    click(client, "Bracket"); click(client, "Best of 1");
                    break;
                case 3:
                    capture(client, "tournament-bracket-settings-small.png");
                    click(client, "Players and Byes");
                    break;
                case 4:
                    int selected = 0;
                    for (Element child : new ArrayList<>(client.currentScreen.children())) if (child instanceof CheckboxWidget && selected++ < 2) ((CheckboxWidget) child).onPress();
                    break;
                case 5:
                    capture(client, "tournament-byes-small.png");
                    click(client, "Done"); click(client, "Standings / Bracket");
                    moveCursor(client, 0, 0);
                    break;
                case 6:
                    capture(client, "tournament-bracket-preview-small.png");
                    require(number(client.currentScreen, "zoom") * new TournamentBracketLayout(6).width() <= client.currentScreen.width - 32 + 1, "Small bracket not fitted");
                    click(client, "Back");
                    hover(client, "Best of 3");
                    break;
                case 7:
                    capture(client, "tournament-bestof-tooltip-small.png");
                    click(client, "Apply");
                    TournamentSettings settings = ZsgRooms.getGame(ROOM).getTournamentSettings();
                    require(settings.enabled && settings.bracket && settings.bestOf == 3 && settings.byes.size() == 2, "Setup not applied");
                    require(settings.rounds == 4 && settings.points.equals(Arrays.asList(12,8,4,1)), "Points draft lost");
                    client.openScreen(new RoomLobbyScreen(new TitleScreen(), ROOM));
                    break;
                case 8:
                    capture(client, "tournament-lobby-small.png");
                    InGame active = ZsgRooms.getGame(ROOM);
                    Tournament tournament = new Tournament(active.getTournamentSettings());
                    active.setTournament(tournament); active.startGame();
                    active.setSequence(new RaceSequence(active.getRaceId(), Arrays.asList(active.seed), tournament.participants()));
                    tournament.beginRace(active.getRaceId());
                    String payload = TournamentRaceClient.launchPayload(ROOM);
                    Object screen = client.currentScreen;
                    ZsgRooms.applyRoomAction("launch", ROOM, "Host", payload);
                    require(client.currentScreen == screen && client.world == null, "Waiting player loaded a world");
                    require(!TournamentRaceClient.participates(ROOM), "Waiting host entered current pair");
                    ZsgRooms.applyRoomAction("launch", ROOM, "Host", payload);
                    require(client.currentScreen == screen, "Duplicate launch changed screen");
                    client.openScreen(new TournamentStandingsScreen(client.currentScreen, ROOM, null));
                    break;
                case 9:
                    capture(client, "tournament-standings-small.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    client.openScreen(new TournamentStandingsScreen(new TitleScreen(), ROOM, demo(8, true)));
                    moveCursor(client, 0, 0);
                    break;
                case 10:
                    capture(client, "tournament-bracket-desktop.png");
                    click(client, "+");
                    client.currentScreen.mouseClicked(180, 120, 0);
                    require(client.currentScreen.mouseDragged(80, 80, 0, -100, -40), "Bracket drag rejected");
                    client.currentScreen.mouseReleased(80, 80, 0);
                    break;
                case 11:
                    capture(client, "tournament-bracket-zoomed.png");
                    client.openScreen(new TournamentStandingsScreen(new TitleScreen(), ROOM, demo(64, true)));
                    client.currentScreen.mouseScrolled(100, 100, -10000);
                    for (int i = 0; i < 40; i++) client.currentScreen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                    break;
                case 12:
                    capture(client, "tournament-bracket-large-scrolled.png");
                    click(client, "Fit");
                    require(number(client.currentScreen, "panX") == 0 && number(client.currentScreen, "panY") == 0, "Fit retained pan");
                    require(number(client.currentScreen, "zoom") * new TournamentBracketLayout(64).height() <= client.currentScreen.height - 116 + 1, "Large bracket not fitted");
                    client.openScreen(new TournamentStandingsScreen(new TitleScreen(), ROOM, demo(8, false)));
                    break;
                case 13:
                    capture(client, "tournament-points-table-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 14:
                    client.currentScreen.mouseScrolled(100, 100, -100);
                    break;
                case 15:
                    capture(client, "tournament-points-table-small.png");
                    done = true;
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: tournament setup, explicit byes, bracket connectors and future rounds, zoom, pan, 64-player scrolling, points table, tooltips, small/desktop layout, spectator launch and duplicate launch");
                    client.scheduleStop();
                    break;
            }
        } catch (Throwable error) {
            done = true; ZsgRooms.LOGGER.error("[TournamentSmoke] FAILED", error); client.scheduleStop();
        }
    }
    private static double number(Object object, String name) throws Exception {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(object);
    }
    private static void moveCursor(MinecraftClient client, double x, double y) throws Exception {
        java.lang.reflect.Method cursor = client.mouse.getClass().getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
        cursor.setAccessible(true);
        cursor.invoke(client.mouse, client.getWindow().getHandle(), x, y);
    }
    private static Tournament demo(int count, boolean bracket) {
        TournamentSettings settings = new TournamentSettings();
        settings.enabled = true; settings.bracket = bracket; settings.bestOf = 3;
        for (int i = 0; i < count; i++) settings.order.add(i == 0 ? "LongPlayerName123" : "Runner" + (i + 1));
        Tournament tournament = new Tournament(settings);
        if (bracket) {
            for (int i = 0; i < 3; i++) {
                RaceSequence race = new RaceSequence(UUID.randomUUID().toString(), Arrays.asList("123"), tournament.participants(), 2);
                tournament.beginRace(race.raceId);
                String winner = tournament.participants().get(0);
                require(race.submit(winner, RaceSequence.report(race.raceId, 0, 1000, "finish")), "Demo finish");
                race.withdraw(tournament.participants().get(1));
                require(tournament.record(race), "Demo score");
            }
        } else {
            int score = 100;
            for (String name : settings.order) { tournament.scores.put(name, score); score -= 10; }
            tournament.withdrawn.add(settings.order.get(count - 1));
        }
        return tournament;
    }
    private static List<TextFieldWidget> fields(MinecraftClient client) {
        List<TextFieldWidget> fields = new ArrayList<>();
        for (Element child : client.currentScreen.children()) if (child instanceof TextFieldWidget) fields.add((TextFieldWidget) child);
        return fields;
    }
    private static void click(MinecraftClient client, String label) {
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (button.getMessage().getString().equals(label) && button.active) { button.onPress(); return; }
        }
        throw new IllegalStateException("Missing button " + label);
    }
    private static void hover(MinecraftClient client, String label) throws Exception {
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (!button.getMessage().getString().equals(label)) continue;
            java.lang.reflect.Method cursor = client.mouse.getClass().getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
            cursor.setAccessible(true);
            cursor.invoke(client.mouse, client.getWindow().getHandle(), (button.x + 8) * client.getWindow().getScaleFactor(), (button.y + 8) * client.getWindow().getScaleFactor());
        }
    }
    private static void capture(MinecraftClient client, String name) {
        for (Element child : client.currentScreen.children()) if (child instanceof AbstractButtonWidget) {
            AbstractButtonWidget button = (AbstractButtonWidget) child;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= client.currentScreen.width
                    && button.y + button.getHeight() <= client.currentScreen.height, "Control outside viewport");
            if (button instanceof ButtonWidget) require(client.textRenderer.getWidth(button.getMessage()) <= button.getWidth() - 2, "Label overflow");
        }
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
