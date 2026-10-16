package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Exercises the real splits screen against the race-switch fixtures. */
final class SplitsSmokeChecks {
    private int step, expected;
    private ReplayHandler before;

    boolean tick(MinecraftClient client) throws Exception {
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        switch (step) {
            case 0:
                handler.getReplaySender().setReplaySpeed(0);
                client.options.guiScale = 2; client.onResolutionChanged();
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                camera(0);
                break;
            case 1:
                net.minecraft.entity.player.PlayerEntity runner = client.world.getPlayers().stream()
                        .filter(p -> p != handler.getCameraEntity()).findFirst().get();
                handler.getCameraEntity().setCameraPosition(runner.getX() + 4, runner.getY() + 3, runner.getZ() + 4);
                handler.getCameraEntity().setCameraRotation(runner.yaw, 20, 0);
                open();
                break;
            case 2:
                if (!ready(client, 0) || !ready(client, 1)) return false;
                require(((List<?>) field(client.currentScreen, "columns")).size() == 2, "Splits did not group both recordings");
                assertBounds(client);
                screenshot(client, "splits-wide.png");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                break;
            case 3:
                if (!ready(client, 0)) return false;
                require((Integer) field(field(client.currentScreen, "layout"), "columns") == 1, "Small splits table did not page columns");
                assertBounds(client);
                screenshot(client, "splits-small.png");
                Screen screen = client.currentScreen;
                int footer = (Integer) field(field(screen, "layout"), "footerY");
                ButtonWidget next = screen.children().stream().filter(e -> e instanceof ButtonWidget)
                        .map(e -> (ButtonWidget) e).filter(b -> b.y == footer && b.getMessage().getString().equals(">") && b.active).findFirst().get();
                next.onPress();
                break;
            case 4:
                if (!ready(client, 1)) return false;
                screenshot(client, "splits-second-runner.png");
                before = handler;
                choose(client, 1);
                break;
            case 5:
                if (handler == before) return false;
                require(handler.getReplaySender().paused(), "Split switch resumed paused playback");
                require(Math.abs(handler.getReplaySender().currentTimeStamp() - expected) <= 1, "Split switched to wrong timestamp");
                require((Integer) call(field(controls(), "camera"), "getSelected") == 2, "Split from freecam did not select Follow Player");
                require(!handler.isCameraView(), "Split did not follow recorded runner");
                camera(4);
                handler.getReplaySender().setReplaySpeed(2);
                break;
            case 6:
                open();
                break;
            case 7:
                if (!ready(client, 0)) return false;
                before = handler;
                choose(client, 0);
                require(handler == ReplayModReplay.instance.getReplayHandler(), "Local split unnecessarily reopened the recording");
                require(handler.getReplaySender().getReplaySpeed() == 2, "Local split lost playback speed");
                require(Math.abs(handler.getReplaySender().currentTimeStamp() - expected) < 200, "Local split sought to wrong time");
                require((Integer) call(field(controls(), "camera"), "getSelected") == 4, "Local split lost Details mode");
                break;
            case 8:
                open();
                client.currentScreen.onClose();
                break;
            case 9:
                require(client.currentScreen == null || !client.currentScreen.getClass().getSimpleName().equals("ReplaySplitsScreen"),
                        "Cancelled split scan reopened screen");
                require(handler.getReplaySender().getReplaySpeed() == 2, "Cancelling split scan lost playback speed");
                require(handler.getOverlay().isVisible(), "Splits failed to restore overlay");
                ZsgRooms.LOGGER.info("[ReplaySplitsSmoke] PASS: two columns, reset worlds, small paging, exact cross-recording split, local seek, automatic follow, Details preservation, pause/speed, cancellation");
                return true;
            default: throw new IllegalStateException("Invalid splits smoke step");
        }
        step++;
        return false;
    }

    private static Object controls() throws Exception {
        Field field = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
        field.setAccessible(true);
        return field(field.get(null), "controls");
    }

    private static void camera(int mode) throws Exception {
        Object camera = field(controls(), "camera");
        camera.getClass().getMethod("setSelected", int.class).invoke(camera, mode);
    }

    private static void open() throws Exception { call(controls(), "openSplits"); }

    private static boolean ready(MinecraftClient client, int index) throws Exception {
        if (client.currentScreen == null || !client.currentScreen.getClass().getSimpleName().equals("ReplaySplitsScreen")) return false;
        List<?> columns = (List<?>) field(client.currentScreen, "columns");
        if (columns.size() <= index) return false;
        Object column = columns.get(index);
        if (field(column, "data") == null) {
            String status = (String) field(column, "status");
            require(status.equals("Indexing splits..."), "Column indexing failed: " + status);
            return false;
        }
        return true;
    }

    private void choose(MinecraftClient client, int index) throws Exception {
        Screen screen = client.currentScreen;
        Object column = ((List<?>) field(screen, "columns")).get(index);
        List<?> worlds = (List<?>) field(field(column, "data"), "worlds");
        Object split = null;
        for (int i = 0; i < worlds.size(); i++) {
            Map<?, ?> splits = (Map<?, ?>) field(worlds.get(i), "splits");
            if (!splits.isEmpty()) {
                Field selected = column.getClass().getDeclaredField("world"); selected.setAccessible(true); selected.set(column, i);
                split = splits.values().iterator().next(); break;
            }
        }
        require(split != null, "Fixture has no recorded split");
        expected = (Integer) field(split, "time");
        call(screen, "rebuild");
        Enum<?> kind = (Enum<?>) field(split, "kind");
        Object layout = field(screen, "layout");
        int rowY = 88 + kind.ordinal() * (Integer) field(layout, "rowHeight");
        ButtonWidget target = screen.children().stream().filter(e -> e instanceof ButtonWidget)
                .map(e -> (ButtonWidget) e).filter(b -> b.y == rowY && b.active).findFirst().get();
        target.onPress();
    }

    private static void assertBounds(MinecraftClient client) {
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        for (net.minecraft.client.gui.Element element : client.currentScreen.children()) if (element instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) element;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= width
                    && button.y + button.getHeight() <= height, "Splits widget outside viewport");
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object call(Object object, String name) throws Exception {
        Method method;
        try { method = object.getClass().getDeclaredMethod(name); }
        catch (NoSuchMethodException inherited) { method = object.getClass().getMethod(name); }
        method.setAccessible(true); return method.invoke(object);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
}
