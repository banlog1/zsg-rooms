package zsgrooms.replaytest;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import zsgrooms.modid.ZsgRooms;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Dev-only checks of real recorder output and transformed Minecraft render paths on isolated copies. */
final class SuperDetailedSmokeChecks {
    private static final String PRESENTATION = "zsg-rooms/presentation.bin", SOUNDS = "zsg-rooms/local-sounds.bin";
    private static final int MAX_FRAMES = 200000, MAX_EVENTS = 100000, MAGIC = 0x5A505253;
    private static final String[] SCALARS = {"worldZoom", "worldZoomX", "worldZoomY", "handZoom", "handZoomX", "handZoomY",
            "bobPhase", "bobStride", "pitch", "swayPitch", "swayYaw", "swing", "mainEquip", "offEquip", "useRemaining"};
    private final int[] times = {-1, -1}, worlds = new int[2], entities = new int[2];
    private Path source, directory, deterministic, missing;
    private byte[] sourceHash;
    private Object metadata, controls;
    private Class<?> bridge;
    private int step, renders, sourceFrames, sourceEvents, frozen, savedPerspective, savedDistance;
    private long next, deadline;
    private boolean running, optionsSaved;
    private double savedFov;
    private boolean savedBob;
    private DrawCapture hands;

    File prepare(MinecraftClient client, File original) throws Exception {
        source = original.toPath().toRealPath();
        sourceHash = hash(source);
        bridge = Class.forName("zsgrooms.replayviewer.PresentationPlayback");
        require(call(bridge, null, "current") == null && call(bridge, null, "handState") == null,
                "Presentation bridge active outside replay");
        Class<?> recording = Class.forName("zsgrooms.replayviewer.RaceRecording");
        metadata = call(recording, null, "read", new Class<?>[]{Path.class}, source);
        validateSource(recording);
        require(times[0] >= 0 && times[1] >= 0,
                "Fresh Super recording needs two first-person presentation spans after 2500ms, at least 600ms apart");
        directory = Files.createTempDirectory(client.runDirectory.toPath(), "super-detailed-smoke-").toRealPath();
        deterministic = directory.resolve("super-presentation.mcpr");
        missing = directory.resolve("super-missing.mcpr");
        fixture(deterministic, true);
        fixture(missing, false);
        require(Arrays.equals(sourceHash, hash(source)), "Fixture preparation changed the original source SHA-256");
        deadline = System.nanoTime() + 170000000000L;
        ZsgRooms.LOGGER.info("[SuperDetailedSmoke] Real source decoded: {} presentation frames, {} local sounds; targets {} / {}; copies {}",
                sourceFrames, sourceEvents, times[0], times[1], directory);
        return deterministic.toFile();
    }

