package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.settings.*;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Muted, isolated UI and two-launch import checks. Never included in the mod. */
final class SettingsImportSmoke {
    private int stage, ticks, totalTicks;
    private boolean done;
    private SettingsImportScreen screen;
    private Path fixture;
    private SettingsImportStore store;
    private SettingsSnapshotStore.Snapshot snapshot;

    void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient client) {
        if (done || client.getOverlay() != null) return;
        try {
            if (++totalTicks > 1600) throw new AssertionError("Importer smoke timed out");
            if (++ticks < 12) return;
            ticks = 0;
            if (screen != null && (Boolean) value(screen, "busy")) return;
            if (client.currentScreen instanceof SavedSettingsScreen && (Boolean) value(client.currentScreen, "busy")) return;
            switch (stage++) {
                case 0:
                    client.options.setSoundVolume(SoundCategory.MASTER, 0);
                    client.options.pauseOnLostFocus = false; client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    store = new SettingsImportStore(FabricLoader.getInstance().getGameDir());
                    if (Boolean.getBoolean("zsgrooms.settingsImportApplySmoke")) {
                        require(store.status().contains("successfully"), "Pre-launch import did not run: " + store.status());
                        require(client.options.fov == 90, "Imported FOV was not loaded by Minecraft");
                        require(client.options.keyInventory.getBoundKeyTranslationKey().equals("key.keyboard.r"), "Imported binding was not loaded");
                        Object config = Class.forName("me.contaria.standardsettings.StandardSettings").getField("config").get(null);
                        require(config != null, "StandardSettings did not initialize");
                        boolean foundFov = false;
                        for (Object option : (List<?>) config.getClass().getField("standardSettings").get(config)) {
                            if (option.getClass().getMethod("getID").invoke(option).equals("fov")) {
                                require(((Number) option.getClass().getMethod("get").invoke(option)).doubleValue() == 90, "StandardSettings did not load imported FOV");
                                foundFov = true;
                            }
                        }
                        require(foundFov, "StandardSettings FOV option missing");
                        ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: next-launch settings applied before Minecraft and StandardSettings initialized");
                        done = true; client.scheduleStop(); return;
                    }
                    store.cancel();
                    Path fixtureLauncher = FabricLoader.getInstance().getGameDir().resolve("settings-import-fixture/MultiMC");
                    fixture = fixtureLauncher.resolve("instances/import-test/.minecraft");
                    Files.createDirectories(fixture.resolve("config/mcsr"));
                    Files.write(fixtureLauncher.resolve("multimc.cfg"), new byte[0]);
                    Files.write(fixture.getParent().resolve("instance.cfg"), "name=Import smoke test\n".getBytes(StandardCharsets.UTF_8));
                    Files.write(fixture.getParent().resolve("mmc-pack.json"), "{\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.16.1\"}]}".getBytes(StandardCharsets.UTF_8));
                    Files.write(fixture.resolve("options.txt"), "fov:0.5\nkey_key.inventory:key.keyboard.r\nsoundCategory_music:0.0\nunsupported:value\n".getBytes(StandardCharsets.UTF_8));
                    Files.write(fixture.resolve("config/mcsr/standardsettings.json"), "{\"fov\":90.0,\"fovOnWorldJoin\":{\"enabled\":false,\"value\":80.0}}".getBytes(StandardCharsets.UTF_8));
                    client.openScreen(new TitleScreen());
                    break;
                case 1:
                    capture(client, "import-title-desktop.png");
                    press(client, "Import Settings");
                    require(client.currentScreen instanceof SettingsImportScreen, "Title entry missing");
                    screen = (SettingsImportScreen) client.currentScreen;
                    break;
                case 2:
                    String discoveryFrom = System.getProperty("zsgrooms.settingsImportDiscoveryFrom", "");
                    List<SettingsImportInstance> sources = discoveryFrom.isEmpty()
                            ? Collections.singletonList(SettingsImportInstance.read(fixture))
                            : SettingsImportInstance.discover(Paths.get(discoveryFrom));
                    require(!sources.isEmpty(), "No test instances found");
                    Field sourceField = SettingsImportScreen.class.getDeclaredField("sources"); sourceField.setAccessible(true); sourceField.set(screen, sources);
                    Method refresh = SettingsImportScreen.class.getDeclaredMethod("refresh"); refresh.setAccessible(true); refresh.invoke(screen);
                    break;
                case 3:
                    check(client); capture(client, "import-sources-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    break;
                case 4:
                    check(client); capture(client, "import-sources-small.png");
                    if (((List<?>) value(screen, "sources")).size() > (Integer) value(screen, "capacity")) {
                        screen.mouseScrolled(40, 90, -1);
                        require((Integer) value(screen, "offset") > 0, "Instance list did not scroll");
                        screen.mouseScrolled(40, 90, 1);
                    }
                    // Exercise the actual row action, without staging any real instance's settings.
                    press(client, ((List<SettingsImportInstance>) value(screen, "sources")).get(0).name);
                    break;
                case 5:
                    require(value(screen, "page").toString().equals("CATEGORIES"), "Instance row did not select its source: " + value(screen, "status"));
                    require(((SettingsImportPlan) value(screen, "plan")).instance.name.equals(((List<SettingsImportInstance>) value(screen, "sources")).get(0).name), "Selected metadata does not match row");
                    check(client); capture(client, "import-selected-small.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    // Keep repeat runs meaningful after a previous smoke import applied successfully.
                    client.options.fov = 70; client.options.write();
                    Method select = SettingsImportScreen.class.getDeclaredMethod("selectSource", Path.class); select.setAccessible(true); select.invoke(screen, fixture);
                    break;
                case 6:
                    check(client); require(value(screen, "page").toString().equals("CATEGORIES"), "Preview failed: " + value(screen, "status"));
                    capture(client, "import-categories-desktop.png");
                    pressStarts(client, "Review ("); break;
                case 7:
                    check(client); capture(client, "import-review-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480); break;
                case 8:
                    check(client); capture(client, "import-review-small.png");
                    screen.mouseScrolled(40, 90, -1);
                    require((Integer) value(screen, "offset") > 0, "Review did not scroll");
                    press(client, "Import on Next Launch"); break;
                case 9:
                    require(store.pending(), "Import was not staged");
                    check(client); capture(client, "import-restart-small.png");
                    press(client, "Cancel Pending Changes"); break;
                case 10:
                    require(!store.pending(), "Pending cancellation failed");
                    screen.onClose();
                    break;
                case 11:
                    capture(client, "import-title-small.png");
                    client.openScreen(new RoomSettingsScreen(new TitleScreen()));
                    for (Element element : client.currentScreen.children()) if (element instanceof ButtonWidget
                            && ((ButtonWidget) element).getMessage().getString().equals("Import...")) {
                        ((ButtonWidget) element).onPress(); break;
                    }
                    require(client.currentScreen instanceof SettingsImportScreen, "Settings entry missing");
                    screen = (SettingsImportScreen) client.currentScreen; break;
                case 12:
                    check(client);
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    press(client, "Saved Settings"); break;
                case 13:
                    require(client.currentScreen instanceof SavedSettingsScreen, "Saved Settings tab missing");
                    check(client); press(client, "Save Current..."); break;
                case 14:
                    ((net.minecraft.client.gui.widget.TextFieldWidget) value(client.currentScreen, "nameField")).setText("Everyday setup");
                    check(client); capture(client, "saved-settings-name-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480); break;
                case 15:
                    check(client); capture(client, "saved-settings-name-small.png");
                    client.options.fov = 80;
                    press(client, "Save"); break;
                case 16:
                    require(value(client.currentScreen, "page").toString().equals("LIST"), "Snapshot save failed: " + value(client.currentScreen, "status"));
                    snapshot = (SettingsSnapshotStore.Snapshot) value(client.currentScreen, "chosen");
                    String savedFov = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(snapshot.file), StandardCharsets.UTF_8))
                            .getAsJsonObject().getAsJsonObject("options").get("fov").getAsString();
                    require(savedFov.equals("0.25"), "Snapshot did not flush current live FOV: " + savedFov);
                    check(client); capture(client, "saved-settings-small.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720); break;
                case 17:
                    check(client); capture(client, "saved-settings-desktop.png"); press(client, "Everyday setup"); break;
                case 18:
                    check(client); capture(client, "saved-settings-detail-desktop.png"); press(client, "Rename..."); break;
                case 19:
                    ((net.minecraft.client.gui.widget.TextFieldWidget) value(client.currentScreen, "nameField")).setText("Streaming setup");
                    press(client, "Rename"); break;
                case 20:
                    require(((SettingsSnapshotStore.Snapshot) value(client.currentScreen, "chosen")).name.equals("Streaming setup"), "Snapshot rename failed");
                    client.options.fov = 70; client.options.write();
                    press(client, "Review Restore"); break;
                case 21:
                    require(client.currentScreen == screen && value(screen, "page").toString().equals("CATEGORIES"), "Snapshot restore preview failed");
                    require(((SettingsImportPlan) value(screen, "plan")).sourceName.equals("Streaming setup"), "Snapshot label lost in preview");
                    pressStarts(client, "Review ("); break;
                case 22:
                    check(client); capture(client, "saved-settings-restore-desktop.png");
                    press(client, "Import on Next Launch"); break;
                case 23:
                    require(store.pending(), "Snapshot restore was not staged");
                    press(client, "Cancel Pending Changes"); break;
                case 24:
                    require(!store.pending(), "Snapshot restore cancellation failed");
                    press(client, "Saved Settings"); break;
                case 25:
                    press(client, "Streaming setup"); break;
                case 26:
                    press(client, "Delete...");
                    require(Files.exists(snapshot.file), "Snapshot deleted before confirmation"); break;
                case 27:
                    press(client, "Yes"); break;
                case 28:
                    require(!Files.exists(snapshot.file), "Snapshot deletion failed");
                    client.options.fov = 70; client.options.write();
                    SettingsImportPlan plan = SettingsImportPlan.preview(fixture, FabricLoader.getInstance().getGameDir(), Collections.emptySet(), true);
                    store.stage(plan, EnumSet.allOf(ImportSettingsSchema.Category.class), false);
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: importer and saved settings tabs, live snapshot, rename, restore preview, delete confirmation, resize, scrolling and cancellation; restart import staged");
                    done = true; client.scheduleStop(); break;
            }
        } catch (Throwable error) {
            done = true; ZsgRooms.LOGGER.error("[SettingsImportSmoke] FAILED", error); client.scheduleStop();
        }
    }

    private static Object value(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void press(MinecraftClient client, String name) { pressStarts(client, name); }
    private static void pressStarts(MinecraftClient client, String name) {
        for (Element element : client.currentScreen.children()) if (element instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) element;
            if (button.getMessage().getString().startsWith(name) || (name.equals("Import Settings") && button instanceof ImportSettingsButton)) {
                require(button.active, "Disabled: " + name); button.onPress(); return;
            }
        }
        throw new AssertionError("Missing button: " + name);
    }
    private static void check(MinecraftClient client) {
        List<AbstractButtonWidget> buttons = new ArrayList<>();
        for (Element element : client.currentScreen.children()) if (element instanceof AbstractButtonWidget) {
            AbstractButtonWidget b = (AbstractButtonWidget) element;
            require(b.x >= 0 && b.y >= 0 && b.x + b.getWidth() <= client.currentScreen.width && b.y + b.getHeight() <= client.currentScreen.height, "Button outside screen: " + b.getMessage().getString());
            require(client.textRenderer.getWidth(b.getMessage()) <= b.getWidth() - 6, "Button text overflows: " + b.getMessage().getString());
            for (AbstractButtonWidget other : buttons) require(!(b.x < other.x + other.getWidth() && b.x + b.getWidth() > other.x
                    && b.y < other.y + other.getHeight() && b.y + b.getHeight() > other.y), "Overlapping buttons");
            buttons.add(b);
        }
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
