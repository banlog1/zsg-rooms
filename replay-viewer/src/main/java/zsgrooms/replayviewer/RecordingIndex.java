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
    private volatile boolean closed;
    private final Thread worker;

    RecordingIndex(ReplayFile replay) {
        File file = file(replay);
        worker = new Thread(() -> {
            if (file == null) return;
            try {
                RaceRecording value = RaceRecording.read(file.toPath());
                if (!closed) recording = value;
            } catch (IOException ignored) { /* Legacy or unsupported metadata: show unavailable values. */ }
        }, "ZSG replay timers");
        worker.setDaemon(true);
        worker.start();
    }

    static File file(ReplayFile replay) {
        Object archive = replay;
        for (int i = 0; i < 8 && archive instanceof DelegatingReplayFileAccessor; i++) {
            archive = ((DelegatingReplayFileAccessor) archive).zsgViewer$getDelegate();
        }
        return archive instanceof ReplayFileAccessor ? ((ReplayFileAccessor) archive).zsgViewer$getInput() : null;
    }

    @Override public void close() { closed = true; worker.interrupt(); }
}
