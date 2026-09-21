// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import java.io.IOException;

/** Explicit recorded loading intervals, not guesses from missing timer/player data. */
final class ReplayLoading {
    private final int[][] intervals;

    ReplayLoading(JsonArray json, int duration) throws IOException {
        if (json != null && json.size() > 4096) throw new IOException("Too many loading intervals");
        intervals = new int[json == null ? 0 : json.size()][2];
        int previous = 0;
        try {
            for (int i = 0; i < intervals.length; i++) {
                JsonArray row = json.get(i).getAsJsonArray();
                if (row.size() != 2) throw new IOException("Invalid loading interval");
                for (int j = 0; j < 2; j++) {
                    if (!row.get(j).isJsonPrimitive() || !row.get(j).getAsJsonPrimitive().isNumber()) {
                        throw new IOException("Invalid loading time");
                    }
                    intervals[i][j] = row.get(j).getAsBigDecimal().intValueExact();
                }
                int start = intervals[i][0], end = intervals[i][1];
                if (start < previous || end < start || end > duration) throw new IOException("Invalid loading bounds");
                previous = end;
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid loading timeline", error);
        }
    }

    boolean at(int time) {
        int low = 0, high = intervals.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (intervals[mid][0] <= time) low = mid + 1; else high = mid;
        }
        return low > 0 && time < intervals[low - 1][1];
    }
}
