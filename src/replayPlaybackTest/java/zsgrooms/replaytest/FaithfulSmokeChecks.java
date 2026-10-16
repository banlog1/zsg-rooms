package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.FullReplaySender;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Real viewer checks against isolated copies with deterministic optional presentation frames. */
final class FaithfulSmokeChecks {
    private Path directory, detailed, legacy, source;
    private int base, world, entity, step, automaticStep, hudStep, detailsStep;
    private long next;
    private Object controls, faithful, analysis;
    private byte[] hud, sourceHash;
    private int savedDistance, savedPerspective;
    private InputUtil.Key inventoryBinding;
    private boolean running, savedAutoHide, distanceRewound;
    private double savedZoom;
    private int savedOffset, frozen, timelineTarget, timelineX, timelineY, hudDistance;
    private String hudPrefix;

    File prepare(MinecraftClient client, File original) throws Exception {
        source = original.toPath().toRealPath(); sourceHash = hash(source);
        Class<?> recordingClass = Class.forName("zsgrooms.replayviewer.RaceRecording");
        Object recording = callStatic(recordingClass, "read", new Class<?>[]{Path.class}, original.toPath());
        Object track = callStatic(Class.forName("zsgrooms.replayviewer.PlayerHudTrack"), "read",
                new Class<?>[]{recordingClass}, recording);
        require(track != null, "Faithful fixture needs an existing player HUD track");
        for (Object frame : (List<?>) get(track, "frames")) {
            int time = (Integer) get(frame, "time");
            byte[] payload = (byte[]) get(frame, "payload");
            if (time < 2500 || payload.length == 0) continue;
            Object interval = invoke(recording, "intervalAt", new Class<?>[]{int.class}, time);
            if (interval == null || (Integer) get(interval, "end") < time + 4000) continue;
            base = time; world = (Integer) get(frame, "world"); entity = (Integer) get(frame, "entity"); hud = payload;
            break;
        }
        require(hud != null, "Fixture lacks a populated HUD frame with four seconds of world coverage");
        validateOriginalDetail(recordingClass, recording, track);
        directory = Files.createTempDirectory(client.runDirectory.toPath(), "faithful-smoke-");
        detailed = directory.resolve("faithful-detail.mcpr");
        legacy = directory.resolve("faithful-legacy.mcpr");
        fixture(original, detailed, true);
        fixture(original, legacy, false);
        ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] Isolated synthetic fixtures: {} (source untouched)", directory);
        return detailed.toFile();
    }

    private static void validateOriginalDetail(Class<?> recordingClass, Object recording, Object hudTrack) throws Exception {
        Class<?> detailClass = Class.forName("zsgrooms.replayviewer.DetailedTrack");
        Object detail = callStatic(detailClass, "read", new Class<?>[]{recordingClass}, recording);
        if (detail == null) {
            ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] Original has no detail track; real encoder/decoder contract NOT CHECKED");
            return;
        }
        try {
            require((Integer) invoke(detail, "frameCount") > 0, "Original detail track contains no frames");
            boolean decoded = false;
            for (Object frame : (List<?>) get(hudTrack, "frames")) {
                int time = (Integer) get(frame, "time");
                Object interval = invoke(recording, "intervalAt", new Class<?>[]{int.class}, time);
                if (interval == null) continue;
                Object captured = invoke(detail, "at", new Class<?>[]{int.class, int.class, int.class, int.class},
                        time, get(frame, "world"), get(frame, "entity"), get(interval, "start"));
                if (captured != null && callStatic(Class.forName("zsgrooms.replayviewer.DetailedState"), "decode",
                        new Class<?>[]{byte[].class}, (Object) get(captured, "payload")) != null) {
                    decoded = true; break;
                }
            }
            require(decoded, "Real recorder detail frames did not decode as available viewer states");
            ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] Real recorder detail track validated: {} frames, populated state decoded", invoke(detail, "frameCount"));
        } finally { invoke(detail, "close"); }
    }

    private void fixture(File original, Path destination, boolean detail) throws Exception {
        try (ZipFile input = new ZipFile(original);
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[65536];
            java.util.Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals("zsg-rooms/detail.bin") || entry.getName().equals("zsg-rooms/detail.json")) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream source = input.getInputStream(entry)) {
                    int count; while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                output.closeEntry();
            }
            if (detail) {
                output.putNextEntry(new ZipEntry("zsg-rooms/detail.bin"));
                DataOutputStream frames = new DataOutputStream(output);
                frames.writeInt(0x5A444554); frames.writeInt(1);
                for (int i = 0; i < 7; i++) {
                    byte[] payload = payload(i);
                    frames.writeInt(base + i * 400); frames.writeInt(world); frames.writeInt(entity);
                    frames.writeInt(payload.length); frames.write(payload);
                }
                output.closeEntry();
            }
        }
    }

    private byte[] payload(int variant) throws Exception {
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        try {
            int kind = variant >= 1 && variant <= 3 ? variant : 0;
            data.writeByte(1); data.writeInt(fixtureDistance(variant)); data.writeByte(variant == 6 ? 2 : 0);
            data.writeBoolean(variant == 5); data.writeBoolean(variant == 4);
            data.writeFloat(0.25F); data.writeFloat(0.30F); data.writeByte(1); data.writeBoolean(false);
            data.writeInt(hud.length); data.writeBytes(hud);
            data.writeByte(kind); data.writeInt(variant + 1); data.writeInt(kind == 0 ? -1 : 7);
            data.writeString(kind == 3 ? "Smoke chest" : "Smoke inventory");
            int count = kind == 0 ? 0 : kind == 3 ? 63 : 46;
            data.writeShort(count);
            Object layout = count == 0 ? null : callStatic(Class.forName("zsgrooms.replayviewer.RecordedScreenLayout"),
                    "create", new Class<?>[]{int.class, int.class}, kind, count);
            int[] x = count == 0 ? null : (int[]) get(layout, "x"), y = count == 0 ? null : (int[]) get(layout, "y");
            for (int i = 0; i < count; i++) {
                data.writeShort(i); data.writeShort(x[i]); data.writeShort(y[i]);
                data.writeItemStack(i == 0 ? new ItemStack(Items.DIAMOND, 3) : ItemStack.EMPTY);
            }
            data.writeItemStack(kind == 0 ? ItemStack.EMPTY : new ItemStack(Items.GOLD_INGOT, 2));
            byte[] result = new byte[data.readableBytes()]; data.readBytes(result); return result;
        } finally { data.release(); }
    }

    boolean tick(MinecraftClient client) throws Exception {
        if (running) return false;
        running = true;
        try { return advance(client); }
        catch (Exception failure) {
            restoreBinding(client);
            throw new IllegalStateException("Faithful smoke step " + step + ", automatic " + automaticStep
                    + ", HUD " + hudStep + ", details " + detailsStep, failure);
        } finally { running = false; }
    }

    private boolean advance(MinecraftClient client) throws Exception {
        if (System.nanoTime() < next) return false;
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        switch (step) {
            case 0:
                if (handler == null || client.world == null || client.player == null
                        || handler.getReplaySender().currentTimeStamp() < 2500) return false;
                controls = controls();
                if (controls == null || get(get(controls, "recordingIndex"), "detail") == null) return false;
                faithful = get(controls, "faithful");
                analysis = get(controls, "analysis");
                require((Boolean) get(analysis, "runTimers") && !(Boolean) get(analysis, "piglinCounter")
                        && !(Boolean) get(analysis, "renderDistance"), "Timer/counter/render-distance defaults changed");
                savedDistance = client.options.viewDistance; savedPerspective = client.options.perspective;
                client.options.guiScale = 2; client.onResolutionChanged();
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                mode(5); seek(handler, 0); break;
            case 1:
                require(!handler.isCameraView(), "Faithful mode did not follow the recorded player");
                Object state = state(0);
                require((Float) get(state, "cooldown0") == 0.25F && (Integer) get(state, "indicator") == 1,
                        "Recorded attack indicator state missing");
                require(client.options.viewDistance == 8, "Recorded render distance was not applied");
                recordedDistance(8);
                Object capturedHud = get(state, "hud");
                ItemStack[] capturedItems = (ItemStack[]) get(capturedHud, "items");
                require(ItemStack.areEqual(client.player.getMainHandStack(), capturedItems[(Integer) get(capturedHud, "selected")]),
                        "Faithful first-person camera did not expose the captured main-hand item");
                require(ItemStack.areEqual(client.player.getOffHandStack(), capturedItems[40]),
                        "Faithful first-person camera did not expose the captured off-hand item");
                capture(client, "faithful-wide.png");
                invoke(controls, "toggleControls"); break;
            case 2:
                require(!(Boolean) get(controls, "shown") && (Boolean) get(controls, "revealShown"),
                        "Paused explicit hide did not leave a recovery button");
                require((Boolean) invoke(controls, "controlsManuallyHidden"), "Paused hide was not retained");
                capture(client, "faithful-hidden.png");
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Show controls"));
                invoke(controls, "openTimeJump"); break;
            case 3:
                require(client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("GoToTimeScreen"),
                        "Go-to-time dialog did not open");
                TextFieldWidget input = (TextFieldWidget) get(client.currentScreen, "time");
                ButtonWidget go = (ButtonWidget) get(client.currentScreen, "jump");
                input.setText("invalid"); require(!go.active, "Invalid time accepted");
                input.setText("99999999"); require(!go.active, "After-end time accepted");
                input.setText(String.format(java.util.Locale.ROOT, "%.3f", (base + 50) / 1000.0));
                require(go.active, "Valid replay time rejected"); go.onPress(); break;
            case 4:
                require(client.currentScreen == null && handler.getReplaySender().paused(), "Go-to changed paused state");
                require(Math.abs(handler.getReplaySender().currentTimeStamp() - base - 50) <= 1, "Go-to sought wrong time");
                seek(handler, 1); break;
            case 5:
                if (!automaticChecks(client, handler, 1)) return false;
                capture(client, "faithful-inventory.png");
                bindInventory(client); key(client, GLFW.GLFW_KEY_I); break;
            case 6:
                require(client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("Inspection"),
                        "Rebound inventory key did not open manual inspection");
                Screen inspection = client.currentScreen;
                require(!(Boolean) get(controls, "shown") && !(Boolean) get(controls, "revealShown"),
                        "Manual Inspection did not remain modal");
                int inspectionTime = handler.getReplaySender().currentTimeStamp();
                inspection.mouseClicked(timelineX, timelineY, 0);
                inspection.mouseReleased(timelineX, timelineY, 0);
                require(handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == inspectionTime,
                        "Manual Inspection leaked a click to the timeline");
                inspection.mouseClicked(inspection.width / 2.0, inspection.height / 2.0, 0);
                ItemStack[] slots = (ItemStack[]) get(state(1), "slots");
                require(slots[0].getItem() == Items.DIAMOND && slots[0].getCount() == 3, "Inspection mutated recorded slots");
                require(((ItemStack) get(state(1), "cursor")).getCount() == 2, "Inspection mutated cursor stack");
                key(client, GLFW.GLFW_KEY_I);
                require(client.currentScreen == null, "Rebound inventory key did not close inspection");
                restoreBinding(client);
                seek(handler, 2); break;
            case 7:
                if (!automaticChecks(client, handler, 2)) return false;
                capture(client, "faithful-crafting.png"); seek(handler, 3); break;
            case 8:
                // HUD checks span frames; do not restart the completed container checks.
                if (hudStep == 0 && automaticStep != -1) {
                    if (!automaticChecks(client, handler, 3)) return false;
                    automaticStep = -1;
                }
                if (!hudChecks(client, handler, "faithful-chest-hud", 3)) return false;
                automaticStep = 0;
                require(((ItemStack[]) get(state(3), "slots")).length == 63, "Automatic chest lost its slots");
                capture(client, "faithful-chest.png"); seek(handler, 4); break;
            case 9:
                require((Boolean) get(state(0), "paused"), "Recorded pause state missing");
                capture(client, "faithful-paused.png"); seek(handler, 5); break;
            case 10:
                require((Boolean) get(state(0), "hidden"), "Recorded F1 state missing");
                require((Boolean) Class.forName("zsgrooms.replayviewer.ReplayViewer").getMethod("faithfulHudHidden").invoke(null),
                        "Recorded F1 did not hide the faithful HUD");
                if (hudStep == 0) require((Boolean) get(controls, "shown"), "Recorded F1 incorrectly hid replay controls");
                if (!hudChecks(client, handler, "faithful-f1-hud", 5)) return false;
                seek(handler, 2); break;
            case 11:
                if (!distanceRewound) {
                    state(2); recordedDistance(8);
                    distanceRewound = true; seek(handler, 6);
                    next = System.nanoTime() + 750000000L; return false;
                }
                require((Integer) get(state(0), "perspective") == 2 && client.options.perspective == 2, "Recorded F5 not applied");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480); break;
            case 12:
                capture(client, "faithful-small.png"); mode(0); break;
            case 13:
                if (!detailsChecks(client, handler)) return false;
                mode(5); seek(handler, 0); break;
            case 14:
                state(0); handler.endReplay(); break;
            case 15:
                restored(client);
                ReplayModReplay.instance.startReplay(legacy.toFile()); break;
            case 16:
                controls = controls();
                if (controls == null) return false;
                Object index = get(controls, "recordingIndex");
                if (get(index, "recording") == null || get(index, "detailStatus").equals("Loading detailed capture")) return false;
                require(get(index, "detail") == null, "Legacy fixture unexpectedly has detailed capture");
                faithful = get(controls, "faithful"); analysis = get(controls, "analysis");
                set(analysis, "renderDistance", true);
                mode(5); seek(handler, 0); break;
            case 17:
                require(get(faithful, "state") == null && !((String) get(faithful, "unavailable")).isEmpty(),
                        "Legacy faithful mode did not report unavailable capture");
                recordedDistance(-1); capture(client, "faithful-legacy-rd.png");
                handler.endReplay(); break;
            case 18:
                restored(client);
                Screen browser = (Screen) Class.forName("zsgrooms.replayviewer.ReplayBrowserScreen")
                        .getConstructor(Screen.class).newInstance(new Object[]{null});
                client.openScreen(browser);
                set(browser, "browse", directory); invoke(browser, "load");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720); break;
            case 19:
                if ((Boolean) get(client.currentScreen, "busy")) return false;
                require(((List<?>) get(client.currentScreen, "entries")).stream().anyMatch(entry -> {
                    try { return detailed.toRealPath().equals(get(entry, "path")); } catch (Exception e) { throw new IllegalStateException(e); }
                }), "Browser did not discover the fixture folder");
                capture(client, "faithful-browser-wide.png");
                ((TextFieldWidget) get(client.currentScreen, "search")).setText("no-match-91a6c09f");
                require(((List<?>) get(client.currentScreen, "filtered")).isEmpty(), "Browser search did not filter rows");
                ((TextFieldWidget) get(client.currentScreen, "search")).setText("");
                set(client.currentScreen, "selected", detailed.toRealPath()); invoke(client.currentScreen, "refresh");
                boolean playable = client.currentScreen.children().stream().anyMatch(element -> element instanceof ButtonWidget
                        && ((ButtonWidget) element).getMessage().getString().equals("Play") && ((ButtonWidget) element).active);
                require(playable, "Browser did not enable playback for the selected fixture");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480); break;
            case 20:
                capture(client, "faithful-browser-small.png");
                ButtonWidget play = (ButtonWidget) client.currentScreen.children().stream().filter(element -> element instanceof ButtonWidget
                        && ((ButtonWidget) element).getMessage().getString().equals("Play")).findFirst().orElseThrow(() -> new IllegalStateException("Missing Play button"));
                play.onPress(); break;
            case 21:
                if (handler == null || client.world == null || client.player == null) return false;
                handler.endReplay();
                require(Arrays.equals(sourceHash, hash(source)), "Original source recording changed during faithful smoke");
                ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] PASS: synthetic faithful follow, held items, automatic inventory/crafting/chest with real timeline/cursor seeking, idle/paused/manual hide recovery, small/wide containers, modal read-only Inspection, Details inventory suppression, timer/counter/render-distance settings and eight-way snapshots, on/off/F1/explicit-hide HUD pixel evidence, pause/F5/options, legacy fallback, go-to and browser playback; original SHA-256 unchanged");
                ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] NOT CHECKED: actual recorder capture equivalence and native folder dialog");
                return true;
            default: throw new IllegalStateException("Invalid faithful smoke step");
        }
        step++; next = System.nanoTime() + 750000000L; return false;
    }

    private boolean automaticChecks(MinecraftClient client, ReplayHandler handler, int kind) throws Exception {
        String name = kind == 1 ? "inventory" : kind == 2 ? "crafting" : "chest";
        state(kind);
        recordedDistance(fixtureDistance(kind));
        next = System.nanoTime() + 750000000L;
        switch (automaticStep++) {
            case 0:
                shown(handler, "Paused automatic " + name);
                require(handler.getReplaySender().paused(), "Automatic screen resumed playback");
                frozen = handler.getReplaySender().currentTimeStamp();
                Object autoHide = get(controls, "autoHide");
                savedAutoHide = (Boolean) invoke(autoHide, "isChecked");
                invoke(autoHide, "setChecked", new Class<?>[]{boolean.class}, true);
                savedZoom = handler.getOverlay().timeline.getZoom();
                savedOffset = handler.getOverlay().timeline.getOffset();
                // Zoom only the widget, keeping its real replay length and seek callback intact.
                handler.getOverlay().timeline.setZoom(4000.0 / handler.getReplayDuration()).setOffset(base);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                moveCursor(client, 8, 8); break;
            case 1:
                frozen(handler, frozen, name + " wide");
                containerBounds(client, handler, kind, 640, 360);
                capture(client, "faithful-" + name + "-wide.png");
                timelineClick(client, handler, base + kind * 400 + 250); break;
            case 2:
                timelineResult(handler);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480); break;
            case 3:
                frozen(handler, timelineTarget, name + " after resize");
                containerBounds(client, handler, kind, 320, 240);
                capture(client, "faithful-" + name + "-small.png");
                timelineClick(client, handler, base + kind * 400 + 100); break;
            case 4:
                timelineResult(handler);
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Hide controls")); break;
            case 5:
                hidden(true, "Explicit hide on automatic " + name);
                frozen(handler, timelineTarget, name + " manually hidden");
                handler.getOverlay().setMouseVisible(false);
                handler.getOverlay().setMouseVisible(true);
                invoke(controls, "update");
                hidden(true, "Cursor release overrode explicit hide");
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Show controls")); break;
            case 6:
                shown(handler, "Eye-button recovery on " + name);
                require(handler.getReplaySender() instanceof FullReplaySender, "Idle checks require normal replay mode");
                FullReplaySender sender = (FullReplaySender) handler.getReplaySender();
                require(sender.paused() && sender.isAsyncMode(), "Idle checks must begin paused in async mode");
                // Quiesce while paused, then exercise real visibility updates at normal speed.
                // Only the idle clock advances; packet time stays inside this 400 ms fixture.
                sender.setSyncModeAndWait();
                try {
                    sender.setReplaySpeed(1);
                    require(!sender.paused(), "Normal-speed visibility checks remained paused");
                    handler.getOverlay().setMouseVisible(false);
                    invoke(controls, "update");
                    set(get(controls, "visibility"), "lastActivity", System.nanoTime() - 4000000000L);
                    invoke(controls, "update");
                    hidden(false, "Idle automatic " + name);
                    state(kind);
                    handler.getOverlay().setMouseVisible(true);
                    moveCursor(client, 8, 8);
                    invoke(controls, "update");
                    shown(handler, "Cursor reveal on automatic " + name);
                    moveCursor(client, client.getWindow().getScaledWidth() / 2, timelineY);
                    invoke(controls, "update");
                    set(get(controls, "visibility"), "lastActivity", System.nanoTime() - 4000000000L);
                    invoke(controls, "update");
                    shown(handler, "Toolbar hover must prevent idle hiding on " + name);
                    state(kind);
                } finally {
                    sender.setReplaySpeed(0);
                    sender.setAsyncMode(true);
                }
                // Rewind to a known timestamp before the cross-frame freeze assertion.
                frozen = base + kind * 400 + 50;
                require(frozen < timelineTarget, "Pause fixture must rewind the earlier timeline target");
                seek(handler, kind);
                frozen(handler, frozen, name + " pause fixture seek");
                moveCursor(client, 8, 8);
                // Expire only the visibility clock: a paused sender must keep the controls shown.
                set(get(controls, "visibility"), "lastActivity", System.nanoTime() - 4000000000L);
                invoke(controls, "update");
                shown(handler, "Paused automatic " + name + " after idle expiry"); break;
            case 7:
                frozen(handler, frozen, name + " settled pause");
                shown(handler, "Completed automatic " + name);
                handler.getOverlay().timeline.setZoom(savedZoom).setOffset(savedOffset);
                invoke(get(controls, "autoHide"), "setChecked", new Class<?>[]{boolean.class}, savedAutoHide);
                automaticStep = 0;
                ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] Automatic {}: small/wide timeline clicks, pause freeze and visibility recovery passed", name);
                return true;
            default: throw new IllegalStateException("Invalid automatic screen check");
        }
        return false;
    }

    private void shown(ReplayHandler handler, String context) throws Exception {
        require((Boolean) get(controls, "shown") && !(Boolean) get(controls, "revealShown")
                && ViewerSmokeChecks.find(handler.getOverlay(), "Analysis") != null, context + ": toolbar missing");
        require(!(Boolean) invoke(controls, "controlsManuallyHidden"), context + ": manual hide leaked");
    }

    private void hidden(boolean manual, String context) throws Exception {
        require(!(Boolean) get(controls, "shown") && (Boolean) get(controls, "revealShown"), context + ": missing hidden/reveal state");
        require((Boolean) invoke(controls, "controlsManuallyHidden") == manual, context + ": incorrect manual-hide state");
    }

    private static void frozen(ReplayHandler handler, int time, String context) {
        require(handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == time,
                context + ": paused timestamp changed from " + time + " to " + handler.getReplaySender().currentTimeStamp());
    }

    private Object layout(MinecraftClient client) throws Exception {
        java.lang.reflect.Constructor<?> constructor = Class.forName("zsgrooms.replayviewer.ViewerLayout").getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(client.getWindow().getScaledWidth());
    }

    private void containerBounds(MinecraftClient client, ReplayHandler handler, int kind, int width, int height) throws Exception {
        require(client.getWindow().getScaledWidth() == width && client.getWindow().getScaledHeight() == height,
                "Automatic container viewport did not settle at " + width + "x" + height);
        shown(handler, "Automatic container layout");
        Object layout = layout(client);
        int hudInset = (Integer) invoke(controls, "hudInset");
        require(hudInset >= 58, "Faithful container lost reserved HUD space");
        int barY = (Integer) invoke(layout, "barY", new Class<?>[]{int.class, int.class}, height, hudInset);
        int inset = (Integer) invoke(layout, "recordedScreenInset", new Class<?>[]{int.class, int.class, boolean.class}, height, hudInset, true);
        require(height - inset <= barY - 4 && barY + (Integer) get(layout, "height") <= height - hudInset,
                "Automatic container/toolbar/HUD regions overlap");
        Object slots = callStatic(Class.forName("zsgrooms.replayviewer.RecordedScreenLayout"), "create",
                new Class<?>[]{int.class, int.class}, kind, ((ItemStack[]) get(state(kind), "slots")).length);
        int slotHeight = (Integer) get(slots, "height");
        float scale = Math.min(1, Math.min((width - 16) / 176F, Math.max(1, height - inset - 40) / (float) slotHeight));
        float top = 24 + (height - inset - 24 - slotHeight * scale) / 2;
        require(scale > 0 && top - 24 * scale >= 0 && top + slotHeight * scale <= barY - 4,
                "Recorded container or cursor stack exceeds its reserved area");
        ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] Container kind={} GUI={}x{} scale={} toolbarY={} HUD={}", kind, width, height, scale, barY, hudInset);
    }

    private void timelineClick(MinecraftClient client, ReplayHandler handler, int requested) throws Exception {
        shown(handler, "Before timeline click");
        Object layout = layout(client);
        timelineY = (Integer) invoke(layout, "barY", new Class<?>[]{int.class, int.class},
                client.getWindow().getScaledHeight(), invoke(controls, "hudInset")) + (Integer) get(layout, "timelineY") + 12;
        int distance = Integer.MAX_VALUE;
        for (int x = 12; x < client.getWindow().getScaledWidth() - 12; x++) {
            int time = (Integer) invoke(handler.getOverlay().timeline, "getTimeAt", new Class<?>[]{int.class, int.class}, x, timelineY);
            if (time >= 0 && Math.abs(time - requested) < distance) {
                distance = Math.abs(time - requested); timelineX = x; timelineTarget = time;
            }
        }
        require(distance <= 15 && timelineTarget != handler.getReplaySender().currentTimeStamp(),
                "Timeline lacks a distinct pixel target inside the recorded screen interval");
        require(client.currentScreen != null && Class.forName("zsgrooms.replayviewer.mixin.OverlayScreenAccessor").isInstance(client.currentScreen),
                "Automatic recorded screen blocked ReplayMod's real input screen");
        moveCursor(client, timelineX, timelineY);
        Screen input = client.currentScreen;
        require(input.mouseClicked(timelineX, timelineY, 0), "Real overlay did not consume timeline click");
        input.mouseReleased(timelineX, timelineY, 0);
    }

    private void timelineResult(ReplayHandler handler) throws Exception {
        frozen(handler, timelineTarget, "Timeline seek");
        require(handler.getOverlay().timeline.getCursorPosition() == timelineTarget, "Timeline cursor did not follow the real seek");
        shown(handler, "After timeline seek");
        handler.getOverlay().setMouseVisible(true);
    }

    private boolean detailsChecks(MinecraftClient client, ReplayHandler handler) throws Exception {
        next = System.nanoTime() + 750000000L;
        switch (detailsStep++) {
            case 0:
                restored(client); recordedDistance(8);
                capture(client, "faithful-freecam-rd.png");
                mode(4); bindInventory(client);
                handler.getOverlay().setMouseVisible(true); break;
            case 1:
                recordedDistance(8);
                key(client, GLFW.GLFW_KEY_I);
                require((Boolean) get(get(controls, "details"), "inventoryOpen"), "Details inventory key failed"); break;
            case 2:
                require(!(Boolean) get(controls, "shown") && !(Boolean) get(controls, "revealShown"),
                        "Follow: Details inventory no longer suppresses toolbar/reveal");
                capture(client, "faithful-details-modal-small.png");
                key(client, GLFW.GLFW_KEY_I); break;
            case 3:
                require(!(Boolean) get(get(controls, "details"), "inventoryOpen"), "Details inventory did not close");
                shown(handler, "After closing Details inventory"); restoreBinding(client); return true;
            default: throw new IllegalStateException("Invalid Details check");
        }
        return false;
    }

    private boolean hudChecks(MinecraftClient client, ReplayHandler handler, String prefix, int variant) throws Exception {
        next = System.nanoTime() + 750000000L;
        if (hudStep > 0) frozen(handler, frozen, prefix);
        switch (hudStep++) {
            case 0:
                hudPrefix = prefix; frozen = base + variant * 400 + 50;
                frozen(handler, frozen, prefix + " fixture baseline");
                hudDistance = fixtureDistance(variant); recordedDistance(hudDistance);
                snapshotChecks();
                hudSettings(client, handler, true, false, false); break;
            case 1:
                capture(client, prefix + "-timers.png");
                hudSettings(client, handler, false, true, true); break;
            case 2:
                require((Boolean) get(analysis, "counterAvailable"), "Faithful piglin counter lacks its recorded-player anchor");
                capture(client, prefix + "-counter.png");
                hudSettings(client, handler, true, true, true); break;
            case 3:
                capture(client, prefix + "-both.png");
                hudSettings(client, handler, false, false, false); break;
            case 4:
                capture(client, prefix + "-off.png");
                hudSettings(client, handler, true, true, true);
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Hide controls")); break;
            case 5:
                hidden(true, "HUD explicit hide"); capture(client, prefix + "-hidden.png");
                ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Show controls")); break;
            case 6:
                shown(handler, "HUD reveal"); capture(client, prefix + "-restored.png"); break;
            case 7:
                hudSettings(client, handler, true, true, false); break;
            case 8:
                capture(client, prefix + "-distance-off.png");
                hudSettings(client, handler, false, false, true); break;
            case 9:
                capture(client, prefix + "-distance-only.png");
                hudSettings(client, handler, true, true, true); break;
            case 10:
                if (!hudPixels(client)) { hudStep--; return false; }
                hudStep = 0; return true;
            default: throw new IllegalStateException("Invalid HUD check");
        }
        return false;
    }

    private void hudSettings(MinecraftClient client, ReplayHandler handler, boolean timers, boolean pigs, boolean distance) throws Exception {
        ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
        Screen settings = client.currentScreen;
        require(settings != null && settings.getClass().getSimpleName().equals("AnalysisScreen"), "Replay settings did not open");
        invoke(settings, "openDisplayTab");
        toggle(settings, "Run timers", timers);
        toggle(settings, "Render distance", distance);
        settingsBounds(settings);
        ButtonWidget analysisTab = (ButtonWidget) settings.children().stream().filter(e -> e instanceof ButtonWidget
                && ((ButtonWidget) e).getMessage().getString().equals("Analysis")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Analysis tab"));
        analysisTab.onPress(); toggle(settings, "Piglin cluster counter", pigs);
        require((Boolean) get(analysis, "runTimers") == timers && (Boolean) get(analysis, "piglinCounter") == pigs
                        && (Boolean) get(analysis, "renderDistance") == distance,
                "Settings coupled or ignored Faithful HUD toggles");
        settings.onClose(); handler.getOverlay().setMouseVisible(true); moveCursor(client, 8, 8);
        invoke(controls, "update");
        shown(handler, "Returning from HUD settings");
    }

    private void snapshotChecks() throws Exception {
        Object original = invoke(controls, "snapshot");
        for (int flags = 0; flags < 8; flags++) {
            boolean timers = (flags & 1) != 0, pigs = (flags & 2) != 0, distance = (flags & 4) != 0;
            set(analysis, "runTimers", timers); set(analysis, "piglinCounter", pigs);
            set(analysis, "renderDistance", distance);
            Object snapshot = invoke(controls, "snapshot");
            require((Boolean) get(snapshot, "runTimers") == timers && (Boolean) get(snapshot, "piglins") == pigs
                            && (Boolean) get(snapshot, "renderDistance") == distance,
                    "Snapshot lost independent Faithful HUD flags");
            set(analysis, "runTimers", !timers); set(analysis, "piglinCounter", !pigs);
            set(analysis, "renderDistance", !distance);
            invoke(controls, "restore", new Class<?>[]{snapshot.getClass()}, snapshot);
            require((Boolean) get(analysis, "runTimers") == timers && (Boolean) get(analysis, "piglinCounter") == pigs
                            && (Boolean) get(analysis, "renderDistance") == distance,
                    "Restore lost independent Faithful HUD flags");
        }
        invoke(controls, "restore", new Class<?>[]{original.getClass()}, original);
    }

    private static void toggle(Screen screen, String label, boolean enabled) {
        CheckboxWidget box = (CheckboxWidget) screen.children().stream().filter(e -> e instanceof CheckboxWidget
                && ((CheckboxWidget) e).getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing checkbox: " + label));
        require(box.visible && box.active, "Checkbox not interactive: " + label);
        if (box.isChecked() != enabled) require(screen.mouseClicked(box.x + 5, box.y + 5, 0), "Checkbox click missed: " + label);
        screen.mouseReleased(box.x + 5, box.y + 5, 0);
        require(box.isChecked() == enabled, "Checkbox did not change: " + label);
    }

    private static void settingsBounds(Screen screen) {
        List<AbstractButtonWidget> visible = new ArrayList<>();
        for (net.minecraft.client.gui.Element child : screen.children()) if (child instanceof AbstractButtonWidget) {
            AbstractButtonWidget button = (AbstractButtonWidget) child;
            if (!button.visible) continue;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= screen.width
                    && button.y + button.getHeight() <= screen.height, "Settings control outside viewport: " + button.getMessage().getString());
            for (AbstractButtonWidget other : visible) require(button.x + button.getWidth() <= other.x
                    || other.x + other.getWidth() <= button.x || button.y + button.getHeight() <= other.y
                    || other.y + other.getHeight() <= button.y, "Settings controls overlap");
            visible.add(button);
        }
    }

    private boolean hudPixels(MinecraftClient client) throws Exception {
        String[] names = {"timers", "counter", "both", "off", "hidden", "restored", "distance-off", "distance-only"};
        BufferedImage[] images = new BufferedImage[names.length];
        for (int i = 0; i < names.length; i++) {
            Path path = screenshotPath(client, hudPrefix + "-" + names[i] + ".png");
            if (!Files.exists(path)) return false;
            try { images[i] = ImageIO.read(path.toFile()); }
            catch (java.io.IOException pendingWrite) { return false; }
            if (images[i] == null) return false;
            require(images[i].getWidth() == client.getWindow().getFramebufferWidth()
                    && images[i].getHeight() == client.getWindow().getFramebufferHeight(), "HUD evidence has wrong viewport dimensions");
        }
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        Object timing = invoke(controls, "recordedTiming");
        Class<?> timings = Class.forName("zsgrooms.replayviewer.ReplayTimings");
        String rta = "RTA " + callStatic(timings, "clock", new Class<?>[]{long.class}, ((Number) get(timing, "rta")).longValue());
        String igt = "IGT " + callStatic(timings, "clock", new Class<?>[]{long.class}, ((Number) get(timing, "igt")).longValue());
        int left = width - 12 - Math.max(client.textRenderer.getWidth(rta), client.textRenderer.getWidth(igt));
        int rtaPixels = timerPixels(images, width, height, left, 10, 0xFFFFFF);
        int igtPixels = timerPixels(images, width, height, left, 21, 0xA8D8FF);
        int count = (Integer) get(analysis, "piglins");
        require((Boolean) get(analysis, "counterAvailable"), "Counter unavailable during HUD evidence check");
        int faceLeft = width - 12 - (Integer) callStatic(Class.forName("zsgrooms.replayviewer.PiglinVisuals"),
                "counterWidth", new Class<?>[]{int.class, boolean.class}, count, true);
        int[] palette = {0x503A32, 0x241C18, 0xD39A7D, 0xA96F55, 0xE8B392, 0xFFE8BC, 0x9B6254};
        int facePixels = 0, colors = 0, hiddenPixels = 0;
        for (int color : palette) {
            boolean found = false;
            for (int y = 36; y < 48; y++) for (int x = faceLeft; x < faceLeft + 16; x++) {
                if (pixel(images[2], x, y, width, height) != color || pixel(images[3], x, y, width, height) == color) continue;
                require(pixel(images[1], x, y, width, height) == color && pixel(images[5], x, y, width, height) == color,
                        "Counter changed when only timers toggled or replay controls were restored");
                require(pixel(images[0], x, y, width, height) != color, "Counter remained visible with its setting off");
                if (pixel(images[4], x, y, width, height) != color) hiddenPixels++;
                facePixels++; found = true;
            }
            if (found) colors++;
        }
        require(facePixels >= 64 && colors >= 4 && hiddenPixels >= 64,
                "Piglin face lacks meaningful on/off/explicit-hide pixel evidence: " + facePixels + "/" + colors + "/" + hiddenPixels);
        recordedDistance(hudDistance);
        int distanceIcon = distancePixels(images, width, height, 12, 24, 10, 22, 81);
        int distanceLabel = distancePixels(images, width, height, 30,
                30 + client.textRenderer.getWidth("RD " + hudDistance + " chunks"), 10, 19, 24);
        ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] {} pixel evidence: RTA={}, IGT={}, piglin face={}, colors={}, hidden={}",
                hudPrefix, rtaPixels, igtPixels, facePixels, colors, hiddenPixels);
        ZsgRooms.LOGGER.info("[ReplayFaithfulSmoke] {} RD {} chunks: icon={}, label={}, independent toggles and explicit-hide checked",
                hudPrefix, hudDistance, distanceIcon, distanceLabel);
        return true;
    }

    private static int distancePixels(BufferedImage[] images, int width, int height,
                                      int left, int right, int top, int bottom, int minimum) {
        int pixels = 0, hidden = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            int on = pixel(images[2], x, y, width, height), off = pixel(images[6], x, y, width, height);
            boolean icon = left == 12;
            if (icon) {
                int dx = x - left, dy = y - top;
                if (dx % 4 == 3 || dy % 4 == 3) continue;
                int expected = dx / 4 == 1 && dy / 4 == 1 ? 0x77DCC8 : 0xB8C1C0;
                require(on == expected && off != expected, "RD grid is missing or remains visible with its setting disabled");
            } else if (!fontColor(on, 0xE8EEED) || fontColor(off, 0xE8EEED)) continue;
            require(pixel(images[1], x, y, width, height) == on && pixel(images[5], x, y, width, height) == on
                            && pixel(images[7], x, y, width, height) == on,
                    "RD pixels changed when other HUD flags toggled or replay controls were restored");
            int timersOnly = pixel(images[0], x, y, width, height), disabled = pixel(images[3], x, y, width, height);
            require(icon ? timersOnly != on && disabled != on
                            : !fontColor(timersOnly, 0xE8EEED) && !fontColor(disabled, 0xE8EEED),
                    "RD remained visible with its setting disabled");
            int explicitlyHidden = pixel(images[4], x, y, width, height);
            if (icon ? explicitlyHidden != on : !fontColor(explicitlyHidden, 0xE8EEED)) hidden++;
            pixels++;
        }
        require(pixels >= minimum && hidden >= minimum,
                "RD lacks independent on/off/explicit-hide evidence at x=" + left + ": " + pixels + "/" + hidden);
        return pixels;
    }

    private static int fixtureDistance(int variant) { return variant == 3 ? 12 : variant == 5 ? 16 : 8; }

    private void recordedDistance(int expected) throws Exception {
        require((Integer) invoke(controls, "recordedRenderDistance") == expected,
                "Recorded render distance did not match fixture/seek: expected " + expected);
    }

    private static int timerPixels(BufferedImage[] images, int width, int height, int left, int top, int color) {
        int pixels = 0, hidden = 0;
        for (int y = top; y < top + 9; y++) for (int x = left; x < width - 12; x++) {
            int glyph = pixel(images[2], x, y, width, height);
            if (!fontColor(glyph, color) || fontColor(pixel(images[3], x, y, width, height), color)) continue;
            require(pixel(images[0], x, y, width, height) == glyph && pixel(images[5], x, y, width, height) == glyph,
                    "Timer glyphs changed when only counter toggled or replay controls were restored");
            require(!fontColor(pixel(images[1], x, y, width, height), color), "Timer remained visible with Run timers disabled");
            if (!fontColor(pixel(images[4], x, y, width, height), color)) hidden++;
            pixels++;
        }
        require(pixels >= 24 && hidden >= 24, "Timer lacks on/off/explicit-hide glyph evidence at y=" + top + ": " + pixels + "/" + hidden);
        return pixels;
    }

    private static boolean fontColor(int actual, int expected) {
        // Captured font glyphs are FCFCFC/A6D5FC instead of requested FFFFFF/A8D8FF.
        return Math.abs((actual >> 16 & 255) - (expected >> 16 & 255)) <= 3
                && Math.abs((actual >> 8 & 255) - (expected >> 8 & 255)) <= 3
                && Math.abs((actual & 255) - (expected & 255)) <= 3;
    }

    private static int pixel(BufferedImage image, int x, int y, int width, int height) {
        return image.getRGB((int) ((x + 0.5) * image.getWidth() / width),
                (int) ((y + 0.5) * image.getHeight() / height)) & 0xFFFFFF;
    }

    private void bindInventory(MinecraftClient client) {
        require(inventoryBinding == null, "Inventory binding was not restored");
        inventoryBinding = InputUtil.fromTranslationKey(client.options.keyInventory.getBoundKeyTranslationKey());
        client.options.keyInventory.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_I));
        KeyBinding.updateKeysByCode();
    }

    private void restoreBinding(MinecraftClient client) {
        if (inventoryBinding == null) return;
        client.options.keyInventory.setBoundKey(inventoryBinding); KeyBinding.updateKeysByCode(); inventoryBinding = null;
    }

    private static void moveCursor(MinecraftClient client, int x, int y) throws Exception {
        double nativeX = (double) x * client.getWindow().getWidth() / client.getWindow().getScaledWidth();
        double nativeY = (double) y * client.getWindow().getHeight() / client.getWindow().getScaledHeight();
        GLFW.glfwSetCursorPos(client.getWindow().getHandle(), nativeX, nativeY);
        // Route the native event too, so focus loss cannot leave Minecraft's cursor coordinates stale.
        invoke(client.mouse, "onCursorPos", new Class<?>[]{long.class, double.class, double.class},
                client.getWindow().getHandle(), nativeX, nativeY);
        require(Math.abs(client.mouse.getX() - nativeX) < 1 && Math.abs(client.mouse.getY() - nativeY) < 1,
                "Replay cursor did not move to the requested input point");
    }

    private static byte[] hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    private Object state(int kind) throws Exception {
        Object state = get(faithful, "state");
        require(state != null && (Integer) get(state, "screen") == kind, "Missing faithful screen kind " + kind + " at step " + step);
        return state;
    }
    private void restored(MinecraftClient client) {
        require(client.options.viewDistance == savedDistance && client.options.perspective == savedPerspective,
                "Faithful follow did not restore viewer presentation options");
    }
    private void mode(int mode) throws Exception { get(controls, "camera").getClass().getMethod("setSelected", int.class).invoke(get(controls, "camera"), mode); }
    private void seek(ReplayHandler handler, int variant) throws Exception {
        clientScreenClear(); handler.doJump(base + variant * 400 + 50, true);
        handler.getReplaySender().setReplaySpeed(0); handler.getOverlay().setMouseVisible(true);
    }
    private static void clientScreenClear() { MinecraftClient.getInstance().openScreen(null); }
    private static Object controls() throws Exception {
        Class<?> viewer = Class.forName("zsgrooms.replayviewer.ReplayViewer");
        Field instance = viewer.getDeclaredField("instance"); instance.setAccessible(true);
        Object current = instance.get(null); return current == null ? null : get(current, "controls");
    }
    private static void key(MinecraftClient client, int key) throws Exception {
        Method method = client.keyboard.getClass().getDeclaredMethod("onKey", long.class, int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(client.keyboard, client.getWindow().getHandle(), key, 0, GLFW.GLFW_PRESS, 0);
        method.invoke(client.keyboard, client.getWindow().getHandle(), key, 0, GLFW.GLFW_RELEASE, 0);
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... values) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, values);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(object.getClass().getName() + "." + name);
    }
    private static Object invoke(Object object, String name) throws Exception { return invoke(object, name, new Class<?>[0]); }
    private static Object callStatic(Class<?> type, String name, Class<?>[] types, Object... values) throws Exception {
        Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(null, values);
    }
    private Path screenshotPath(MinecraftClient client, String name) {
        return client.runDirectory.toPath().resolve("screenshots").resolve(name.replace(".png", "-" + directory.getFileName() + ".png"));
    }
    private void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, screenshotPath(client, name).getFileName().toString(), client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
