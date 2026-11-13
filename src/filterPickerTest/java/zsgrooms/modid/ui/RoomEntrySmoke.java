package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.net.RoomWebSocketTransport;

import java.util.ArrayList;
import java.util.List;

/** Actual, muted Minecraft previews. No relay requests, rooms or clipboard writes. */
final class RoomEntrySmoke {
    private final String captureId = Long.toString(System.currentTimeMillis());
    private final Screen parent = new Screen(new LiteralText("Entry preview")) {};
    private int ticks, stage;
    private boolean done;

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (done || client.getOverlay() != null || ++ticks < 15) return;
        ticks = 0;
        try {
            switch (stage++) {
                case 0:
                    client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    open(client); break;
                case 1:
                    capture(client, "desktop"); exercise(client);
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    open(client); break;
                case 2:
                    capture(client, "small"); exercise(client);
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1920, 1080);
                    open(client); break;
                case 3:
                    capture(client, "wide"); exercise(client);
                    require(ZsgRooms.getActiveRoom() == null, "Preview created a room");
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(client.currentScreen == parent, "Back did not return to parent");
                    interruptedConnection(client, false);
                    interruptedConnection(client, true);
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: join and connection screens at three sizes; code validation, code preserved across settings, invalid relay not saved, Back, interrupted connection recovery; previews do not connect or create rooms");
                    done = true; client.scheduleStop(); break;
            }
        } catch (Throwable error) {
            done = true; ZsgRooms.LOGGER.error("[RoomEntrySmoke] FAILED", error); client.scheduleStop();
        }
    }

    private void open(MinecraftClient client) {
        RoomEntryScreen screen = RoomEntryScreen.forTutorial(parent);
        client.openScreen(screen);
        for (Element element : screen.children()) {
            if (element instanceof TextFieldWidget) ((TextFieldWidget) element).setText("W9K7Q2F8");
            else if (element instanceof RoomEntryButton && ((RoomEntryButton) element).icon == RoomEntryButton.Icon.EYE)
                ((RoomEntryButton) element).onPress();
        }
    }

    private void exercise(MinecraftClient client) {
        Screen screen = client.currentScreen;
        TextFieldWidget field = field(screen);
        field.setText("abc"); require(!button(screen, "Join").active, "Short code allowed");
        field.setText("W9K7Q2F8"); require(button(screen, "Join").active, "Valid code disabled");
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        require(client.currentScreen == screen && !RoomWebSocketTransport.isActive(), "Preview joined a relay");
        String previousRelay = RoomWebSocketTransport.getRelayUrl();
        button(screen, "Connection settings").onPress();
        require(client.currentScreen instanceof RelaySettingsScreen, "Connection settings missing");
        validateWidgets(client);
        field(client.currentScreen).setText("https://");
        button(client.currentScreen, "Apply").onPress();
        require(client.currentScreen instanceof RelaySettingsScreen, "Invalid address was accepted");
        require(previousRelay.equals(RoomWebSocketTransport.getRelayUrl()), "Invalid address changed relay");
        button(client.currentScreen, "Use default").onPress();
        require(field(client.currentScreen).getText().equals(RoomWebSocketTransport.DEFAULT_RELAY_URL), "Default address incorrect");
        client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(client.currentScreen == screen && field(screen).getText().equals("W9K7Q2F8"), "Settings return lost code");
        require(previousRelay.equals(RoomWebSocketTransport.getRelayUrl()), "Back saved an unapplied address");
    }

    private static TextFieldWidget field(Screen screen) {
        for (Element element : screen.children()) if (element instanceof TextFieldWidget) return (TextFieldWidget) element;
        throw new AssertionError("Missing text field");
    }

    private void interruptedConnection(MinecraftClient client, boolean creating) throws Exception {
        RoomEntryScreen screen = new RoomEntryScreen(parent, creating);
        client.openScreen(screen);
        if (!creating) field(screen).setText("W9K7Q2F8");
        java.lang.reflect.Field state = RoomEntryScreen.class.getDeclaredField("state");
        state.setAccessible(true);
        for (Object value : state.getType().getEnumConstants()) {
            if (value.toString().equals("CONNECTING")) state.set(screen, value);
        }
        // Simulate a mod replacing the screen before the next client tick, without networking.
        client.openScreen(parent);
        require(state.get(screen).toString().equals("ERROR"), "Interrupted connection left a stuck screen");
        client.openScreen(screen);
        require(button(screen, "Retry").active, "Interrupted connection has no retry");
        validateWidgets(client);
        screen.onClose();
        if (!creating) {
            require(client.currentScreen == screen && field(screen).getText().equals("W9K7Q2F8"), "Interrupted join lost its code");
            screen.onClose();
        }
        require(client.currentScreen == parent && !RoomWebSocketTransport.isActive(), "Interrupted connection did not close");
    }

    private static RoomEntryButton button(Screen screen, String label) {
        for (Element element : screen.children()) if (element instanceof RoomEntryButton && ((RoomEntryButton) element).getMessage().getString().equals(label))
            return (RoomEntryButton) element;
        throw new AssertionError("Missing button: " + label);
    }

    private void capture(MinecraftClient client, String name) {
        validateWidgets(client);
        ScreenshotUtils.saveScreenshot(client.runDirectory, "join-screen-" + captureId + "-" + name + ".png",
                client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> {});
    }

    private void validateWidgets(MinecraftClient client) {
        Screen screen = client.currentScreen;
        List<AbstractButtonWidget> widgets = new ArrayList<>();
        for (Element element : screen.children()) if (element instanceof AbstractButtonWidget) {
            AbstractButtonWidget b = (AbstractButtonWidget) element;
            require(b.x >= 0 && b.y >= 0 && b.x + b.getWidth() <= screen.width && b.y + b.getHeight() <= screen.height, "Control outside viewport");
            if (b instanceof RoomEntryButton && ((RoomEntryButton) b).icon == RoomEntryButton.Icon.NONE)
                require(client.textRenderer.getWidth(b.getMessage()) <= b.getWidth() - 8, "Button label overflow");
            for (AbstractButtonWidget other : widgets) require(!overlaps(b, other), "Overlapping controls");
            widgets.add(b);
        }
    }

    private static boolean overlaps(AbstractButtonWidget a, AbstractButtonWidget b) {
        return a.x < b.x + b.getWidth() && b.x < a.x + a.getWidth() && a.y < b.y + b.getHeight() && b.y < a.y + a.getHeight();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
