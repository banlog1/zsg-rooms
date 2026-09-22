package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import java.lang.reflect.Method;
import java.util.List;

/** Exercises the separately installed viewer against a fresh recorder smoke fixture. */
final class DetailsSmokeChecks {
    private int step;
    private long next;
    private Object controls, options, track;
    private int target;
    private int loadingTarget = -1;
    private final java.util.ArrayList<Object> transitions = new java.util.ArrayList<>();
    private final java.util.ArrayList<String> terrain = new java.util.ArrayList<>();
    private int transition;
    private net.minecraft.client.util.InputUtil.Key inventoryBinding;
    private final ScreenSmokeChecks screens = new ScreenSmokeChecks();
    private final QuickModeSmokeChecks quickMode = new QuickModeSmokeChecks();
    private boolean screensDone;

    boolean started() { return step > 0; }

    boolean tick(ReplayHandler handler, MinecraftClient client) throws Exception {
        if (System.nanoTime() < next) return false;
        if (step == 0) {
            java.lang.reflect.Field instance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
            instance.setAccessible(true);
            controls = get(instance.get(null), "controls");
            if (controls == null) return false;
            track = get(get(controls, "recordingIndex"), "hud");
            if (track == null) return false;
            options = get(controls, "details");
            require(!(Boolean) get(options, "alwaysInventory") && !(Boolean) get(options, "inventoryOpen"), "Inventory default is not key mode");
            List<?> frames = (List<?>) get(track, "frames");
            for (Object frame : frames) {
                byte[] payload = (byte[]) get(frame, "payload");
                if (payload.length == 0) continue;
                Object state = decode(payload);
                if (state != null && ((ItemStack[]) get(state, "items"))[9].getItem() == Items.ENDER_PEARL) {
                    target = (Integer) get(frame, "time") + 100;
                    break;
                }
            }
            require(target > 0, "Fresh recording lacks inventory samples");
            client.options.guiScale = 2;
            client.onResolutionChanged();
            Object recording = get(get(controls, "recordingIndex"), "recording");
            for (int[] interval : (int[][]) get(get(recording, "loading"), "intervals")) {
                if (interval[0] > target && interval[1] - interval[0] > 2000) {
                    loadingTarget = interval[0] + 1800;
                    break;
                }
            }
            for (Object interval : (List<?>) get(recording, "intervals")) {
                int start = (Integer) get(interval, "start"), end = (Integer) get(interval, "end");
                for (Object frame : frames) {
                    int time = (Integer) get(frame, "time");
                    if (time > start + 500 && time < end - 500 && ((byte[]) get(frame, "payload")).length > 0) {
                        transitions.add(frame);
                        break;
                    }
                }
            }
            require(transitions.size() >= 6, "Fixture lacks dimension/reset coverage");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            handler.doJump(target, true);
            handler.getReplaySender().setReplaySpeed(0);
            Object dropdown = get(controls, "camera");
            dropdown.getClass().getMethod("setSelected", int.class).invoke(dropdown, 4);
            client.openScreen(null);
            handler.getOverlay().setMouseVisible(false);
        } else if (step == 1) {
            require(!handler.isCameraView(), "Details mode did not follow player");
            Object state = get(get(controls, "detailHud"), "state");
            require(state != null, "Details did not decode recorded state");
            ItemStack[] items = (ItemStack[]) get(state, "items");
            require(items[9].getItem() == Items.ENDER_PEARL && items[9].getCount() == 7, "Inventory mismatched capture");
            require(items[1].getCount() == 12 && items[40].getItem() == Items.SHIELD, "Hotbar/offhand mismatched capture");
            require((Float) get(state, "health") > 0 && (Integer) get(state, "food") > 0, "Health/hunger missing");
            capture(client, "viewer-details-hotbar.png");
            inventoryBinding = net.minecraft.client.util.InputUtil.fromTranslationKey(client.options.keyInventory.getBoundKeyTranslationKey());
            client.options.keyInventory.setBoundKey(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_I));
            net.minecraft.client.options.KeyBinding.updateKeysByCode();
            key(client);
            require((Boolean) get(options, "inventoryOpen"), "Inventory key did not open details");
        } else if (step == 2) {
            require(!(Boolean) get(controls, "shown"), "Inventory did not hide playback controls");
            capture(client, "viewer-details-inventory.png");
            key(client);
            require(!(Boolean) get(options, "inventoryOpen"), "Inventory key did not close details");
            client.options.keyInventory.setBoundKey(inventoryBinding);
            net.minecraft.client.options.KeyBinding.updateKeysByCode();
            handler.getOverlay().setMouseVisible(true);
            Method update = controls.getClass().getDeclaredMethod("update");
            update.setAccessible(true);
            update.invoke(controls);
            require((Boolean) get(controls, "shown"), "Closing inventory did not restore controls");
            ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
            for (net.minecraft.client.gui.Element element : client.currentScreen.children()) {
                if (element instanceof CheckboxWidget && ((CheckboxWidget) element).getMessage().getString().equals("Always show inventory")) {
                    ((CheckboxWidget) element).onPress();
                }
            }
            require((Boolean) get(options, "alwaysInventory"), "Always inventory setting failed");
            client.currentScreen.onClose();
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
        } else if (step == 3) {
            capture(client, "viewer-details-small.png");
            require(get(get(controls, "detailHud"), "state") != null, "Small GUI lost details");
            key(client, GLFW.GLFW_KEY_ESCAPE);
            require((Boolean) get(options, "inventoryDismissed") && (Boolean) get(options, "alwaysInventory"),
                    "Escape did not dismiss optional always inventory without changing preference");
            handler.doJump(0, true);
            handler.getReplaySender().setReplaySpeed(0);
        } else if (step == 4) {
            require(get(get(controls, "detailHud"), "state") == null, "Seeking before player leaked later inventory");
            handler.doJump(target, true);
            handler.getReplaySender().setReplaySpeed(0);
        } else if (step == 5) {
            require(get(get(controls, "detailHud"), "state") != null, "Seeking forward did not restore details");
            seekFrame(handler, transitions.get(0));
        } else if (step == 6) {
            terrain.add(QuickModeSmokeChecks.terrain(client));
            Object state = get(get(controls, "detailHud"), "state");
            Object expected = decode((byte[]) get(transitions.get(transition), "payload"));
            require(state != null && get(state, "food").equals(get(expected, "food"))
                    && get(state, "health").equals(get(expected, "health")), "Dimension/reset state mismatch " + transition);
            if (++transition < transitions.size()) {
                seekFrame(handler, transitions.get(transition));
                next = System.nanoTime() + 1500000000L;
                return false;
            }
            Object dropdown = get(controls, "camera");
            dropdown.getClass().getMethod("setSelected", int.class).invoke(dropdown, 0);
        } else if (step == 7) {
            require(!(Boolean) Class.forName("zsgrooms.replayviewer.ReplayViewer").getMethod("detailedActive").invoke(null), "Freecam retained details");
            if (loadingTarget >= 0) {
                handler.doJump(loadingTarget, true);
                handler.getReplaySender().setReplaySpeed(0);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                get(controls, "camera").getClass().getMethod("setSelected", int.class).invoke(get(controls, "camera"), 3);
            }
        } else if (step == 8 && loadingTarget >= 0) {
            require(loading(controls), "Long recorded load lost its indicator");
            capture(client, "viewer-loading.png");
            handler.doJump(target, true);
            handler.getReplaySender().setReplaySpeed(0);
        } else if (step == 9 && loadingTarget >= 0) {
            require(!loading(controls), "Loading indicator leaked into active play after backwards seek");
            handler.doJump(loadingTarget, true);
            handler.getReplaySender().setReplaySpeed(0);
        } else {
            if (loadingTarget >= 0 && !screens.started()) require(loading(controls), "Loading indicator did not return after forward seek");
            if (!screensDone) {
                if (!screens.tick(handler, client, controls)) return false;
                screensDone = true;
            }
            if (!quickMode.tick(handler, client, controls, transitions, terrain)) return false;
            AudioSmokeChecks.run(handler, client);
            ZsgRooms.LOGGER.info("[ReplayDetailsSmoke] PASS: recorded inventory, health/hunger, hotbar/offhand, keyboard toggle, optional always mode, small GUI, seeking and {} world intervals", transitions.size());
            ZsgRooms.LOGGER.info("[ReplayDetailsSmoke] Loading interval playback checked: {}", loadingTarget >= 0);
            return true;
        }
        step++;
        next = System.nanoTime() + 1500000000L;
        return false;
    }
    private static boolean loading(Object controls) throws Exception {
        Method method = controls.getClass().getDeclaredMethod("recordedLoading");
        method.setAccessible(true);
        return (Boolean) method.invoke(controls);
    }
    private static void seekFrame(ReplayHandler handler, Object frame) throws Exception {
        AudioSmokeChecks.seek(handler, (Integer) get(frame, "time"));
        handler.getReplaySender().setReplaySpeed(0);
    }
    private static Object decode(byte[] payload) throws Exception {
        Method decode = Class.forName("zsgrooms.replayviewer.PlayerHudState").getDeclaredMethod("decode", byte[].class);
        decode.setAccessible(true);
        return decode.invoke(null, (Object) payload);
    }
    private static void key(MinecraftClient client) throws Exception {
        key(client, net.minecraft.client.util.InputUtil.fromTranslationKey(client.options.keyInventory.getBoundKeyTranslationKey()).getCode());
    }
    private static void key(MinecraftClient client, int key) throws Exception {
        Method onKey = client.keyboard.getClass().getDeclaredMethod("onKey", long.class, int.class, int.class, int.class, int.class);
        onKey.setAccessible(true);
        onKey.invoke(client.keyboard, client.getWindow().getHandle(), key, 0, GLFW.GLFW_PRESS, 0);
        onKey.invoke(client.keyboard, client.getWindow().getHandle(), key, 0, GLFW.GLFW_RELEASE, 0);
    }
    private static Object get(Object object, String name) throws Exception {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
