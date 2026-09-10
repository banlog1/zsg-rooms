package zsgrooms.replaytest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Isolated fixtures derived from the development capture, never the user's original replay. */
final class RaceSwitchSmoke {
    private Path incoming;
    private String first;
    private String second;
    private int step;
    private long next;
    private ReplayHandler before;
    private int expected;

    File prepare(MinecraftClient client, File original) throws Exception {
        // Reproduce opening playback after saving a recording, with the live badge enabled.
        Field preferences = zsgrooms.modid.replay.ReplayPrototype.class.getDeclaredField("preferences");
        preferences.setAccessible(true);
        preferences.set(null, new zsgrooms.modid.replay.ReplayPreferences(true, "", true));
        Field status = zsgrooms.modid.replay.ReplayPrototype.class.getDeclaredField("recordingStatus");
        status.setAccessible(true);
        status.set(null, "Saved to replay_recordings");
        String group = UUID.randomUUID().toString();
        first = UUID.randomUUID().toString(); second = UUID.randomUUID().toString();
        Path root = client.runDirectory.toPath();
        Path a = root.resolve("replay_recordings/race-test-" + group + "/a.mcpr");
        incoming = root.resolve("race-test-incoming/" + group + "/b.mcpr");
        fixture(original, a, first, group, 4000);
        fixture(original, incoming, second, group, 7000);
        return a.toFile();
    }

