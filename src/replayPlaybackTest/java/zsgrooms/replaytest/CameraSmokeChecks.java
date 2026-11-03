package zsgrooms.replaytest;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.ReplaySender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import zsgrooms.modid.ZsgRooms;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Exercises the transformed Minecraft Camera, not just the viewer's sidecar decoder. */
final class CameraSmokeChecks {
    private static final String CAMERA = "zsg-rooms/camera.bin", STATUS = "zsg-rooms/camera.json";
    private static final int MAGIC = 0x5A43414D, AVAILABLE = 1, GAP = 2;
    private static final int MAX_FRAMES = 2000000;
    private static final float EPSILON = .005F;
    private Path source, directory, deterministic, missing;
    private byte[] sourceHash;
    private Object metadata, controls;
    private Method lookup;
    private int first = -1, later = -1, hz, sourceFrames, step, checks, distinguished, frozen;
    private int savedPerspective;
    private boolean savedHudHidden, optionsSaved, quickChecked;
    private long next;
    private float frozenYaw, frozenPitch;
    private Entity previousFocus;

    File prepare(MinecraftClient client, File original) throws Exception {
        source = original.toPath().toRealPath();
        sourceHash = hash(source);
        Class<?> api = Class.forName("zsgrooms.replayviewer.ReplayViewer");
        lookup = api.getMethod("recordedCameraRotation", Entity.class, float[].class);
        outsideReplay(null);
        Class<?> recording = Class.forName("zsgrooms.replayviewer.RaceRecording");
        metadata = call(recording, null, "read", new Class<?>[]{Path.class}, source);
        findTargets();
        require(first >= 0 && later >= 0, "Source needs two available high-rate camera spans at least 600ms apart after 2500ms");
        directory = Files.createTempDirectory(client.runDirectory.toPath(), "camera-smoke-").toRealPath();
        require(!source.startsWith(directory), "Source recording is inside the test-owned directory");
        deterministic = directory.resolve("camera-deterministic.mcpr");
        missing = directory.resolve("camera-missing.mcpr");
        fixture(deterministic, true);
        fixture(missing, false);
        require(Arrays.equals(sourceHash, hash(source)), "Preparing camera fixtures changed the source recording");
        ZsgRooms.LOGGER.info("[ReplayCameraSmoke] Source camera validated: {} records at up to {}Hz; targets {} / {}ms; isolated copies {}",
                sourceFrames, hz, first, later, directory);
        return deterministic.toFile();
    }

    private void findTargets() throws Exception {
        try (ZipFile zip = new ZipFile(source.toFile()); DataInputStream input = cameraInput(zip)) {
            hz = header(input);
            Frame previous = null, frame;
            while ((frame = read(input)) != null) {
                require(++sourceFrames <= MAX_FRAMES, "Source camera is too large for the bounded live smoke");
                validate(frame, previous);
                if (previous != null && pair(previous, frame) && frame.time - previous.time >= 2) {
                    int target = previous.time + (frame.time - previous.time) / 2;
                    Object interval = call(metadata, "intervalAt", new Class<?>[]{int.class}, target);
                    if (target >= 2500 && interval != null && (Integer) get(interval, "world") == frame.world
                            && previous.time >= (Integer) get(interval, "start") && frame.time < (Integer) get(interval, "end")
                            && !(Boolean) call(get(metadata, "loading"), "at", new Class<?>[]{int.class}, target)) {
                        if (first < 0) first = target;
                        else if (later < 0 && target >= first + 600) later = target;
                    }
                }
                previous = frame;
            }
        }
    }

