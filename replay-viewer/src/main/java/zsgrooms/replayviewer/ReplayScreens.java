// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import java.io.IOException;

/** Optional screen-open intervals from the recorder, independent of viewer UI state. */
final class ReplayScreens {
    static final int NONE = 0, INVENTORY = 1, CRAFTING = 2;
    private final int[][] intervals;

    ReplayScreens(JsonArray json, int duration) throws IOException {
        if (json != null && json.size() > 4096) throw new IOException("Too many screen intervals");
        intervals = new int[json == null ? 0 : json.size()][3];
        int previous = 0;
        try {
            for (int i = 0; i < intervals.length; i++) {
                JsonArray row = json.get(i).getAsJsonArray();
                if (row.size() != 3) throw new IOException("Invalid screen interval");
                for (int j = 0; j < 3; j++) {
                    if (!row.get(j).isJsonPrimitive() || !row.get(j).getAsJsonPrimitive().isNumber()) {
                        throw new IOException("Invalid screen value");
                    }
                    intervals[i][j] = row.get(j).getAsBigDecimal().intValueExact();
                }
                int start = intervals[i][0], end = intervals[i][1], kind = intervals[i][2];
                if (start < previous || end < start || end > duration || kind < INVENTORY || kind > CRAFTING) {
                    throw new IOException("Invalid screen bounds or kind");
                }
                previous = end;
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid screen timeline", error);
        }
    }

    int at(int time) {
        int low = 0, high = intervals.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (intervals[mid][0] <= time) low = mid + 1; else high = mid;
        }
        return low > 0 && time < intervals[low - 1][1] ? intervals[low - 1][2] : NONE;
    }
}
