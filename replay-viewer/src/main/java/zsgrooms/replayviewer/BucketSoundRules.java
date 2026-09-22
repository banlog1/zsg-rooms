// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class BucketSoundRules {
    static final int NONE = 0, WATER = 1, LAVA = 2;
    static final int EMPTY_BUCKET = 1, WATER_BUCKET = 2, LAVA_BUCKET = 4;
    enum Action { NONE, FILL_WATER, FILL_LAVA, EMPTY_WATER, EMPTY_LAVA }

    static Action action(int beforeSource, int afterSource, boolean afterEmpty, int held) {
        if (beforeSource == afterSource) return Action.NONE;
        if (afterEmpty && (held & EMPTY_BUCKET) != 0) {
            if (beforeSource == WATER) return Action.FILL_WATER;
            if (beforeSource == LAVA) return Action.FILL_LAVA;
        }
        if (beforeSource == NONE) {
            if (afterSource == WATER && (held & WATER_BUCKET) != 0) return Action.EMPTY_WATER;
            if (afterSource == LAVA && (held & LAVA_BUCKET) != 0) return Action.EMPTY_LAVA;
        }
        return Action.NONE;
    }
}
