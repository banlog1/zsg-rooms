package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Player;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.net.RoomSnapshot;
import zsgrooms.modid.net.RoomWebSocketTransport;
import zsgrooms.modid.replay.ReplayPreferences;
import zsgrooms.modid.replay.ReplayPrototype;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static zsgrooms.modid.ui.TutorialOverlay.Box;

/** Muted end-to-end onboarding checks against the production screens. */
final class TutorialSmoke {
    private static final String SENTINEL = "TUTORIAL-SENTINEL";
    private final String captureId = Long.toString(System.currentTimeMillis());
    private int stage, ticks;
    private boolean done;
    private String roomBefore, activeBefore;
    private Map<Path, byte[]> configBefore;

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (done || client.getOverlay() != null || ++ticks < 15) return;
        ticks = 0;
        try {
            switch (stage++) {
                case 0:
                    require(client.currentScreen instanceof TutorialScreen, "First launch did not open tutorial prompt");
                    require(TutorialOnboarding.preferences().shouldPrompt(), "Showing the prompt must not save a choice");
                    client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    Room room = new Room(SENTINEL, "123|structure:rooms-mix", new Player(client.getSession().getUsername(), true, true), 4);
                    InGame game = new InGame(room.seed, SENTINEL, InGame.SeedType.FIXED, false);
                    ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                    roomBefore = snapshot(); activeBefore = ZsgRooms.getActiveRoomName();
                    configBefore = configSnapshot();
                    break;
                case 1:
                    capture(client, "welcome");
                    press(client, "No");
                    require(client.currentScreen instanceof TitleScreen, "No did not return to title");
                    TutorialOnboarding.tick(client);
                    require(client.currentScreen instanceof TitleScreen, "Prompt reappeared after No");
                    require(reload().getChoice() == TutorialPreferences.Choice.NONE, "No was not saved");
                    TutorialOnboarding.open(client.currentScreen);
                    press(client, "A quick one");
                    require(screen(client).steps() == 8 && !screen(client).full, "Quick choice failed");
                    for (int i = 0; i < 8; i++) press(client, i == 7 ? "Finish" : "Next");
                    require(client.currentScreen instanceof TitleScreen, "Quick completion did not return");
                    require(reload().getChoice() == TutorialPreferences.Choice.QUICK, "Quick was not saved");
                    TutorialOnboarding.open(client.currentScreen);
                    press(client, "Yes, full");
                    require(screen(client).steps() == 21 && screen(client).full, "Full choice failed");
                    require(reload().getChoice() == TutorialPreferences.Choice.FULL, "Full was not saved");
                    screen(client).showStep(1);
                    break;
                case 2: capture(client, "settings"); screen(client).showStep(7); break;
                case 3:
                    capture(client, "room");
                    exerciseAll(client);
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    open(client, -1);
                    break;
                case 4:
                    capture(client, "welcome-small"); screen(client).full = true; screen(client).showStep(15);
                    clickTarget(screen(client)); break;
                case 5:
                    capture(client, "recording-options-small"); select(screen(client), "Super Detailed"); break;
                case 6:
                    capture(client, "recording-small"); screen(client).showStep(16); clickTarget(screen(client)); break;
                case 7:
                    capture(client, "camera-options-small"); select(screen(client), "240 Hz (advanced)"); break;
                case 8:
                    capture(client, "camera-small");
                    exerciseAll(client);
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1920, 1080);
                    open(client, 10);
                    break;
                case 9:
                    capture(client, "race-format-wide");
                    exerciseAll(client);
                    open(client, 15); clickTarget(screen(client)); select(screen(client), "Detailed"); break;
                case 10:
                    capture(client, "recording-wide");
                    require(snapshot().equals(roomBefore), "Tutorial mutated the existing room");
                    require(java.util.Objects.equals(activeBefore, ZsgRooms.getActiveRoomName()), "Tutorial changed active room");
                    require(!RoomWebSocketTransport.hasRelayRoom(SENTINEL), "Tutorial connected a relay");
                    Map<Path, byte[]> after = configSnapshot();
                    require(after.keySet().equals(configBefore.keySet()), "Tutorial created/deleted other configuration files");
                    for (Path p : after.keySet()) require(Arrays.equals(configBefore.get(p), after.get(p)), "Tutorial changed " + p);
                    Screen settings = new RoomSettingsScreen(new TitleScreen());
                    client.openScreen(settings);
                    press(client, "Open tutorial");
                    require(client.currentScreen instanceof TutorialScreen, "Settings entry failed");
                    screen(client).keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(client.currentScreen == settings, "Close did not restore Settings");
                    require(reload().getChoice() == TutorialPreferences.Choice.FULL, "Closing reopened prompt erased preference");
                    ZsgRooms.leaveRoomLocally(SENTINEL);
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: production tutorial, first-launch persistence, quick/full completion, interactive options and toggles, keyboard menus, settings reopening and all option layouts at three sizes; existing room/config unchanged");
                    done = true; client.scheduleStop(); break;
            }
        } catch (Throwable error) {
            done = true; ZsgRooms.LOGGER.error("[TutorialSmoke] FAILED", error); client.scheduleStop();
        }
    }

    private static TutorialPreferences reload() {
        return new TutorialPreferences(FabricLoader.getInstance().getConfigDir().resolve("zsg-rooms-tutorial.properties"));
    }
    private static String snapshot() { return RoomSnapshot.capture(ZsgRooms.getRoom(SENTINEL), ZsgRooms.getGame(SENTINEL)).toJson(); }
    private static Map<Path, byte[]> configSnapshot() throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        Path config = FabricLoader.getInstance().getConfigDir();
        if (!Files.isDirectory(config)) return result;
        try (Stream<Path> paths = Files.walk(config)) {
            for (Path p : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                if (!p.getFileName().toString().equals("zsg-rooms-tutorial.properties")) result.put(p, Files.readAllBytes(p));
            }
        }
        return result;
    }

    private static void exerciseAll(MinecraftClient client) {
        ReplayPreferences recordingBefore = ReplayPrototype.getPreferences();
        Screen parent = new TitleScreen();
        TutorialScreen s = new TutorialScreen(parent, TutorialOnboarding.preferences()); s.full = true;
        client.openScreen(s);
        for (int step = 0; step < s.steps(); step++) {
            s.showStep(step); check(s);
            s.mouseClicked(0, 0, 0); s.mouseScrolled(0, 0, -1); s.charTyped('a', 0);
            require(s.step == step, "Background input advanced the tour");
            if (s.hasPracticeControl()) exercisePractice(client, s);
            else clickTarget(s);
            require(step == s.steps() - 1 ? client.currentScreen == parent : s.step == step + 1, "Highlighted click failed");
        }
        client.openScreen(s); s.showStep(1);
        press(client, "Back"); require(s.step == 0, "Back failed");
        s.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        require(s.step == 0, "Enter activated a removed Back button");
        require(s.getFocused() == null || s.children().contains(s.getFocused()), "Focus belongs to a removed control");
        press(client, "Next"); require(s.step == 1, "Next failed");
        s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); require(client.currentScreen == parent, "Escape failed");
        client.openScreen(s); s.showStep(1);
        press(client, "Close tour"); require(client.currentScreen == parent, "Close failed");
        require(ReplayPrototype.getPreferences() == recordingBefore, "Tutorial changed in-memory recording preferences");
    }

    private static void exercisePractice(MinecraftClient client, TutorialScreen s) {
        int step = s.step;
        clickTarget(s);
        require(s.step == step, "Practice control advanced the tour");
        check(s);
        if (step == 14 || step == 17) {
            require(((CheckboxWidget) practiceControl(s)).isChecked(), "Practice checkbox did not turn on");
            clickTarget(s); check(s);
            require(!((CheckboxWidget) practiceControl(s)).isChecked(), "Practice checkbox did not turn off");
            clickTarget(s);
            press(client, "Next"); press(client, "Back");
            require(((CheckboxWidget) practiceControl(s)).isChecked(), "Back lost practice toggle");
        } else {
            List<String> labels = new ArrayList<>();
            for (ButtonWidget b : s.optionButtons) labels.add(b.getMessage().getString());
            require(labels.size() >= 3, "Choice menu did not show options");
            for (String label : labels) {
                if (s.optionMenu == null) clickTarget(s);
                check(s); select(s, label); check(s);
                require(s.step == step && s.optionMenu == null, "Selection did not return to its step");
                require(s.getFocused() == null || s.children().contains(s.getFocused()), "Selection kept stale keyboard focus");
                if (step != 9) require(practiceControl(s).getMessage().getString().startsWith(label), "Selected value not shown on control");
            }
            String selected = practiceControl(s).getMessage().getString();
            press(client, "Next"); press(client, "Back"); check(s);
            require(practiceControl(s).getMessage().getString().equals(selected), "Back lost selected option");
            clickTarget(s); s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == s && s.optionMenu == null, "Escape should close only the menu");
            clickTarget(s); press(client, "Next");
            require(s.step == step && s.optionMenu == null, "Outside click activated a covered button");
            clickTarget(s); s.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0); s.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            s.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); check(s);
            if (step != 9) require(practiceControl(s).getMessage().getString().startsWith(labels.get(1)), "Keyboard selection failed");
            selected = practiceControl(s).getMessage().getString();
            clickTarget(s); s.init(client, s.width, s.height); check(s);
            require(s.optionMenu == null && practiceControl(s).getMessage().getString().equals(selected), "Resize lost selection or kept stale menu");
        }
        press(client, "Next");
    }

    private static AbstractButtonWidget practiceControl(TutorialScreen s) {
        for (Element e : s.children()) if (e instanceof AbstractButtonWidget) {
            AbstractButtonWidget b = (AbstractButtonWidget) e;
            if (b.x == s.target.x && b.y == s.target.y) return b;
        }
        throw new AssertionError("Missing interactive target");
    }
    private static void clickTarget(TutorialScreen s) {
        Box t = s.target;
        s.mouseClicked(t.x + t.width / 2D, t.y + t.height / 2D, 0);
    }
    private static void select(TutorialScreen s, String label) {
        for (ButtonWidget b : s.optionButtons) if (b.getMessage().getString().equals(label)) {
            s.mouseClicked(b.x + b.getWidth() / 2D, b.y + b.getHeight() / 2D, 0); return;
        }
        throw new AssertionError("Missing option: " + label);
    }
    private static TutorialScreen open(MinecraftClient client, int step) {
        TutorialScreen screen = new TutorialScreen(new TitleScreen(), TutorialOnboarding.preferences());
        screen.full = true; screen.step = step; client.openScreen(screen); return screen;
    }
    private static TutorialScreen screen(MinecraftClient client) { return (TutorialScreen) client.currentScreen; }
    private static void press(MinecraftClient client, String label) {
        for (Element e : client.currentScreen.children()) if (e instanceof ButtonWidget) {
            ButtonWidget b = (ButtonWidget) e;
            if (b.getMessage().getString().equals(label)) {
                require(b.active, "Disabled: " + label);
                client.currentScreen.mouseClicked(b.x + b.getWidth() / 2D, b.y + b.getHeight() / 2D, 0);
                return;
            }
        }
        throw new AssertionError("Missing button " + label);
    }
    private static void check(TutorialScreen s) {
        Box p = s.panel;
        require(p.x >= 0 && p.y >= 0 && p.x + p.width <= s.width && p.y + p.height <= s.height, "Panel outside viewport");
        if (s.step >= 0) {
            Box t = s.target;
            require(t != null, "Missing target at " + s.step);
            require(t.x >= 0 && t.y >= 0 && t.x + t.width <= s.width && t.y + t.height <= s.height, "Target outside viewport " + s.step);
            require(!overlaps(t, p), "Panel covers target " + s.step);
        }
        List<Box> boxes = new ArrayList<>();
        for (Element e : s.children()) if (e instanceof AbstractButtonWidget) {
            AbstractButtonWidget b = (AbstractButtonWidget) e;
            Box box = new Box(b.x, b.y, b.getWidth(), b.getHeight());
            require(contains(p, box) || s.hasPracticeControl() && contains(s.target, box), "Button outside panel/target");
            if (!(b instanceof CheckboxWidget) && !b.getMessage().getString().equals("Close tour")) require(MinecraftClient.getInstance().textRenderer.getWidth(b.getMessage()) <= b.getWidth() - 8, "Label overflow");
            for (Box other : boxes) require(!overlaps(box, other), "Buttons overlap");
            boxes.add(box);
        }
        if (s.optionMenu != null) {
            require(contains(new Box(1, 1, s.width - 2, s.height - 2), s.optionMenu), "Menu outside viewport");
            List<Box> options = new ArrayList<>();
            for (ButtonWidget b : s.optionButtons) {
                Box box = new Box(b.x, b.y, b.getWidth(), b.getHeight());
                require(contains(s.optionMenu, box), "Option outside menu");
                require(MinecraftClient.getInstance().textRenderer.getWidth(b.getMessage()) <= b.getWidth() - 26, "Option label overflow");
                for (Box other : options) require(!overlaps(box, other), "Options overlap");
                options.add(box);
            }
        }
        require(s.textFits(), "Truncated tutorial at " + s.step + " in " + s.width + "x" + s.height);
    }
    private static boolean overlaps(Box a, Box b) { return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height; }
    private static boolean contains(Box a, Box b) { return a != null && b.x >= a.x && b.y >= a.y && b.x + b.width <= a.x + a.width && b.y + b.height <= a.y + a.height; }
    private void capture(MinecraftClient client, String name) {
        check(screen(client));
        ScreenshotUtils.saveScreenshot(client.runDirectory, "tutorial-build-" + captureId + "-" + name + ".png",
                client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