    private void fixture(Path destination, boolean withCamera) throws Exception {
        require(destination.getParent().equals(directory) && !destination.equals(source), "Fixture destination is not test-owned");
        try (ZipFile input = new ZipFile(source.toFile());
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[65536];
            java.util.Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(CAMERA) || entry.getName().equals(STATUS)) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream stream = input.getInputStream(entry)) {
                    int count;
                    while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                output.closeEntry();
            }
            if (withCamera) {
                output.putNextEntry(new ZipEntry(CAMERA));
                DataOutputStream frames = new DataOutputStream(output);
                frames.writeInt(MAGIC); frames.writeInt(1); frames.writeInt(hz);
                try (DataInputStream camera = cameraInput(input)) {
                    require(header(camera) == hz, "Camera header changed while copying");
                    int index = 0;
                    Frame frame;
                    while ((frame = read(camera)) != null) {
                        require(index < MAX_FRAMES, "Camera grew while copying");
                        frames.writeInt(frame.time); frames.writeInt(frame.world); frames.writeInt(frame.entity); frames.writeInt(frame.flags);
                        boolean available = (frame.flags & AVAILABLE) != 0;
                        frames.writeFloat(available ? (index % 2 == 0 ? 171.375F : -174.625F) : frame.yaw);
                        frames.writeFloat(available ? (index % 2 == 0 ? -37.25F : 49.75F) : frame.pitch);
                        index++;
                    }
                    require(index == sourceFrames, "Camera frame count changed while copying");
                }
                output.closeEntry();
            }
        }
    }

    boolean tick(MinecraftClient client) throws Exception {
        if (System.nanoTime() < next) return false;
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        try {
            switch (step) {
                case 0:
                    if (!ready(handler, client, true)) return false;
                    savedPerspective = client.options.perspective;
                    savedHudHidden = client.options.hudHidden;
                    optionsSaved = true;
                    seek(handler, client, first);
                    break;
                case 1:
                    checkModes(handler, client, true, "initial paused Full");
                    mode(client, 2, 0);
                    frozen = handler.getReplaySender().currentTimeStamp();
                    float[] angles = captured(handler, client, "pause baseline");
                    frozenYaw = angles[0]; frozenPitch = angles[1];
                    client.options.hudHidden = true;
                    break;
                case 2:
                    require(handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == frozen,
                            "Paused playback clock advanced between rendered frames");
                    float[] held = captured(handler, client, "paused with F1");
                    angle(frozenYaw, held[0], "Paused/F1 yaw drift");
                    close(frozenPitch, held[1], "Paused/F1 pitch drift");
                    client.options.hudHidden = savedHudHidden;
                    fallbacks(handler, client);
                    seek(handler, client, later);
                    break;
                case 3:
                    checkModes(handler, client, true, "forward seek Full");
                    seek(handler, client, first);
                    break;
                case 4:
                    checkModes(handler, client, true, "backward seek Full");
                    if (Boolean.getBoolean("zsgrooms.replayCameraQuickSmoke")) {
                        call(get(controls, "playback"), "setQuick", new Class<?>[]{boolean.class}, true);
                    } else {
                        ZsgRooms.LOGGER.info("[ReplayCameraSmoke] Quick indexing skipped; enable zsgrooms.replayCameraQuickSmoke for that coverage");
                        step = 6;
                        return false;
                    }
                    break;
                case 5:
                    if ((Boolean) call(get(controls, "playback"), "busy")) return false;
                    require(handler.isQuickMode(), "Quick mode did not initialize: " + call(get(controls, "playback"), "status"));
                    require(handler.getReplaySender().paused(), "Quick mode switch resumed playback");
                    seek(handler, client, later);
                    step = 8;
                    next = System.nanoTime() + 200000000L;
                    return false;
                case 6:
                    previousFocus = client.getCameraEntity();
                    handler.endReplay();
                    outsideReplay(previousFocus);
                    controls = null;
                    break;
                case 7:
                    ReplayModReplay.instance.startReplay(missing.toFile());
                    step = 10;
                    return false;
                case 8:
                    checkModes(handler, client, true, "forward seek Quick");
                    seek(handler, client, first);
                    break;
                case 9:
                    checkModes(handler, client, true, "backward seek Quick");
                    quickChecked = true;
                    step = 6;
                    return false;
                case 10:
                    if (!ready(handler, client, false)) return false;
                    seek(handler, client, first);
                    break;
                case 11:
                    require(get(get(controls, "recordingIndex"), "camera") == null, "Missing-track archive unexpectedly loaded a camera track");
                    checkModes(handler, client, false, "missing sidecar");
                    previousFocus = client.getCameraEntity();
                    handler.endReplay();
                    outsideReplay(previousFocus);
                    restore(client);
                    require(distinguished > 0, "Deterministic camera never differed from packet-derived angles");
                    require(Arrays.equals(sourceHash, hash(source)), "Camera smoke changed the original recording");
                    ZsgRooms.LOGGER.info("[ReplayCameraSmoke] PASS: {} independent Camera.update checks; modes 2/4/5, primitive yaw/pitch hook, packet distinction, no entity/options mutation, paused/F1 stability, forward/back seeks, freecam/drone/F5 and missing-track fallback, outside replay, Quick={}, source SHA-256 unchanged",
                            checks, quickChecked ? "checked" : "skipped");
                    return true;
                default: throw new IllegalStateException("Unknown camera smoke step " + step);
            }
            step++;
            next = System.nanoTime() + 300000000L;
            return false;
        } catch (Exception failure) {
            restore(client);
            throw new IllegalStateException("Camera smoke step " + step, failure);
        }
    }

    private boolean ready(ReplayHandler handler, MinecraftClient client, boolean withCamera) throws Exception {
        if (handler == null || client.world == null || client.player == null || handler.getCameraEntity() == null
                || handler.getReplaySender().currentTimeStamp() < 2500 || !handler.getReplaySender().isAsyncMode()
                || handler.getReplaySender() instanceof FullReplaySender && ((FullReplaySender) handler.getReplaySender()).isHurrying()) return false;
        Object instance = staticField(Class.forName("zsgrooms.replayviewer.ReplayViewer"), "instance");
        controls = instance == null ? null : get(instance, "controls");
        if (controls == null) return false;
        Object index = get(controls, "recordingIndex");
        Thread worker = (Thread) get(index, "worker");
        if (get(index, "recording") == null || withCamera && get(index, "camera") == null) {
            require(worker.isAlive(), "Recording metadata/camera failed to load in live viewer");
            return false;
        }
        return withCamera || !worker.isAlive();
    }

    private void seek(ReplayHandler handler, MinecraftClient client, int target) throws Exception {
        ReplaySender sender = handler.getReplaySender();
        require(sender.isAsyncMode(), "Smoke seek requires the normal async playback context");
        sender.setReplaySpeed(0);
        handler.doJump(target, true);
        handler.getReplaySender().setReplaySpeed(0);
        client.openScreen(null);
        handler.getOverlay().setMouseVisible(true);
        mode(client, 2, 0);
    }

    private void checkModes(ReplayHandler handler, MinecraftClient client, boolean available, String label) throws Exception {
        for (int mode : new int[]{2, 4, 5}) {
            mode(client, mode, 0);
            require(!handler.isCameraView(), label + ": mode " + mode + " did not follow the player");
            if (available) captured(handler, client, label + " mode " + mode);
            else fallback(handler, client, false, false, label + " mode " + mode);
        }
    }

    private float[] captured(ReplayHandler handler, MinecraftClient client, String label) throws Exception {
        Entity focus = client.getCameraEntity();
        require(focus instanceof PlayerEntity && !focus.hasVehicle() && !((PlayerEntity) focus).isSleeping(),
                label + ": fixture player is unavailable, mounted or sleeping");
        require(focus.getUuid().equals(get(metadata, "recorder")), label + ": camera follows a different player");
        Expected expected = expected(handler.getReplaySender().currentTimeStamp());
        require(expected != null && focus.getEntityId() == expected.entity, label + ": seek lacks matching camera coverage");
        Object interval = call(get(get(controls, "recordingIndex"), "recording"), "intervalAt",
                new Class<?>[]{int.class}, handler.getReplaySender().currentTimeStamp());
        require(interval != null && (Integer) get(interval, "world") == expected.world, label + ": wrong recorded world");
        float[] result = new float[2];
        require(active(focus, result), label + ": camera API returned fallback");
        angle(expected.yaw, result[0], label + " API yaw");
        close(expected.pitch, result[1], label + " API pitch");
        for (float delta : new float[]{.25F, .75F}) {
            float normalYaw = focus.getYaw(delta), normalPitch = focus.getPitch(delta);
            if (Math.abs(wrap(expected.yaw - normalYaw)) > 1 || Math.abs(expected.pitch - normalPitch) > 1) distinguished++;
            Camera camera = render(handler, client, focus, false, false, delta, label);
            angle(expected.yaw, camera.getYaw(), label + " rendered yaw");
            close(expected.pitch, camera.getPitch(), label + " rendered pitch");
        }
        return result;
    }

    private void fallbacks(ReplayHandler handler, MinecraftClient client) throws Exception {
        for (int mode : new int[]{0, 1, 3}) {
            mode(client, mode, 0);
            require(handler.isCameraView(), "Freecam/drone did not use the camera entity");
            fallback(handler, client, false, false, "freecam/drone mode " + mode);
        }
        for (int perspective : new int[]{1, 2}) {
            mode(client, 2, perspective);
            fallback(handler, client, true, perspective == 2, "F5 perspective " + perspective);
        }
        mode(client, 2, 0);
    }

    private void fallback(ReplayHandler handler, MinecraftClient client, boolean thirdPerson, boolean inverse, String label) throws Exception {
        Entity focus = client.getCameraEntity();
        require(focus != null, label + ": missing camera entity");
        float[] unchanged = {123, 45};
        require(!active(focus, unchanged), label + ": camera API active in a fallback context");
        require(Arrays.equals(unchanged, new float[]{123, 45}), label + ": failed lookup changed its output");
        float delta = .35F, yaw = focus.getYaw(delta), pitch = focus.getPitch(delta);
        if (inverse) { yaw += 180; pitch = -pitch; }
        Camera camera = render(handler, client, focus, thirdPerson, inverse, delta, label);
        angle(yaw, camera.getYaw(), label + " vanilla yaw");
        close(pitch, camera.getPitch(), label + " vanilla pitch");
    }

    private Camera render(ReplayHandler handler, MinecraftClient client, Entity focus, boolean third, boolean inverse,
                          float delta, String label) throws Exception {
        Entity proxy = handler.getCameraEntity();
        float[] focusBefore = angles(focus), proxyBefore = angles(proxy);
        int perspective = client.options.perspective, distance = client.options.viewDistance;
        boolean hidden = client.options.hudHidden;
        Camera camera = new Camera();
        camera.update(client.world, focus, third, inverse, delta);
        require(Arrays.equals(focusBefore, angles(focus)), label + ": Camera.update mutated recorded entity angles");
        require(Arrays.equals(proxyBefore, angles(proxy)), label + ": Camera.update mutated camera proxy angles");
        require(perspective == client.options.perspective && distance == client.options.viewDistance
                && hidden == client.options.hudHidden, label + ": Camera.update changed game options");
        checks++;
        return camera;
    }

    private void mode(MinecraftClient client, int selected, int perspective) throws Exception {
        Object dropdown = get(controls, "camera");
        dropdown.getClass().getMethod("setSelected", int.class).invoke(dropdown, selected);
        client.openScreen(null);
        call(controls, "update");
        // Faithful Follow may own F5; select the test perspective after its per-frame update.
        client.options.perspective = perspective;
    }

    private boolean active(Entity focused, float[] rotation) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        Entity proxy = handler == null ? null : handler.getCameraEntity();
        float[] focusBefore = focused == null ? null : angles(focused), proxyBefore = proxy == null ? null : angles(proxy);
        int perspective = client.options.perspective, distance = client.options.viewDistance;
        boolean hidden = client.options.hudHidden;
        boolean result = (Boolean) lookup.invoke(null, focused, rotation);
        require(focused == null || Arrays.equals(focusBefore, angles(focused)), "Camera lookup mutated focus entity angles");
        require(proxy == null || Arrays.equals(proxyBefore, angles(proxy)), "Camera lookup mutated proxy entity angles");
        require(perspective == client.options.perspective && distance == client.options.viewDistance
                && hidden == client.options.hudHidden, "Camera lookup changed game options");
        return result;
    }

    void timeout(MinecraftClient client) {
        restore(client);
        throw new IllegalStateException("[ReplayCameraSmoke] Camera smoke timed out at step " + step);
    }

    private void outsideReplay(Entity stale) throws Exception {
        require(ReplayModReplay.instance.getReplayHandler() == null, "Outside-replay check still has an active replay");
        float[] result = {123, 45};
        require(!active(stale, result) && Arrays.equals(result, new float[]{123, 45}), "Camera hook active/changed output outside replay");
    }

    /** Independent streaming oracle: never invokes CameraTrack or the viewer lookup for expected angles. */
    private Expected expected(int time) throws Exception {
        try (ZipFile zip = new ZipFile(deterministic.toFile()); DataInputStream input = cameraInput(zip)) {
            header(input);
            Frame a = null, b;
            while ((b = read(input)) != null) {
                if (b.time > time) {
                    if (a == null || !pair(a, b)) return null;
                    double fraction = (double) (time - a.time) / (b.time - a.time);
                    return new Expected(a.world, a.entity, (float) (a.yaw + fraction * wrap(b.yaw - a.yaw)),
                            (float) (a.pitch + fraction * (b.pitch - a.pitch)));
                }
                if (b.time == time) return (b.flags & AVAILABLE) != 0 ? new Expected(b.world, b.entity, b.yaw, b.pitch) : null;
                a = b;
            }
            return null;
        }
    }

    private static boolean pair(Frame a, Frame b) {
        return (a.flags & AVAILABLE) != 0 && (b.flags & AVAILABLE) != 0 && (b.flags & GAP) == 0
                && a.world == b.world && a.entity == b.entity && b.time > a.time && b.time - a.time <= 100;
    }

    private void validate(Frame frame, Frame previous) throws Exception {
        require(frame.time >= 0 && (previous == null || frame.time > previous.time)
                && frame.time <= (Integer) get(metadata, "duration") && frame.world >= -1 && frame.entity >= -1
                && (frame.flags & ~(AVAILABLE | GAP)) == 0, "Invalid real recorder camera frame");
        if ((frame.flags & AVAILABLE) != 0) require(frame.world >= 0 && frame.entity >= 0
                && Float.isFinite(frame.yaw) && Float.isFinite(frame.pitch) && Math.abs(frame.pitch) <= 90,
                "Invalid available real recorder camera angles/identity");
    }

    private static int header(DataInputStream input) throws IOException {
        require(input.readInt() == MAGIC && input.readInt() == 1, "Unsupported real recorder camera header");
        int hz = input.readInt();
        require(hz == 60 || hz == 120 || hz == 240, "Real recorder camera rate is not 60/120/240Hz");
        return hz;
    }

    private static DataInputStream cameraInput(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(CAMERA);
        require(entry != null && entry.getSize() <= 12L + MAX_FRAMES * 24L, "Source camera sidecar missing/too large");
        return new DataInputStream(new BufferedInputStream(zip.getInputStream(entry)));
    }

    private static Frame read(DataInputStream input) throws IOException {
        int first = input.read();
        if (first < 0) return null;
        int time = first << 24 | input.readUnsignedByte() << 16 | input.readUnsignedByte() << 8 | input.readUnsignedByte();
        return new Frame(time, input.readInt(), input.readInt(), input.readInt(), input.readFloat(), input.readFloat());
    }

    private static float[] angles(Entity entity) {
        require(entity instanceof LivingEntity, "Smoke camera focus/proxy is not a living entity");
        LivingEntity living = (LivingEntity) entity;
        float[] result = {entity.yaw, entity.pitch, entity.prevYaw, entity.prevPitch, living.headYaw, living.prevHeadYaw,
                living.bodyYaw, living.prevBodyYaw, 0, 0, 0, 0};
        if (entity instanceof ClientPlayerEntity) {
            ClientPlayerEntity player = (ClientPlayerEntity) entity;
            result[8] = player.renderYaw; result[9] = player.lastRenderYaw;
            result[10] = player.renderPitch; result[11] = player.lastRenderPitch;
        }
        return result;
    }

    private void restore(MinecraftClient client) {
        if (optionsSaved) {
            client.options.perspective = savedPerspective;
            client.options.hudHidden = savedHudHidden;
        }
    }

    private static float wrap(float value) {
        value %= 360;
        return value >= 180 ? value - 360 : value < -180 ? value + 360 : value;
    }

    private static void angle(float expected, float actual, String label) { close(0, wrap(actual - expected), label); }
    private static void close(float expected, float actual, String label) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) <= EPSILON, label + ": expected " + expected + ", actual " + actual);
    }

    private static byte[] hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), target, name); }
    private static Object staticField(Class<?> type, String name) throws Exception { return field(type, null, name); }
    private static Object field(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return call(target.getClass(), target, name, types, args);
    }
    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private static Object call(Class<?> type, Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static final class Frame {
        final int time, world, entity, flags;
        final float yaw, pitch;
        Frame(int time, int world, int entity, int flags, float yaw, float pitch) {
            this.time = time; this.world = world; this.entity = entity; this.flags = flags;
            this.yaw = yaw; this.pitch = pitch;
        }
    }
    private static final class Expected {
        final int world, entity;
        final float yaw, pitch;
        Expected(int world, int entity, float yaw, float pitch) {
            this.world = world; this.entity = entity; this.yaw = yaw; this.pitch = pitch;
        }
    }
}
