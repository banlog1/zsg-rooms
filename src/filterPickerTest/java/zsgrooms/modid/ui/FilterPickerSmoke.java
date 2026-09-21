package zsgrooms.modid.ui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/** Isolated visual/interaction test driver, excluded from the release JAR. */
public final class FilterPickerSmoke implements ClientModInitializer {
    private int ticks;
    private int stage;
    private String selected;
    private final Screen parent = new Screen(new LiteralText("Picker test parent")) {};

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("zsgrooms.loadingPositionSmoke")) {
            new LoadingPositionSmoke().initialize();
            return;
        }
        if (Boolean.getBoolean("zsgrooms.spawnPreparationSmoke")) {
            new zsgrooms.modid.SpawnPreparationSmoke().initialize();
            return;
        }
        if (Boolean.getBoolean("zsgrooms.lobbyRulesSmoke")) {
            new LobbyRulesSmoke().initialize();
            return;
        }
        if (Boolean.getBoolean("zsgrooms.seedVisualSmoke")) {
            new SeedPresentationSmoke().initialize();
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (client.getOverlay() != null || ++this.ticks < 30) return;
        this.ticks = 0;
        try {
            switch (this.stage++) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.maxFps = 60;
                    client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    break;
                case 1:
                    open(client, "rooms-temple-v5");
                    break;
                case 2:
                    require(client.getResourceManager().containsResource(new Identifier("zsg-rooms", "textures/gui/filters/dt.png")), "Pack was not enabled");
                    check(client);
                    screenshot(client, "gallery-rooms.png");
                    click(client, "ZSG Rooms Mode");
                    require("rooms-mix".equals(this.selected), "Random mode selection failed");
                    open(client, "rooms-mix");
                    click(client, "Existing Filters");
                    break;
                case 3:
                    check(client);
                    screenshot(client, "gallery-existing.png");
                    click(client, "ZSG Mapless (OP)");
                    require("zsgop".equals(this.selected) && client.currentScreen == this.parent, "Selection callback or return failed");
                    open(client, "rpseedbank");
                    click(client, "Existing Filters");
                    while (findButton(client, "Ruined Portal Seedbank") == null) click(client, ">");
                    break;
                case 4:
                    check(client);
                    screenshot(client, "gallery-rp.png");
                    click(client, "Compact");
                    break;
                case 5:
                    check(client);
                    screenshot(client, "compact-existing.png");
                    click(client, "Other");
                    require(!findButton(client, "Compact").active, "Tab change reset the manual mode");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 6:
                    open(client, "rooms-temple-v5");
                    break;
                case 7:
                    check(client);
                    screenshot(client, "gallery-small.png");
                    click(client, ">");
                    break;
                case 8:
                    check(client);
                    screenshot(client, "gallery-small-next.png");
                    click(client, "Other");
                    click(client, ">");
                    click(client, "Manual Seed");
                    require("manual".equals(this.selected), "Manual selection failed");
                    open(client, "zsgjungletempleop");
                    click(client, "Existing Filters");
                    click(client, "Compact");
                    break;
                case 9:
                    check(client);
                    screenshot(client, "compact-small.png");
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(client.currentScreen == this.parent, "Escape failed");
                    client.getResourcePackManager().setEnabledProfiles(Collections.singleton("vanilla"));
                    client.reloadResources();
                    break;
                case 10:
                    require(!client.getResourceManager().containsResource(new Identifier("zsg-rooms", "textures/gui/filters/dt.png")), "Pack did not unload");
                    open(client, "rooms-temple-v5");
                    check(client);
                    break;
                case 11:
                    screenshot(client, "compact-no-pack.png");
                    click(client, "Gallery");
                    break;
                case 12:
                    check(client);
                    screenshot(client, "gallery-no-pack.png");
                    open(client, "rpseedbank");
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: Rooms default tab, automatic mode with/without pack, manual override, pagination, selection, Escape, resize, resource reload");
                    client.scheduleStop();
                    break;
                default: break;
            }
        } catch (Throwable error) {
            ZsgRooms.LOGGER.error("[FilterPickerSmoke] FAILED", error);
            client.scheduleStop();
            this.stage = 100;
        }
    }

    private void open(MinecraftClient client, String id) {
        client.openScreen(new FilterPickerScreen(this.parent, id, result -> this.selected = result));
        require(!findButton(client, "ZSG Rooms").active, "Picker did not open on Rooms tab");
        boolean hasPack = client.getResourceManager().containsResource(new Identifier("zsg-rooms", "textures/gui/filters/dt.png"));
        require(!findButton(client, hasPack ? "Gallery" : "Compact").active, "Incorrect automatic mode");
    }

    private static ButtonWidget findButton(MinecraftClient client, String label) {
        for (Element element : client.currentScreen.children()) {
            if (element instanceof ButtonWidget && ((ButtonWidget) element).getMessage().getString().equals(label)) {
                return (ButtonWidget) element;
            }
        }
        return null;
    }

    private static void click(MinecraftClient client, String label) {
        for (Element element : client.currentScreen.children()) {
            if (element instanceof ButtonWidget) {
                ButtonWidget button = (ButtonWidget) element;
                if (button.active && button.getMessage().getString().equals(label)) {
                    require(button.mouseClicked(button.x + 3, button.y + 3, 0), "Click failed: " + label);
                    return;
                }
            }
        }
        throw new IllegalStateException("Button missing: " + label);
    }

    private static void check(MinecraftClient client) throws Exception {
        List<? extends Element> children = client.currentScreen.children();
        for (int i = 0; i < children.size(); i++) {
            if (!(children.get(i) instanceof ButtonWidget)) continue;
            ButtonWidget a = (ButtonWidget) children.get(i);
            require(a.x >= 0 && a.y >= 0 && a.x + a.getWidth() <= client.currentScreen.width
                    && a.y + a.getHeight() <= client.currentScreen.height, "Button outside screen");
            for (int j = i + 1; j < children.size(); j++) {
                if (!(children.get(j) instanceof ButtonWidget)) continue;
                ButtonWidget b = (ButtonWidget) children.get(j);
                require(a.x >= b.x + b.getWidth() || b.x >= a.x + a.getWidth()
                        || a.y >= b.y + b.getHeight() || b.y >= a.y + a.getHeight(), "Overlapping buttons");
            }
            if (a.getClass().getSimpleName().equals("FilterButton")) {
                Field field = a.getClass().getDeclaredField("lines");
                field.setAccessible(true);
                List<?> lines = (List<?>) field.get(a);
                require(lines.size() <= 2, "Filter label needs more than two lines: " + a.getMessage().getString());
            }
        }
    }

    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
