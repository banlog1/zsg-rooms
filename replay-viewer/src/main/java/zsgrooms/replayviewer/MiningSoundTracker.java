// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** A single interaction animates for several ticks; only repeated swings suggest mining. */
final class MiningSoundTracker {
    private long target;
    private int block, phase, first, last = -1, lastSound = -1;
    private boolean repeated;

    void reset() { last = -1; lastSound = -1; repeated = false; }

    boolean sample(int time, long target, int block, int phase) {
        if (last < 0 || time < last || time - last > 150 || this.target != target || this.block != block) {
            reset();
            this.target = target;
            this.block = block;
            first = time;
        } else if (phase < this.phase && time - first >= 100) repeated = true;
        this.phase = phase;
        last = time;
        if (!repeated || lastSound >= 0 && time - lastSound < 200) return false;
        lastSound = time;
        return true;
    }
}
