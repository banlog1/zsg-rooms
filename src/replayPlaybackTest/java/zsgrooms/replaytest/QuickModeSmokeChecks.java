package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.ScreenshotUtils;
import zsgrooms.modid.ZsgRooms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Real ReplayMod index, mode transitions and HUD snapshots across dimensions and resets. */
final class QuickModeSmokeChecks {
    private int step, index;
    private long next, started;
    private Object playback;
    private int switchTime;

    boolean tick(ReplayHandler handler, MinecraftClient client, Object controls, List<Object> frames, List<String> terrain) throws Exception {
        if (System.nanoTime() < next) return false;
        if (step == 0) {
            require(!handler.isQuickMode(), "Quick Mode enabled by default");
            playback = get(controls, "playback");
            handler.getReplaySender().setReplaySpeed(0);
            get(controls, "camera").getClass().getMethod("setSelected", int.class).invoke(get(controls, "camera"), 4);
            invoke(get(controls, "details"), "closeInventory");
            handler.getOverlay().setMouseVisible(true);
            invoke(controls, "update");
            switchTime = handler.getReplaySender().currentTimeStamp();
            ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
            toggle(client);
            started = System.nanoTime();
        } else if (step == 1) {
            require(System.nanoTime() - started < 120000000000L, "Quick Mode indexing timed out");
            if ((Boolean) invoke(playback, "busy")) return false;
            require(handler.isQuickMode(), "Quick Mode did not enable: " + invoke(playback, "status"));
            require(handler.getReplaySender().paused(), "Enabling Quick Mode unpaused playback");
            require(Math.abs(handler.getReplaySender().currentTimeStamp() - switchTime) <= 50, "Enabling moved replay time");
            capture(client, "viewer-quick-settings.png");
            client.currentScreen.onClose();
            jump(handler, frames.get(frames.size() - 1));
        } else if (step == 2) {
            // Walk backwards through every world, then forwards to the last one again.
            int frameIndex = frames.size() - 1 - index;
            Object state = get(get(controls, "detailHud"), "state");
            Object frame = get(get(controls, "detailHud"), "last");
            require(state != null && frame != null, "Quick Mode lost detailed HUD at world " + frameIndex);
            require(java.util.Arrays.equals((byte[]) get(frame, "payload"), (byte[]) get(frames.get(frameIndex), "payload")),
                    "Quick Mode displayed wrong world inventory " + frameIndex);
            require(!handler.isCameraView(), "Quick Mode lost player follow");
            require(terrain(client).equals(terrain.get(frameIndex)), "Quick Mode changed terrain at world " + frameIndex);
            capture(client, "viewer-quick-details.png");
            if (++index < frames.size()) {
                jump(handler, frames.get(frames.size() - 1 - index));
                next = System.nanoTime() + 1500000000L;
                return false;
            }
            jump(handler, frames.get(frames.size() - 1));
        } else if (step == 3) {
            require(get(get(controls, "detailHud"), "state") != null, "Quick forward seek lost HUD");
            invoke(controls, "seekTo", int.class, handler.getReplaySender().currentTimeStamp() - 5000);
            require(handler.getReplaySender().paused(), "Quick skip unpaused playback");
            switchTime = handler.getReplaySender().currentTimeStamp();
            ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
            toggle(client);
        } else if (step == 4) {
            if ((Boolean) invoke(playback, "busy")) return false;
            require(!handler.isQuickMode(), "Could not return to normal playback");
            require(handler.getReplaySender().paused(), "Disabling Quick Mode unpaused playback");
            require(Math.abs(handler.getReplaySender().currentTimeStamp() - switchTime) <= 50, "Disabling moved replay time");
            client.currentScreen.onClose();
            jump(handler, frames.get(0));
        } else if (step == 5) {
            require(get(get(controls, "detailHud"), "state") != null, "Normal mode HUD not restored");
            handler.getReplaySender().setReplaySpeed(2);
            invoke(playback, "setQuick", boolean.class, true);
        } else if (step == 6) {
            if ((Boolean) invoke(playback, "busy")) return false;
            require(handler.isQuickMode() && handler.getReplaySender().getReplaySpeed() == 2, "Quick mode lost playback speed");
            invoke(controls, "seekTo", int.class, handler.getReplaySender().currentTimeStamp() + 5000);
            require(!handler.getReplaySender().paused() && handler.getReplaySender().getReplaySpeed() == 2,
                    "Quick skip lost playback speed");
            invoke(playback, "setQuick", boolean.class, false);
        } else {
            if ((Boolean) invoke(playback, "busy")) return false;
            require(!handler.isQuickMode() && handler.getReplaySender().getReplaySpeed() == 2, "Normal mode lost playback speed");
            handler.getReplaySender().setReplaySpeed(0);
            ZsgRooms.LOGGER.info("[ReplayQuickSmoke] PASS: UI toggle, index, pause/speed, backwards {} worlds, forward seek, detailed follow and return to normal", frames.size());
            return true;
        }
        step++;
        next = System.nanoTime() + 1500000000L;
        return false;
    }

    private static void toggle(MinecraftClient client) {
        for (net.minecraft.client.gui.Element element : client.currentScreen.children()) {
            if (element instanceof CheckboxWidget && ((CheckboxWidget) element).getMessage().getString().equals("Quick Mode (experimental)")) {
                ((CheckboxWidget) element).onPress();
                return;
            }
        }
        throw new IllegalStateException("Missing Quick Mode setting");
    }
    static String terrain(MinecraftClient client) {
        net.minecraft.util.math.BlockPos origin = client.getCameraEntity().getBlockPos();
        StringBuilder blocks = new StringBuilder();
        for (int y = -4; y <= 2; y++) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            blocks.append(net.minecraft.block.Block.getRawIdFromState(client.world.getBlockState(origin.add(x, y, z)))).append(',');
        }
        return blocks.toString();
    }
    private static void jump(ReplayHandler handler, Object frame) throws Exception {
        AudioSmokeChecks.seek(handler, (Integer) get(frame, "time"));
        handler.getReplaySender().setReplaySpeed(0);
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static Object invoke(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }
    private static Object invoke(Object object, String name, Class<?> type, Object value) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(object, value);
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
