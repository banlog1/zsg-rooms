// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.Locale;

/** Recorded timer values, never the viewer's own live timer or wall clock. */
final class ReplayTimings {
    private final long[][] samples;

    ReplayTimings(JsonArray json, int duration) throws IOException {
        if (json != null && json.size() > 12000) throw new IOException("Too many timer samples");
        samples = new long[json == null ? 0 : json.size()][];
        long previous = -1;
        for (int i = 0; i < samples.length; i++) {
            JsonArray row = json.get(i).getAsJsonArray();
            if (row.size() != 5) throw new IOException("Invalid timer sample");
            long[] sample = new long[5];
            for (int j = 0; j < 5; j++) {
                JsonElement value = row.get(j);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid timer value");
                sample[j] = value.getAsBigDecimal().longValueExact();
            }
            if (sample[0] <= previous || sample[0] > duration || sample[1] < -1 || sample[1] > Integer.MAX_VALUE
                    || sample[2] < 0 || sample[2] > 2 || sample[3] < -1 || sample[4] < -1
                    || sample[3] > Integer.MAX_VALUE || sample[4] > Integer.MAX_VALUE) throw new IOException("Invalid timer bounds");
            samples[i] = sample;
            previous = sample[0];
        }
    }

    Value at(int time) {
        int low = 0, high = samples.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (samples[mid][0] <= time) low = mid + 1; else high = mid;
        }
        if (low == 0) return Value.UNKNOWN;
        long[] a = samples[low - 1];
        // A stopped/limited capture is not evidence that a pause continued indefinitely.
        if (time - a[0] > 1500 || a[2] == 2) return Value.UNKNOWN;
        long rta = a[3], igt = a[4];
        if (low < samples.length) {
            long[] b = samples[low];
            if (b[0] - a[0] <= 1500 && a[1] == b[1] && a[2] == b[2]) {
                rta = between(rta, b[3], time - a[0], b[0] - a[0]);
                igt = between(igt, b[4], time - a[0], b[0] - a[0]);
            }
        }
        return new Value(a[2] == 1, true, rta, igt);
    }

    private static long between(long a, long b, long elapsed, long duration) {
        return a < 0 || b < a ? a : a + (b - a) * elapsed / duration;
    }

    static String clock(long millis) {
        if (millis < 0) return "--:--";
        return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60000, millis / 1000 % 60, millis % 1000);
    }

    static final class Value {
        static final Value UNKNOWN = new Value(false, false, -1, -1);
        final boolean paused, known;
        final long rta, igt;
        Value(boolean paused, boolean known, long rta, long igt) {
            this.paused = paused; this.known = known; this.rta = rta; this.igt = igt;
        }
    }
}
