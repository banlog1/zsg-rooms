// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.replay.ReplayFile;
import zsgrooms.replayviewer.mixin.DelegatingReplayFileAccessor;
import zsgrooms.replayviewer.mixin.ReplayFileAccessor;
import java.io.File;
import java.io.IOException;

/** Independent archive read, performed once per open file without scanning packet data. */
final class RecordingIndex implements AutoCloseable {
    volatile RaceRecording recording;
    volatile PlayerHudTrack hud;
    volatile DetailedTrack detail;
    volatile BarterHistory barters;
    volatile String barterStatus = "Reading barter history...";
    volatile String detailStatus = "Loading detailed capture";
    private volatile boolean closed;
    private final Thread worker;

    RecordingIndex(ReplayFile replay) {
        File file = file(replay);
        worker = new Thread(() -> {
            if (file == null) {
                detailStatus = "Detailed capture unavailable";
                barterStatus = "Barter history unavailable";
                return;
            }
            try {
                RaceRecording value = RaceRecording.read(file.toPath());
                if (closed) return;
                recording = value;
                try {
                    BarterHistory history = BarterHistory.read(file.toPath(), value.duration);
                    if (!closed) {
                        barters = history;
                        barterStatus = !history.available ? "No barter history in this recording"
                                : history.truncated ? "Partial barter history" : "";
                    }
                } catch (IOException error) { if (!closed) barterStatus = "Barter history unavailable"; }
                if (closed) return;
                try {
                    PlayerHudTrack track = PlayerHudTrack.read(value);
                    if (!closed) hud = track;
                } catch (IOException ignored) { /* Optional tracks fail independently. */ }
                if (closed) return;
                DetailedTrack detailed;
                try { detailed = DetailedTrack.read(value); }
                catch (IOException error) {
                    if (!closed) detailStatus = "Detailed capture unavailable";
                    return;
                }
                synchronized (this) {
                    if (closed) {
                        if (detailed != null) detailed.close();
                    } else {
                        detail = detailed;
                        detailStatus = detailed == null ? "No detailed capture in this recording" : "";
                    }
                }
            } catch (IOException ignored) {
                if (!closed) {
                    detailStatus = "Detailed capture unavailable";
                    barterStatus = "Barter history unavailable";
                }
            }
        }, "ZSG replay timers");
        worker.setDaemon(true);
        worker.start();
    }

    static File file(ReplayFile replay) {
        ReplayFileAccessor archive = archive(replay);
        return archive == null ? null : archive.zsgViewer$getInput();
    }

    static ReplayFileAccessor archive(ReplayFile replay) {
        Object archive = replay;
        for (int i = 0; i < 8 && archive instanceof DelegatingReplayFileAccessor; i++) {
            archive = ((DelegatingReplayFileAccessor) archive).zsgViewer$getDelegate();
        }
        return archive instanceof ReplayFileAccessor ? (ReplayFileAccessor) archive : null;
    }

    static void verifyIdentity(ReplayFile replay, CompletedIndexCache.Key expected) throws IOException {
        ReplayFileAccessor opened = archive(replay);
        expected.verifyOpened(opened == null ? null : opened.zsgViewer$getZipFile());
        for (String name : new String[] {"recording.tmcpr", "zsg-rooms/races.json", "metaData.json"}) {
            if (opened.zsgViewer$getChangedEntries().containsKey(name) || opened.zsgViewer$getRemovedEntries().contains(name)
                    || opened.zsgViewer$getOutputStreams().containsKey(name)) throw new IOException("Save replay changes before comparing splits");
        }
    }

    @Override public synchronized void close() {
        closed = true;
        worker.interrupt();
        if (detail != null) {
            try { detail.close(); } catch (IOException ignored) { }
            detail = null;
        }
    }
}
