// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Client-thread scope. Only work requested by a deferred chunk callback is flushed. */
final class SeekLightingBatch {
    private final Runnable drain;
    private boolean pending;

    SeekLightingBatch(Runnable drain) { this.drain = drain; }
    void defer() { pending = true; }
    void flush() {
        if (!pending) return;
        pending = false;
        drain.run();
    }
}
