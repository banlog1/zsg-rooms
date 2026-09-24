package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import zsgrooms.modid.ZsgRooms;

final class ChestSmokeChecks {
    private int step, initialTime, changedTime, doubleTime, inspectionTime;
    private long next;
    private Object controls, history, single, pair;
    private BlockPos pos;

    boolean tick(ReplayHandler handler, MinecraftClient client) throws Exception {
        if (System.nanoTime() < next) return false;
        if (step == 0) {
            Class<?> api = Class.forName("zsgrooms.replayviewer.ReplayViewer");
            controls = get(getStatic(api, "instance"), "controls");
            if (controls == null) return false;
            history = get(get(controls, "milestoneIndex"), "chests");
            if (history == null) return false;
            Object recording = get(get(controls, "recordingIndex"), "recording");
            List<?> bindings = (List<?>) get(recording, "chests");
            require(bindings.size() >= 6, "Missing chest opening/reset metadata: " + bindings.size());
            single = bindings.get(0); pair = bindings.get(1);
            pos = BlockPos.fromLong((Long) get(single, "first"));
            for (Object rows : ((Map<?, ?>) get(history, "frames")).values()) {
                for (Object frame : (List<?>) rows) {
                    Object binding = get(frame, "opening");
                    if ((Integer) get(binding, "world") != 0) continue;
                    List<?> items = (List<?>) get(frame, "items");
                    if (items == null) continue;
                    ItemStack stack = (ItemStack) items.get(0);
                    int time = (Integer) get(frame, "time");
                    if (stack.getItem() == Items.IRON_INGOT && stack.getCount() == 7 && initialTime == 0) initialTime = time;
                    if (stack.getItem() == Items.IRON_INGOT && stack.getCount() == 2 && changedTime == 0) changedTime = time;
                    if (items.size() == 54 && doubleTime == 0) doubleTime = time;
                }
            }
            require(initialTime > 0 && changedTime > initialTime && doubleTime > changedTime, "Chest contents/slot updates missing");
            require(itemsAt(single, initialTime - 1) == null, "Future inventory leaked before opening");
            require(itemsAt(single, initialTime).get(0).getCount() == 7, "Initial iron mismatch");
            require(itemsAt(single, changedTime).get(0).getCount() == 2, "Changed iron mismatch");
            require(itemsAt(pair, doubleTime).size() == 54, "Double chest not indexed");
            require(itemsAt(bindings.get(3), initialTime) == null, "Contents leaked across reset");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            client.options.guiScale = 2; client.onResolutionChanged();
            dropdown().getClass().getMethod("setSelected", int.class).invoke(dropdown(), 0);
            seek(handler, initialTime + 50);
        } else if (step == 1 || step == 5) {
            client.openScreen(null);
            handler.getOverlay().setMouseVisible(false);
            handler.getCameraEntity().setCameraPosition(pos.getX() + 0.5, pos.getY() + 0.5 - handler.getCameraEntity().getStandingEyeHeight(), pos.getZ() - 3);
            handler.getCameraEntity().setCameraRotation(0, 0, 0);
            client.onWindowFocusChanged(true);
            client.mouse.lockCursor();
            inspectionTime = handler.getReplaySender().currentTimeStamp();
            handler.getReplaySender().setReplaySpeed(step == 1 ? 0.5 : 0);
            KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(client.options.keyUse.getBoundKeyTranslationKey()));
            Method input = handler.getCameraEntity().getClass().getDeclaredMethod("handleInputEvents");
            input.setAccessible(true); input.invoke(handler.getCameraEntity());
            require(client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("ChestInspectionScreen"), "Freecam did not open chest inspector");
            require(step == 1 ? !handler.getReplaySender().paused() && handler.getReplaySender().getReplaySpeed() == 0.5
                    : handler.getReplaySender().paused(), "Inspector changed playback state");
            List<?> items = (List<?>) get(get(client.currentScreen, "frame"), "items");
            require(((ItemStack) items.get(0)).getCount() == 7, "Displayed chest contents mismatch");
        } else if (step == 2 || step == 6) {
            require(client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("ChestInspectionScreen"),
                    "Inspector closed during playback");
            require(step == 2 ? handler.getReplaySender().currentTimeStamp() > inspectionTime
                    : handler.getReplaySender().currentTimeStamp() == inspectionTime, "Inspector changed timeline advancement");
            require(!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen), "Inspector uses interactive inventory");
            screenshot(client, step == 2 ? "viewer-chest.png" : "viewer-chest-small-quick.png");
            client.currentScreen.onClose();
            require(step == 2 ? !handler.getReplaySender().paused() && handler.getReplaySender().getReplaySpeed() == 0.5
                    : handler.getReplaySender().paused(), "Closing inspector changed playback state");
            if (step == 6) {
                ZsgRooms.LOGGER.info("[ReplayChestSmoke] PASS: real capture, slot deltas, double chest, reset isolation, read-only UI and Quick seeking");
                return true;
            }
            seek(handler, changedTime + 50);
            require(itemsAt(single, initialTime).get(0).getCount() == 7, "Backward query changed after seeking");
            Object playback = get(controls, "playback");
            Method toggle = playback.getClass().getDeclaredMethod("setQuick", boolean.class);
            toggle.setAccessible(true); toggle.invoke(playback, true);
        } else if (step == 3) {
            Object playback = get(controls, "playback");
            Method busy = playback.getClass().getDeclaredMethod("busy"); busy.setAccessible(true);
            if ((Boolean) busy.invoke(playback)) return false;
            require(handler.isQuickMode(), "Quick Mode did not enable");
            seek(handler, initialTime + 50);
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
            client.options.guiScale = 2; client.onResolutionChanged();
        }
        step++; next = System.nanoTime() + 1200000000L;
        return false;
    }

    private Object dropdown() throws Exception { return get(controls, "camera"); }
    @SuppressWarnings("unchecked") private List<ItemStack> itemsAt(Object binding, int time) throws Exception {
        Method at = history.getClass().getDeclaredMethod("at", int.class, String.class, long.class, int.class); at.setAccessible(true);
        Object frame = at.invoke(history, get(binding, "world"), get(binding, "dimension"), get(binding, "first"), time);
        return frame == null ? null : (List<ItemStack>) get(frame, "items");
    }
    private static void seek(ReplayHandler handler, int time) throws Exception {
        handler.getReplaySender().setReplaySpeed(0);
        AudioSmokeChecks.seek(handler, time);
        handler.getReplaySender().setReplaySpeed(0);
    }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static Object getStatic(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static void screenshot(MinecraftClient client, String name) { ScreenshotUtils.saveScreenshot(client.runDirectory, name,
            client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> {}); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
