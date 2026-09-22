package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotUtils;
import zsgrooms.modid.ZsgRooms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Screen indicators must follow the recording, not the viewer's inventory controls. */
final class ScreenSmokeChecks {
    private int step;
    private long next;
    private int inventory, crafting;

    boolean started() { return step > 0; }

    boolean tick(ReplayHandler handler, MinecraftClient client, Object controls) throws Exception {
        if (System.nanoTime() < next) return false;
        if (step == 0) {
            checkTooltipLabels();
            handler.getOverlay().setMouseVisible(true);
            Object recording = get(get(controls, "recordingIndex"), "recording");
            for (int[] interval : (int[][]) get(get(recording, "screens"), "intervals")) {
                if (interval[1] - interval[0] < 500) continue;
                if (interval[2] == 1 && inventory == 0) inventory = interval[0] + 250;
                if (interval[2] == 2 && crafting == 0) crafting = interval[0] + 250;
            }
            require(inventory > 0 && crafting > 0, "Missing recorded inventory/crafting intervals");
            Object camera = get(controls, "camera");
            camera.getClass().getMethod("setSelected", int.class).invoke(camera, 3);
            jump(handler, inventory);
        } else if (step == 1) {
            require(screen(controls) == 1, "Inventory indicator missing in drone mode");
            hoverIndicator(client);
        } else if (step == 2) {
            capture(client, "viewer-inventory-indicator.png");
            jump(handler, crafting);
        } else if (step == 3) {
            require(screen(controls) == 2, "Crafting indicator missing in drone mode");
            capture(client, "viewer-crafting-indicator.png");
            Object camera = get(controls, "camera");
            camera.getClass().getMethod("setSelected", int.class).invoke(camera, 4);
            client.options.perspective = 0;
        } else if (step == 4) {
            require(screen(controls) == 2, "Follow changed recorded screen state");
            capture(client, "viewer-crafting-indicator-follow.png");
            Object details = get(controls, "details");
            Method toggle = details.getClass().getDeclaredMethod("toggleInventory");
            toggle.setAccessible(true);
            toggle.invoke(details);
            require(screen(controls) == 2, "Viewer inventory replaced recorded crafting state");
            jump(handler, inventory);
        } else if (step == 5) {
            require(screen(controls) == 1, "Backward seek did not restore inventory indicator");
            jump(handler, 0);
        } else if (step == 6) {
            require(screen(controls) == 0, "Screen indicator leaked before player existed");
            jump(handler, crafting);
        } else {
            require(screen(controls) == 2, "Forward seek did not restore crafting indicator");
            ZsgRooms.LOGGER.info("[ReplayScreensSmoke] PASS: inventory/crafting capture, drone/follow, indicator hover labels, viewer UI independence and seeking");
            return true;
        }
        step++;
        next = System.nanoTime() + 1500000000L;
        return false;
    }

    private static int screen(Object controls) throws Exception {
        Method method = controls.getClass().getDeclaredMethod("recordedScreen");
        method.setAccessible(true);
        return (Integer) method.invoke(controls);
    }

    private static void checkTooltipLabels() throws Exception {
        Class<?> type = Class.forName("zsgrooms.replayviewer.IndicatorTooltips");
        Method label = type.getDeclaredMethod("hudLabel", double.class, double.class, int.class, boolean.class, boolean.class, int.class);
        label.setAccessible(true);
        require("Recorded player is loading a world.".equals(label.invoke(null, 84.0, 18.0, 100, true, false, 0)), "Loading tooltip missing");
        require("Recorded player has paused the game.".equals(label.invoke(null, 84.0, 18.0, 100, false, true, 0)), "Pause tooltip missing");
        require("Recorded player has their inventory open.".equals(label.invoke(null, 84.0, 18.0, 100, false, false, 1)), "Inventory tooltip missing");
        require("Recorded player is using a crafting table.".equals(label.invoke(null, 62.0, 18.0, 100, true, false, 2)), "Combined indicator tooltip missing");
        require(label.invoke(null, 84.0, 40.0, 100, true, false, 2) == null, "Tooltip outside icon bounds");
        require(label.invoke(null, 84.0, 18.0, 100, false, false, 0) == null, "Tooltip for hidden indicator");
    }

    private static void hoverIndicator(MinecraftClient client) throws Exception {
        Class<?> type = Class.forName("zsgrooms.replayviewer.IndicatorTooltips");
        Field regions = type.getDeclaredField("world");
        regions.setAccessible(true);
        Object region = ((Object[]) regions.get(null))[0];
        if (get(region, "player") == null) {
            capture(client, "viewer-indicator-hover-failure.png");
            Field enabled = type.getDeclaredField("enabled");
            enabled.setAccessible(true);
            Field projection = type.getDeclaredField("projection");
            projection.setAccessible(true);
            throw new IllegalStateException("Overhead indicator has no hover region: enabled=" + enabled.get(null)
                    + ", projection=" + projection.get(null) + ", left=" + get(region, "left"));
        }
        double x = ((Float) get(region, "left") + (Float) get(region, "right")) / 2;
        double y = ((Float) get(region, "top") + (Float) get(region, "bottom")) / 2;
        // Native cursor warps need focus; dispatch the normal callback for unattended tests.
        Method cursor = client.mouse.getClass().getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
        cursor.setAccessible(true);
        cursor.invoke(client.mouse, client.getWindow().getHandle(),
                x * client.getWindow().getWidth() / client.getWindow().getScaledWidth(),
                y * client.getWindow().getHeight() / client.getWindow().getScaledHeight());
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void jump(ReplayHandler handler, int time) throws Exception {
        handler.doJump(time, true);
        handler.getReplaySender().setReplaySpeed(0);
        handler.getOverlay().setMouseVisible(true);
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
