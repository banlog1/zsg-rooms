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
    volatile CameraTrack camera;
    volatile PresentationTrack presentation;
    volatile LocalSoundTrack localSounds;
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
                readCamera(value);
                if (closed) return;
                readDetailed(value);
                if (!closed) readSupplemental(value);
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

    private void readCamera(RaceRecording value) {
        try {
            CameraTrack track = CameraTrack.read(value);
            synchronized (this) {
                if (closed) {
                    if (track != null) track.close();
                } else camera = track;
            }
        } catch (IOException ignored) { /* Camera capture is independent of all other optional tracks. */ }
    }

    private void readSupplemental(RaceRecording value) {
        try {
            LocalSoundTrack track = LocalSoundTrack.read(value);
            synchronized (this) {
                if (closed) { if (track != null) track.close(); }
                else localSounds = track;
            }
        } catch (IOException | RuntimeException ignored) { /* Old or incomplete capture uses inferred sounds. */ }
        if (closed) return;
        try {
            PresentationTrack track = PresentationTrack.read(value);
            synchronized (this) {
                if (closed) { if (track != null) track.close(); }
                else presentation = track;
            }
        } catch (IOException | RuntimeException ignored) { /* Optional presentation must not block normal playback. */ }
    }

    private void readDetailed(RaceRecording value) {
        try {
            DetailedTrack track = DetailedTrack.read(value);
            synchronized (this) {
                if (closed) { if (track != null) track.close(); }
                else {
                    detail = track;
                    detailStatus = track == null ? "No detailed capture in this recording" : "";
                }
            }
        } catch (IOException error) { if (!closed) detailStatus = "Detailed capture unavailable"; }
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
        if (presentation != null) {
            try { presentation.close(); } catch (IOException ignored) { }
            presentation = null;
        }
        if (localSounds != null) {
            try { localSounds.close(); } catch (IOException ignored) { }
            localSounds = null;
        }
        if (camera != null) {
            try { camera.close(); } catch (IOException ignored) { }
            camera = null;
        }
        if (detail != null) {
            try { detail.close(); } catch (IOException ignored) { }
            detail = null;
        }
    }
}
