package zsgrooms.modid.replay;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Optional bounded sidecar. Exhausting its budget never stops packet recording. */
final class ReplayHudTrack {
    static final int MAGIC = 0x5A485544;
    static final int MAX_BYTES = 16 * 1024 * 1024;
    static final int MAX_FRAME = 64 * 1024;
    private final List<Frame> frames = new ArrayList<>();
    private int bytes = 8;
    private boolean sealed;
    private long sampled = -1;
    private int sampledWorld = -1, sampledEntity = -1;

    synchronized boolean due(long time, int world, int entity) {
        if (sealed || bytes >= MAX_BYTES || frames.size() >= 72000 || time < 0 || time > Integer.MAX_VALUE) return false;
        return sampled < 0 || time > sampled && (time - sampled >= 200 || sampledWorld != world || sampledEntity != entity);
    }

    synchronized void add(long time, int world, int entity, byte[] payload) {
        if (!due(time, world, entity)) return;
        sampled = time;
        sampledWorld = world;
        sampledEntity = entity;
        if (!frames.isEmpty()) {
            Frame last = frames.get(frames.size() - 1);
            if (last.world == world && last.entity == entity && java.util.Arrays.equals(last.payload, payload)) {
                if (time - last.time < 1000) return;
                payload = last.payload;
            }
        }
        if (payload.length > MAX_FRAME || bytes + payload.length + 16 > MAX_BYTES) {
            sealed = true;
            return;
        }
        frames.add(new Frame((int) time, world, entity, payload));
        bytes += payload.length + 16;
    }

    synchronized byte[] finish(long duration) throws IOException {
        sealed = true;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes);
        DataOutputStream output = new DataOutputStream(buffer);
        output.writeInt(MAGIC);
        output.writeInt(1);
        for (Frame frame : frames) {
            if (frame.time > duration) break;
            output.writeInt(frame.time);
            output.writeInt(frame.world);
            output.writeInt(frame.entity);
            output.writeInt(frame.payload.length);
            output.write(frame.payload);
        }
        frames.clear();
        return buffer.toByteArray();
    }

    private static final class Frame {
        final int time, world, entity;
        final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
        }
    }
}
