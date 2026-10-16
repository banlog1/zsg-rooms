// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.math.BigDecimal;

/** Absolute replay time in milliseconds, not race elapsed time. */
final class ReplayTimeInput {
    private ReplayTimeInput() { }

    static int parse(String text) {
        if (text == null) return -1;
        String[] parts = text.trim().split(":", -1);
        if (parts.length < 1 || parts.length > 3) return -1;
        try {
            long prefix = 0;
            for (int i = 0; i < parts.length - 1; i++) {
                if (!parts[i].matches("[0-9]{1,9}")) return -1;
                int value = Integer.parseInt(parts[i]);
                if (i > 0 && value >= 60) return -1;
                prefix = prefix * 60 + value;
            }
            String secondsText = parts[parts.length - 1];
            if (!secondsText.matches("[0-9]{1,10}(\\.[0-9]{1,3})?")) return -1;
            BigDecimal seconds = new BigDecimal(secondsText);
            if (parts.length > 1 && seconds.compareTo(BigDecimal.valueOf(60)) >= 0) return -1;
            long millis = seconds.movePointRight(3).longValueExact() + prefix * 60000;
            return millis <= Integer.MAX_VALUE ? (int) millis : -1;
        } catch (ArithmeticException | NumberFormatException error) {
            return -1;
        }
    }

    static String format(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        if (seconds < 3600) return ViewerLayout.time(milliseconds);
        return seconds / 3600 + ":" + two(seconds / 60 % 60) + ":" + two(seconds % 60);
    }

    private static String two(int value) { return value < 10 ? "0" + value : Integer.toString(value); }
}
