// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Drain queued client work across two settled frames before accepting new sounds. */
final class SeekAudioGate {
    private boolean muted;
    private int settled;

    void begin() { muted = true; settled = 0; }
    boolean muted() { return muted; }
    boolean frame(boolean busy) {
        if (busy) begin();
        else if (muted && ++settled >= 2) {
            muted = false;
            return true;
        }
        return false;
    }
}
