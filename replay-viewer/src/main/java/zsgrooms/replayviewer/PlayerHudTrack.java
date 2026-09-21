// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Seekable, bounded optional sidecar; never scans the replay's packet stream. */
final class PlayerHudTrack {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    final List<Frame> frames = new ArrayList<>();

    static PlayerHudTrack read(RaceRecording recording) throws IOException {
        try (ZipFile zip = new ZipFile(recording.path.toFile())) {
            ZipEntry entry = zip.getEntry("zsg-rooms/player-hud.bin");
            if (entry == null) return null;
            if (entry.getSize() > MAX_BYTES) throw new IOException("Oversized player HUD");
            try (InputStream input = zip.getInputStream(entry)) { return read(input, recording.duration); }
        }
    }

    static PlayerHudTrack read(InputStream input, int duration) throws IOException {
        DataInputStream data = new DataInputStream(input);
        if (data.readInt() != 0x5A485544 || data.readInt() != 1) throw new IOException("Unknown player HUD format");
        PlayerHudTrack track = new PlayerHudTrack();
        int total = 8, previous = -1;
        while (true) {
            int first = data.read();
            if (first == -1) break;
            int time = (first << 24) | (data.readUnsignedByte() << 16) | (data.readUnsignedByte() << 8) | data.readUnsignedByte();
            int world = data.readInt(), entity = data.readInt(), length = data.readInt();
            if (time <= previous || time > duration || world < -1 || length < 0 || length > 65536
                    || track.frames.size() >= 72000 || total + length + 16 > MAX_BYTES) throw new IOException("Invalid player HUD bounds");
            byte[] bytes = new byte[length];
            data.readFully(bytes);
            track.frames.add(new Frame(time, world, entity, bytes));
            previous = time;
            total += length + 16;
            if (Thread.currentThread().isInterrupted()) throw new IOException("Closed");
        }
        return track;
    }

    Frame at(int time, int world, int entity, int intervalStart) {
        int low = 0, high = frames.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (frames.get(mid).time <= time) low = mid + 1; else high = mid;
        }
        if (low == 0) return null;
        Frame frame = frames.get(low - 1);
        return frame.time < intervalStart || time - frame.time > 1500 || frame.world != world
                || frame.entity != entity || frame.payload.length == 0 ? null : frame;
    }

    static final class Frame {
        final int time, world, entity;
        final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
        }
    }
}