    private static void fixture(File source, Path destination, String id, String group, int start) throws Exception {
        Files.createDirectories(destination.getParent());
        try (ZipFile zip = new ZipFile(source); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            JsonObject metadata;
            try (InputStreamReader input = new InputStreamReader(zip.getInputStream(zip.getEntry("zsg-rooms/races.json")), StandardCharsets.UTF_8)) {
                metadata = new JsonParser().parse(input).getAsJsonObject();
            }
            metadata.addProperty("recordingId", id);
            JsonObject race = new JsonObject();
            race.addProperty("raceId", UUID.randomUUID().toString()); race.addProperty("testGroupId", group);
            race.addProperty("startOffsetNanos", start * 1000000L);
            race.addProperty("endOffsetNanos", metadata.get("durationMillis").getAsLong() * 1000000L);
            race.addProperty("firstWorldIndex", 0);
            JsonArray races = new JsonArray(); races.add(race); metadata.add("races", races);
            JsonArray timing = new JsonArray();
            for (int t = 0; t <= metadata.get("durationMillis").getAsInt(); t += 1000) {
                JsonArray row = new JsonArray();
                row.add(t); row.add(0); row.add(1); row.add(t); row.add(5000);
                timing.add(row);
            }
            metadata.add("timingSamples", timing);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[65536];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                output.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals("zsg-rooms/races.json")) output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
                else try (InputStream input = zip.getInputStream(entry)) {
                    int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                output.closeEntry();
            }
        }
    }

    boolean tick(MinecraftClient client) throws Exception {
        if (System.nanoTime() < next || (Boolean) field(Class.forName("zsgrooms.replayviewer.ReplayViewer"), null, "switching")) return false;
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        Object overlay = handler.getOverlay();
        switch (step) {
            case 0:
                if (handler.getReplaySender().currentTimeStamp() < 2500) return false;
                client.options.guiScale = 2; client.onResolutionChanged();
                handler.getReplaySender().setReplaySpeed(0);
                handler.doJump(8500, true);
                handler.getOverlay().setMouseVisible(true);
                ViewerSmokeChecks.dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(ViewerSmokeChecks.dropdown(overlay), 3);
                handler.getOverlay().setVisible(false);
                break;
            case 1:
                require(client.getServer() == null, "Playback unexpectedly has an integrated server");
                require("Replay saved".equals(zsgrooms.modid.replay.ReplayPrototype.getHudStatus()), "Missing saved-badge regression fixture");
                screenshot(client, "race-timers-far.png");
                handler.getOverlay().setVisible(true);
                expected = handler.getReplaySender().currentTimeStamp() + 3000;
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(overlay, "Players"));
                break;
            case 2:
                if (!ready(client)) return false;
                require(((java.util.List<?>) field(client.currentScreen.getClass(), client.currentScreen, "entries")).size() == 1,
                        "Unexpected initial group: " + field(client.currentScreen.getClass(), client.currentScreen, "status"));
                Method method = client.currentScreen.getClass().getDeclaredMethod("importPath", Path.class); method.setAccessible(true);
                method.invoke(client.currentScreen, incoming);
                break;
            case 3:
                if (!ready(client)) return false;
                require(((java.util.List<?>) field(client.currentScreen.getClass(), client.currentScreen, "entries")).size() == 2, "Import did not add second take");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                break;
            case 4:
                screenshot(client, "race-players-wide.png");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                break;
            case 5:
                screenshot(client, "race-players-small.png");
                before = handler;
                row(client, second).onPress();
                break;
            case 6:
                if (handler == before) return false;
                require(handler.getReplaySender().paused(), "Paused state lost during switch");
                require(Math.abs(handler.getReplaySender().currentTimeStamp() - expected) <= 1,
                        "Race alignment differs: expected " + expected + ", actual " + handler.getReplaySender().currentTimeStamp());
                require((Integer) ViewerSmokeChecks.dropdown(overlay).getClass().getMethod("getSelected").invoke(ViewerSmokeChecks.dropdown(overlay)) == 3, "Far-follow mode lost");
                require(client.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD, "Destination dimension not loaded");
                screenshot(client, "race-switched.png");
                handler.getReplaySender().setReplaySpeed(2.0);
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(overlay, "Players"));
                break;
            case 7:
                if (!ready(client)) return false;
                expected = handler.getReplaySender().currentTimeStamp() - 3000;
                before = handler; row(client, first).onPress();
                break;
            case 8:
                if (handler == before) return false;
                require(handler.getReplaySender().getReplaySpeed() == 2.0, "Playback speed lost");
                require(Math.abs(handler.getReplaySender().currentTimeStamp() - expected) < 2500, "Reverse alignment differs");
                require(Files.exists(incoming), "Import removed original file");
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(overlay, "Players"));
                break;
            case 9:
                if (!ready(client)) return false;
                client.currentScreen.onClose();
                require(handler.getOverlay().isVisible(), "Back did not restore the playback bar");
                require(handler.getReplaySender().getReplaySpeed() == 2.0, "Back did not restore playback speed");
                handler.getReplaySender().setReplaySpeed(0);
                ViewerSmokeChecks.dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(ViewerSmokeChecks.dropdown(overlay), 2);
                break;
            case 10:
                screenshot(client, "race-timers-follow.png");
                ZsgRooms.LOGGER.info("[ReplayRaceSmoke] PASS: import, same-account test takes, elapsed-time alignment, two-way switch, pause/speed, Far follow, cross-dimension, responsive selector, Back restores playback");
                return true;
            default: throw new IllegalStateException("Invalid test step");
        }
        step++; next = System.nanoTime() + 1000000000L;
        return false;
    }

    private static boolean ready(MinecraftClient client) throws Exception {
        return client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("RaceReplayScreen")
                && !(Boolean) field(client.currentScreen.getClass(), client.currentScreen, "busy");
    }

    private static ButtonWidget row(MinecraftClient client, String id) {
        for (net.minecraft.client.gui.Element element : client.currentScreen.children()) {
            if (element instanceof ButtonWidget) {
                ButtonWidget button = (ButtonWidget) element;
                if (button.getMessage().getString().contains(id.substring(0, 8))) {
                    require(button.active, "Target row unavailable"); return button;
                }
            }
        }
        throw new IllegalStateException("Recording row missing");
    }

    private static Object field(Class<?> type, Object object, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
}
