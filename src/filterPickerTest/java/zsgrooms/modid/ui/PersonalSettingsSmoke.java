package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.replay.ReplayPrototype;
import zsgrooms.modid.seedbank.SeedBankClient;
import java.lang.reflect.Field;
import java.util.*;

/** Isolated UI checks; never packaged with the mod. */
final class PersonalSettingsSmoke {
    private int stage, ticks;
    private boolean done, previousRepair;
    private int savedOffset;
    private String endpoint;

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (done || client.getOverlay() != null || ++ticks < 15) return;
        ticks = 0;
        try {
            switch (stage++) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.guiScale = 2; client.options.maxFps = 60;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    previousRepair = RoomUiPreferences.isRuinedPortalRepairTestingEnabled();
                    endpoint = SeedBankClient.getEndpoint();
                    client.openScreen(new RoomSettingsScreen(new TitleScreen()));
                    break;
                case 1:
                    check(client);
                    selectCategory(client, "HUD");
                    break;
                case 2:
                    check(client); capture(client, "settings-hud-desktop.png");
                    pressRow(client, "HUD preview");
                    require(client.currentScreen instanceof MatchHudSettingsScreen, "HUD preview missing");
                    client.currentScreen.onClose();
                    require(page(client).equals("HUD"), "HUD return lost category");
                    selectCategory(client, "Advanced");
                    if (!RoomUiPreferences.isRuinedPortalRepairTestingEnabled()) pressRow(client, "RP repair on all seeds");
                    selectCategory(client, "General");
                    require(((ButtonWidget) value(client.currentScreen, "testingBadge")).visible, "Testing badge hidden outside Advanced");
                    break;
                case 3:
                    capture(client, "settings-testing-warning.png");
                    selectCategory(client, "Recording");
                    require(!control(client, "Current recording").active, "Stop enabled without recording");
                    boolean before = ReplayPrototype.getPreferences().performanceMode;
                    pressRow(client, "Performance mode");
                    require(ReplayPrototype.getPreferences().performanceMode != before, "Performance setting did not save");
                    pressRow(client, "Performance mode");
                    require(ReplayPrototype.getPreferences().performanceMode == before, "Performance restore failed");
                    break;
                case 4:
                    capture(client, "settings-recording-desktop.png");
                    selectCategory(client, "Advanced"); pressRow(client, "Replay libraries");
                    ((TextFieldWidget) control(client, "Library folder override")).setText("draft-library-folder");
                    client.currentScreen.onClose(); pressRow(client, "Seed-bank service");
                    ((TextFieldWidget) control(client, "Service URL")).setText("http://127.0.0.1:8799");
                    selectCategory(client, "HUD");
                    control(client, "HUD preview"); savedOffset = (Integer) value(client.currentScreen, "offset");
                    selectCategory(client, "Loading");
                    choose(client, "Progress position", "Bottom Right");
                    require(RoomUiPreferences.getLoadingProgressPosition() == LoadingProgressPosition.BOTTOM_RIGHT, "Position did not save");
                    break;
                case 5:
                    capture(client, "settings-loading-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 6:
                    check(client);
                    require(page(client).equals("LOADING"), "Resize lost category");
                    choose(client, "Progress style", "ZSG");
                    require(RoomUiPreferences.getLoadingIndicatorStyle() == LoadingIndicatorStyle.ZSG, "Style menu failed");
                    // Clicking Done outside the menu must dismiss the menu, not close the screen.
                    click(client, find(client, "Loading  v"));
                    client.currentScreen.mouseClicked(client.currentScreen.width / 2.0, client.currentScreen.height - 18, 0);
                    require(client.currentScreen instanceof RoomSettingsScreen, "Menu click leaked into Done");
                    click(client, find(client, "Loading  v"));
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(client.currentScreen instanceof RoomSettingsScreen, "Escape closed settings after outside menu click");
                    break;
                case 7:
                    selectCategory(client, "Advanced"); pressRow(client, "Seed-bank service");
                    require(((TextFieldWidget) control(client, "Service URL")).getText().equals("http://127.0.0.1:8799"), "Service draft lost");
                    require(SeedBankClient.getEndpoint().equals(endpoint), "Unsaved URL was applied");
                    ((TextFieldWidget) control(client, "Service URL")).setText("invalid-url");
                    pressRow(client, "Service configuration");
                    require(value(client.currentScreen, "status").toString().startsWith("Invalid URL"), "Invalid URL not reported");
                    require(SeedBankClient.getEndpoint().equals(endpoint), "Invalid URL changed configuration");
                    client.currentScreen.onClose(); pressRow(client, "Replay libraries");
                    require(((TextFieldWidget) control(client, "Library folder override")).getText().equals("draft-library-folder"), "Library draft lost");
                    client.currentScreen.onClose(); pressRow(client, "Solo replay testing");
                    ((TextFieldWidget) control(client, "Test group ID")).setText("not-a-uuid");
                    client.currentScreen.tick();
                    require(!control(client, "Test grouping").active, "Invalid test group can be applied");
                    selectCategory(client, "HUD");
                    require((Integer) value(client.currentScreen, "offset") == savedOffset, "Category scroll lost");
                    break;
                case 8:
                    check(client); capture(client, "settings-hud-small.png");
                    selectCategory(client, "Recording");
                    control(client, "Current recording");
                    break;
                case 9:
                    check(client); capture(client, "settings-recording-small.png");
                    // Keyboard selection on the compact category menu.
                    click(client, find(client, "Recording  v"));
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    require(page(client).equals("ADVANCED"), "Keyboard category selection failed");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    break;
                case 10:
                    check(client);
                    selectCategory(client, "Advanced");
                    break;
                case 11:
                    capture(client, "settings-advanced-desktop.png");
                    RoomUiPreferences.setRuinedPortalRepairTestingEnabled(previousRepair);
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: personal settings categories, menus, scrolling, resize, draft retention, validation, disabled controls, testing badge and HUD preview return");
                    done = true; client.scheduleStop();
                    break;
            }
        } catch (Throwable error) {
            RoomUiPreferences.setRuinedPortalRepairTestingEnabled(previousRepair);
            done = true; ZsgRooms.LOGGER.error("[PersonalSettingsSmoke] FAILED", error); client.scheduleStop();
        }
    }

    static void selectCategory(MinecraftClient client, String label) throws Exception {
        if (page(client).equals(label.toUpperCase(Locale.ROOT))) return;
        ButtonWidget button = find(client, label);
        if (button != null) click(client, button);
        else {
            String category = ((RoomSettingsScreen.Page) value(client.currentScreen, "page")).category().label;
            click(client, find(client, category + "  v"));
            menu(client, label);
        }
    }

    static void choose(MinecraftClient client, String label, String option) throws Exception {
        click(client, control(client, label)); menu(client, option);
    }

    private static void menu(MinecraftClient client, String label) throws Exception {
        for (Object object : (List<?>) value(client.currentScreen, "menu")) {
            ButtonWidget button = (ButtonWidget) object;
            if (button.getMessage().getString().equals(label)) { click(client, button); return; }
        }
        throw new AssertionError("Missing menu item " + label);
    }

    private static String page(MinecraftClient client) throws Exception { return value(client.currentScreen, "page").toString(); }

    private static void pressRow(MinecraftClient client, String label) throws Exception { click(client, control(client, label)); }

    private static AbstractButtonWidget control(MinecraftClient client, String label) throws Exception {
        // Reach every row using the same scroll events as a user, including after a resize.
        PersonalSettingsLayout layout = (PersonalSettingsLayout) value(client.currentScreen, "layout");
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 20; i++) {
                for (Object row : (List<?>) value(client.currentScreen, "rows"))
                    if (value(row, "label").equals(label) && value(row, "widget") != null)
                        return (AbstractButtonWidget) value(row, "widget");
                client.currentScreen.mouseScrolled(layout.contentX + 5, layout.top + 5, pass == 0 ? -1 : 1);
            }
        }
        throw new AssertionError("Missing row " + label);
    }

    private static ButtonWidget find(MinecraftClient client, String label) {
        for (Element element : client.currentScreen.children()) if (element instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) element;
            if (button.visible && button.getMessage().getString().equals(label)) return button;
        }
        return null;
    }

    private static void click(MinecraftClient client, AbstractButtonWidget button) {
        require(button != null, "Missing control");
        client.currentScreen.mouseClicked(button.x + button.getWidth() / 2.0, button.y + 10, 0);
        client.currentScreen.mouseReleased(button.x + button.getWidth() / 2.0, button.y + 10, 0);
    }

    static Object value(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }

    private static void check(MinecraftClient client) {
        for (Element element : client.currentScreen.children()) if (element instanceof AbstractButtonWidget) {
            AbstractButtonWidget button = (AbstractButtonWidget) element;
            if (!button.visible) continue;
            require(button.x >= 0 && button.x + button.getWidth() <= client.currentScreen.width, "Horizontal overflow");
            require(button.y >= 0 && button.y + button.getHeight() <= client.currentScreen.height, "Vertical overflow");
            if (!(button instanceof CheckboxWidget) && !(button instanceof TextFieldWidget))
                require(client.textRenderer.getWidth(button.getMessage()) < button.getWidth(), "Control label clipped");
        }
    }

    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