    private void validateSource(Class<?> recording) throws Exception {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            for (String name : new String[]{PRESENTATION, SOUNDS}) {
                ZipEntry entry = zip.getEntry(name);
                require(entry != null && entry.getSize() >= 8 && entry.getSize() <= 32L * 1024 * 1024,
                        "Real recorder sidecar missing/too large for bounded smoke: " + name);
            }
        }
        Class<?> trackClass = Class.forName("zsgrooms.replayviewer.PresentationTrack");
        Object track = call(trackClass, null, "read", new Class<?>[]{recording}, metadata);
        require(track != null, "Real recorder presentation sidecar missing; synthetic fixtures cannot replace this evidence");
        try (ZipFile zip = new ZipFile(source.toFile())) {
            ZipEntry entry = zip.getEntry(PRESENTATION);
            require(entry != null && entry.getSize() <= 128L * 1024 * 1024, "Real presentation sidecar missing/too large for smoke");
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(zip.getInputStream(entry)))) {
                require(input.readInt() == MAGIC && input.readInt() == 1, "Unexpected real presentation header");
                int previous = -1;
                while (true) {
                    int first = input.read();
                    if (first < 0) break;
                    int time = first << 24 | input.readUnsignedByte() << 16 | input.readUnsignedByte() << 8 | input.readUnsignedByte();
                    int world = input.readInt(), entity = input.readInt(), length = input.readInt();
                    require(++sourceFrames <= MAX_FRAMES && time > previous && length >= 4 && length <= 65536,
                            "Invalid/unbounded real presentation frame");
                    previous = time;
                    byte[] payload = new byte[length]; input.readFully(payload);
                    Object state = call(Class.forName("zsgrooms.replayviewer.PresentationState"), null, "decode",
                            new Class<?>[]{byte[].class}, (Object) payload);
                    if (java.nio.ByteBuffer.wrap(payload).getInt() == 2) {
                        require(state == null, "Presentation gap decoded as an available state");
                        continue;
                    }
                    require(state != null, "Real recorder presentation payload did not decode at " + time);
                    if (((Integer) get(state, "flags") & 8) == 0 || time < 2525
                            || times[0] >= 0 && time < times[0] + 600 || times[1] >= 0) continue;
                    Object interval = call(metadata, "intervalAt", new Class<?>[]{int.class}, time);
                    if (interval == null || (Integer) get(interval, "world") != world
                            || time - 25 < (Integer) get(interval, "start") || time + 25 >= (Integer) get(interval, "end")
                            || (Boolean) call(get(metadata, "loading"), "at", new Class<?>[]{int.class}, time)) continue;
                    Object pair = call(track, "at", new Class<?>[]{double.class, int.class, int.class, int.class, int.class},
                            (double) time, world, entity, get(interval, "start"), get(interval, "end"));
                    require(pair != null, "Real presentation frame unavailable through viewer track index");
                    int slot = times[0] < 0 ? 0 : 1;
                    times[slot] = time; worlds[slot] = world; entities[slot] = entity;
                }
            }
            require((Integer) call(track, "frameCount") == sourceFrames, "Real presentation decoder lost source frames");
        } finally { call(track, "close"); }

        Class<?> soundsClass = Class.forName("zsgrooms.replayviewer.LocalSoundTrack");
        Object sounds = call(soundsClass, null, "read", new Class<?>[]{recording}, metadata);
        require(sounds != null, "Real recorder local-sound sidecar missing or incomplete");
        try {
            sourceEvents = (Integer) call(sounds, "eventCount");
            require(sourceEvents > 0 && sourceEvents <= MAX_EVENTS, "Fresh Super recording needs 1..100000 real local-sound events");
            require((Integer) call(sounds, "endTime") > 0, "Real local-sound completion missing");
            for (int i = 0; i < sourceEvents; i++) {
                Object event = call(sounds, "event", new Class<?>[]{int.class}, i);
                int mask = (Integer) call(sounds, "coverage", new Class<?>[]{int.class, int.class, int.class},
                        get(event, "time"), get(event, "world"), get(event, "entity"));
                require((mask & (Integer) get(event, "channel")) != 0 && !((String) get(event, "sound")).isEmpty(),
                        "Real local sound lost channel/identity coverage at event " + i);
            }
            soundCursor(sounds);
        } finally { call(sounds, "close"); }
    }

    private static void soundCursor(Object track) throws Exception {
        Object event = call(track, "event", new Class<?>[]{int.class}, 0);
        int time = (Integer) get(event, "time"), world = (Integer) get(event, "world"), entity = (Integer) get(event, "entity");
        Class<?> type = Class.forName("zsgrooms.replayviewer.LocalSoundCursor");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object cursor = constructor.newInstance();
        Class<?>[] signature = {track.getClass(), int.class, int.class, int.class, boolean.class, boolean.class, Consumer.class};
        List<Object> emitted = new ArrayList<>();
        Consumer<Object> sink = emitted::add;
        call(cursor, "frame", signature, track, time - 1, world, entity, false, false, sink);
        call(cursor, "frame", signature, track, time, world, entity, false, false, sink);
        require(!emitted.isEmpty(), "Real source local sounds did not traverse forward playback cursor");
        int count = emitted.size();
        call(cursor, "frame", signature, track, time, world, entity, true, false, sink);
        call(cursor, "frame", signature, track, time, world, entity, false, false, sink);
        require(emitted.size() == count, "Pause/resume duplicated real local sounds");
        call(cursor, "frame", signature, track, time - 1, world, entity, true, false, sink);
        require(emitted.size() == count, "Backward seek replayed historical real local sounds");
        call(cursor, "frame", signature, track, time, world, entity, false, false, sink);
        require(emitted.size() == count * 2, "Backward seek failed to rearm the real local sound cursor");
        call(cursor, "frame", signature, track, time + 500, world, entity, false, true, sink);
        call(cursor, "frame", signature, track, time + 500, world, entity, false, false, sink);
        require(emitted.size() == count * 2, "Blocked/forward seek emitted catch-up local sounds");
        call(cursor, "reset");
        require(!(Boolean) call(cursor, "covers", new Class<?>[]{int.class, int.class}, get(event, "channel"), entity),
                "Reset local-sound cursor retained channel suppression");
    }

    private void fixture(Path destination, boolean presentation) throws Exception {
        require(destination.getParent().equals(directory) && !source.startsWith(directory), "Fixture path is not isolated");
        try (ZipFile input = new ZipFile(source.toFile());
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[65536];
            java.util.Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals(PRESENTATION) || name.equals("zsg-rooms/presentation.json")
                        || !presentation && (name.equals(SOUNDS) || name.equals("zsg-rooms/local-sounds.json"))) continue;
                output.putNextEntry(new ZipEntry(name));
                try (InputStream stream = input.getInputStream(entry)) {
                    int count; while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                output.closeEntry();
            }
            if (presentation) {
                output.putNextEntry(new ZipEntry(PRESENTATION));
                DataOutputStream frames = new DataOutputStream(output);
                frames.writeInt(MAGIC); frames.writeInt(1);
                for (int sample = 0; sample < 2; sample++) for (int side : new int[]{-1, 1}) {
                    byte[] payload = payload(sample, side);
                    frames.writeInt(times[sample] + side * 25); frames.writeInt(worlds[sample]); frames.writeInt(entities[sample]);
                    frames.writeInt(payload.length); frames.write(payload);
                }
                output.closeEntry();
            }
        }
    }

    private static int flags(int sample) { return 1 | 4 | 8 | 512 | 1024 | (sample == 0 ? 16 : 64 | 128 | 256); }
    private static double worldFov(int sample) { return sample == 0 ? 46 : 100; }
    private static double handFov(int sample) { return sample == 0 ? 66 : 80; }
    private static float[] scalars(int sample) {
        return sample == 0 ? new float[]{1.25F, .12F, -.08F, .9F, -.06F, .04F, .5F, .4F, 18, 7, -9, .2F, .4F, .65F, 20.25F}
                : new float[]{1.5F, -.1F, .06F, 1.1F, .05F, -.03F, -.35F, .25F, -21, -6, 11, .6F, .7F, .3F, 14.25F};
    }
    private static byte[] payload(int sample, int side) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        try {
            data.writeInt(flags(sample)); data.writeDouble(worldFov(sample) + side * 3); data.writeDouble(handFov(sample) + side * 3);
            float[] values = scalars(sample);
            values[11] += side * .05F; values[12] += side * .1F; values[13] -= side * .1F; values[14] -= side * .5F;
            for (float value : values) data.writeFloat(value);
            data.writeItemStack(new ItemStack(Items.GOLDEN_APPLE)); data.writeItemStack(new ItemStack(Items.BREAD));
            data.writeItemStack(new ItemStack(sample == 0 ? Items.GOLDEN_APPLE : Items.BREAD));
            byte[] result = new byte[data.readableBytes()]; data.readBytes(result); return result;
        } finally { data.release(); }
    }

    boolean tick(MinecraftClient client) throws Exception {
        if (running) return false;
        if (System.nanoTime() > deadline) timeout(client);
        if (System.nanoTime() < next) return false;
        running = true;
        try {
            ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
            switch (step) {
                case 0:
                    if (!ready(handler, client, true)) return false;
                    savedPerspective = client.options.perspective; savedDistance = client.options.viewDistance;
                    savedFov = client.options.fov; savedBob = client.options.bobView; optionsSaved = true;
                    // Deliberately disagree with the recorded values, without touching the recorded player.
                    client.options.fov = 83; client.options.bobView = false;
                    hands = new DrawCapture(client);
                    seek(handler, client, 0); break;
                case 1:
                    captured(client, 0, "initial paused");
                    frozen = handler.getReplaySender().currentTimeStamp(); break;
                case 2:
                    require(handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == frozen,
                            "Paused replay clock advanced");
                    captured(client, 0, "paused across rendered frames");
                    fallbacks(client); seek(handler, client, 1); break;
                case 3:
                    captured(client, 1, "forward seek"); seek(handler, client, 0); break;
                case 4:
                    captured(client, 0, "backward seek");
                    handler.endReplay(); outside(client); controls = null; break;
                case 5:
                    ReplayModReplay.instance.startReplay(missing.toFile()); break;
                case 6:
                    if (!ready(handler, client, false)) return false;
                    seek(handler, client, 0); break;
                case 7:
                    require(get(get(controls, "recordingIndex"), "presentation") == null
                            && get(get(controls, "recordingIndex"), "localSounds") == null, "Missing-sidecar fixture loaded supplemental tracks");
                    fallback(client, "missing sidecars");
                    handler.endReplay(); outside(client); restore(client);
                    require(Arrays.equals(sourceHash, hash(source)), "Live checks changed original source SHA-256");
                    ZsgRooms.LOGGER.info("[SuperDetailedSmoke] PASS: real presentation/local-sound decode; {} transformed render probes; world/hand FOV+zoom, hand equip/swing/use timing/sway/arm, bob, pause, forward/back seeks, freecam/Follow/F5/missing/exit fallbacks, no render options/entity mutation, source SHA-256 unchanged; fixtures {}",
                            renders, directory);
                    return true;
                default: throw new IllegalStateException("Unknown Super Detailed smoke step " + step);
            }
            step++; next = System.nanoTime() + 300000000L;
            return false;
        } catch (Exception failure) {
            restore(client);
            throw new IllegalStateException("Super Detailed smoke step " + step + ", fixtures " + directory, failure);
        } finally { running = false; }
    }

    private boolean ready(ReplayHandler handler, MinecraftClient client, boolean supplemental) throws Exception {
        if (handler == null || client.world == null || client.player == null || handler.getCameraEntity() == null
                || handler.getReplaySender().currentTimeStamp() < 2500 || !handler.getReplaySender().isAsyncMode()
                || handler.getReplaySender() instanceof FullReplaySender && ((FullReplaySender) handler.getReplaySender()).isHurrying()) return false;
        Object viewer = getStatic(Class.forName("zsgrooms.replayviewer.ReplayViewer"), "instance");
        controls = viewer == null ? null : get(viewer, "controls");
        if (controls == null) return false;
        Object index = get(controls, "recordingIndex");
        if (((Thread) get(index, "worker")).isAlive()) return false;
        require(get(index, "recording") != null && get(index, "detail") != null, "Fresh Super fixture metadata/detail did not load");
        require(!supplemental || get(index, "presentation") != null && get(index, "localSounds") != null,
                "Live viewer failed to load real local sounds/deterministic presentation");
        return true;
    }

    private void seek(ReplayHandler handler, MinecraftClient client, int sample) throws Exception {
        handler.getReplaySender().setReplaySpeed(0);
        handler.doJump(times[sample], true);
        handler.getReplaySender().setReplaySpeed(0);
        client.openScreen(null); handler.getOverlay().setMouseVisible(true);
        mode(client, 5);
    }

    private void mode(MinecraftClient client, int selected) throws Exception {
        Object dropdown = get(controls, "camera");
        dropdown.getClass().getMethod("setSelected", int.class).invoke(dropdown, selected);
        client.openScreen(null); call(controls, "update");
    }

    private void captured(MinecraftClient client, int sample, String label) throws Exception {
        call(controls, "update");
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        require(!handler.isCameraView() && client.getCameraEntity().getEntityId() == entities[sample]
                && handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == times[sample],
                label + ": Faithful did not follow expected player/time while paused");
        List<Object> before = snapshot(client);
        Object state = call(bridge, null, "current");
        require(state != null, label + ": presentation unavailable in Faithful Follow");
        require((Integer) get(state, "flags") == flags(sample), label + ": recorded hand flags lost");
        close(worldFov(sample), (Double) get(state, "worldFov"), label + " state world FOV");
        close(handFov(sample), (Double) get(state, "handFov"), label + " state hand FOV");
        float[] values = scalars(sample);
        for (int i = 0; i < values.length; i++) close(values[i], (Float) get(state, SCALARS[i]), label + " " + SCALARS[i]);
        for (float delta : new float[]{.2F, .8F}) {
            // Reflection and cold class loading can outlast the per-frame stale-state guard.
            call(controls, "update");
            projection(client, true, worldFov(sample), values[0], values[1], values[2], delta, label);
            call(controls, "update");
            projection(client, false, handFov(sample), values[3], values[4], values[5], delta, label);
            MatrixStack bob = new MatrixStack();
            call(controls, "update");
            call(client.gameRenderer, "bobView", new Class<?>[]{MatrixStack.class, float.class}, bob, delta);
            MatrixStack expectedBob = new MatrixStack(); bob(expectedBob, values[6], values[7]);
            matrix(expectedBob.peek().getModel(), bob.peek().getModel(), label + " applied bob");
            hands.draws.clear();
            call(controls, "update");
            hands.renderItem(delta, new MatrixStack(), VertexConsumerProvider.immediate(new BufferBuilder(256)), client.player, 15728880);
            require(hands.draws.size() == 2, label + ": held renderer did not draw both recorded hands");
            for (int hand = 0; hand < 2; hand++) {
                Draw draw = hands.draws.get(hand);
                require(draw.stack.getItem() == (hand == 0 ? Items.GOLDEN_APPLE : Items.BREAD), label + ": wrong rendered item");
                boolean left = (sample == 1) == (hand == 0);
                require(draw.left == left && draw.mode == (left ? ModelTransformation.Mode.FIRST_PERSON_LEFT_HAND
                        : ModelTransformation.Mode.FIRST_PERSON_RIGHT_HAND), label + ": recorded arm was not applied");
                matrix(expectedHand(sample, hand).peek().getModel(), draw.matrix, label + " applied hand " + hand);
            }
            require(call(bridge, null, "handState") == null, label + ": hand override leaked outside draw scope");
            renders++;
        }
        require(before.equals(snapshot(client)), label + ": rendering mutated options/entity/inventory");
        close(83, client.options.fov, "Presentation mutated user FOV option");
        require(!client.options.bobView, "Presentation mutated user bob option");
    }

    private void fallbacks(MinecraftClient client) throws Exception {
        for (int selected : new int[]{0, 1, 2, 3, 4}) {
            mode(client, selected); fallback(client, "camera mode " + selected);
        }
        mode(client, 5);
        for (int perspective : new int[]{1, 2}) {
            // Set after update: Faithful's existing detailed track owns the per-frame F5 option.
            client.options.perspective = perspective;
            fallback(client, "F5 " + perspective);
        }
        client.options.perspective = 0; call(controls, "update");
    }

    private void fallback(MinecraftClient client, String label) throws Exception {
        List<Object> before = snapshot(client);
        require(call(bridge, null, "current") == null && call(bridge, null, "handState") == null,
                label + ": presentation did not fall back");
        for (boolean world : new boolean[]{true, false}) {
            double fov = (Double) call(client.gameRenderer, "getFov", new Class<?>[]{Camera.class, float.class, boolean.class},
                    client.gameRenderer.getCamera(), .35F, world);
            projection(client, world, fov, (Float) get(client.gameRenderer, "zoom"),
                    (Float) get(client.gameRenderer, "zoomX"), (Float) get(client.gameRenderer, "zoomY"), .35F, label);
        }
        require(before.equals(snapshot(client)), label + ": fallback render mutated options/entity");
        renders++;
    }

    private static void projection(MinecraftClient client, boolean world, double fov, float zoom, float x, float y,
                                   float delta, String label) throws Exception {
        MatrixStack expected = new MatrixStack();
        if (zoom != 1) { expected.translate(x, -y, 0); expected.scale(zoom, zoom, 1); }
        expected.peek().getModel().multiply(Matrix4f.viewboxMatrix(fov,
                (float) client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight(),
                .05F, (Float) get(client.gameRenderer, "viewDistance") * 4));
        matrix(expected.peek().getModel(), client.gameRenderer.getBasicProjectionMatrix(client.gameRenderer.getCamera(), delta, world),
                label + (world ? " applied world projection" : " applied hand projection"));
    }

    private static void bob(MatrixStack matrices, float phase, float stride) {
        float angle = phase * (float) Math.PI;
        matrices.translate(MathHelper.sin(angle) * stride * .5F, -Math.abs(MathHelper.cos(angle) * stride), 0);
        matrices.multiply(Vector3f.POSITIVE_Z.getDegreesQuaternion(MathHelper.sin(angle) * stride * 3));
        matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(Math.abs(MathHelper.cos(angle - .2F) * stride) * 5));
    }

    /** Independent oracle for the pinned vanilla food-use/swing/equip transforms, not viewer state getters. */
    private static MatrixStack expectedHand(int sample, int hand) {
        float[] v = scalars(sample);
        MatrixStack matrices = new MatrixStack();
        matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(v[9]));
        matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(v[10]));
        int sign = (sample == 1) == (hand == 0) ? -1 : 1;
        boolean eating = sample == 0 && hand == 0;
        float swing = sample == 1 && hand == 1 ? v[11] : 0;
        if (eating) {
            float remaining = v[14], fraction = remaining / 32;
            if (fraction < .8F) matrices.translate(0, Math.abs(MathHelper.cos(remaining / 4 * (float) Math.PI) * .1F), 0);
            float amount = 1 - (float) Math.pow(fraction, 27);
            matrices.translate(amount * .6F * sign, amount * -.5F, 0);
            matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(sign * amount * 90));
            matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(amount * 10));
            matrices.multiply(Vector3f.POSITIVE_Z.getDegreesQuaternion(sign * amount * 30));
        } else {
            matrices.translate(sign * -.4F * MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI),
                    .2F * MathHelper.sin(MathHelper.sqrt(swing) * (float) (Math.PI * 2)),
                    -.2F * MathHelper.sin(swing * (float) Math.PI));
        }
        matrices.translate(sign * .56F, -.52F + v[12 + hand] * -.6F, -.72F);
        if (!eating) {
            matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(sign * (45 - MathHelper.sin(swing * swing * (float) Math.PI) * 20)));
            float amount = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
            matrices.multiply(Vector3f.POSITIVE_Z.getDegreesQuaternion(sign * amount * -20));
            matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(amount * -80));
            matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(sign * -45));
        }
        return matrices;
    }

    private void outside(MinecraftClient client) throws Exception {
        require(ReplayModReplay.instance.getReplayHandler() == null, "Replay did not exit");
        require(call(bridge, null, "current") == null && call(bridge, null, "handState") == null,
                "Presentation override leaked outside replay");
        require(getStatic(bridge, "current") == null, "Replay exit retained presentation state");
        Object cursor = getStatic(Class.forName("zsgrooms.replayviewer.LocalSoundPlayback"), "cursor");
        require(get(cursor, "track") == null, "Replay exit retained local-sound track/cursor");
    }

    void timeout(MinecraftClient client) {
        restore(client);
        throw new IllegalStateException("Super Detailed smoke timed out at step " + step + "; fixtures " + directory);
    }

    private void restore(MinecraftClient client) {
        if (!optionsSaved) return;
        client.options.fov = savedFov; client.options.bobView = savedBob;
        client.options.perspective = savedPerspective; client.options.viewDistance = savedDistance;
    }

    private static List<Object> snapshot(MinecraftClient client) throws Exception {
        List<Object> values = new ArrayList<>();
        snapshot(values, client.options);
        snapshot(values, client.player);
        if (client.getCameraEntity() != client.player) snapshot(values, client.getCameraEntity());
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        if (handler != null && handler.getCameraEntity() != client.getCameraEntity()) snapshot(values, handler.getCameraEntity());
        if (client.player != null) for (int i = 0; i < client.player.inventory.size(); i++)
            values.add(client.player.inventory.getStack(i).toTag(new CompoundTag()));
        return values;
    }

    private static void snapshot(List<Object> values, Object object) throws Exception {
        if (object == null) return;
        for (Class<?> type = object.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!field.getType().isPrimitive() && !field.getType().isEnum() && field.getType() != ItemStack.class) continue;
                field.setAccessible(true);
                Object value = field.get(object);
                values.add(value instanceof ItemStack ? ((ItemStack) value).toTag(new CompoundTag()) : value);
            }
        }
    }

    private static void matrix(Matrix4f expected, Matrix4f actual, String label) throws Exception {
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
            String name = "a" + row + column;
            close((Float) get(expected, name), (Float) get(actual, name), label + " " + name);
        }
    }
    private static void close(double expected, double actual, String label) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) <= .0005, label + ": expected " + expected + ", actual " + actual);
    }
    private static byte[] hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }
    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    private static Object getStatic(Class<?> type, String name) throws Exception { return field(type, name).get(null); }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return call(target.getClass(), target, name, types, args);
    }
    private static Object call(Class<?> type, Object target, String name) throws Exception { return call(type, target, name, new Class<?>[0]); }
    private static Object call(Class<?> type, Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    /** Stops only the final item draw; all transformed vanilla first-person animation code still executes. */
    private static final class DrawCapture extends HeldItemRenderer {
        final List<Draw> draws = new ArrayList<>();
        DrawCapture(MinecraftClient client) { super(client); }
        @Override public void renderItem(LivingEntity entity, ItemStack stack, ModelTransformation.Mode mode, boolean left,
                                         MatrixStack matrices, VertexConsumerProvider consumers, int light) {
            draws.add(new Draw(stack.copy(), mode, left, new Matrix4f(matrices.peek().getModel())));
        }
    }
    private static final class Draw {
        final ItemStack stack;
        final ModelTransformation.Mode mode;
        final boolean left;
        final Matrix4f matrix;
        Draw(ItemStack stack, ModelTransformation.Mode mode, boolean left, Matrix4f matrix) {
            this.stack = stack; this.mode = mode; this.left = left; this.matrix = matrix;
        }
    }
}
