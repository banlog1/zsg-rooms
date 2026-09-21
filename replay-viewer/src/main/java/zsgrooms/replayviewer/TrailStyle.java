// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class TrailStyle {
    enum Color {
        CYAN("Cyan", 0x64EBFF), GOLD("Gold", 0xFFD166), GREEN("Green", 0x80ED99),
        PINK("Pink", 0xFF80AB), RED("Red", 0xFF625B), BLUE("Blue", 0x80AFFF),
        WHITE("White", 0xFFFFFF), VIOLET("Violet", 0xCD9BFF);

        final String label;
        final int rgb;
        Color(String label, int rgb) { this.label = label; this.rgb = rgb; }
    }

    private final Color defaultColor;
    Color color;
    int seconds = 10;
    int width = 1;
    int opacity = 85;
    boolean fade;

    TrailStyle(Color color) { this.defaultColor = color; this.color = color; }

    int duration() { return Math.max(2, Math.min(30, seconds)) * 1000; }
    int lineWidth() { return Math.max(1, Math.min(4, width)); }
    int alpha(int time, int sampleTime) {
        double strength = fade ? Math.max(0, Math.min(1, 1 - ((long) time - sampleTime) / (double) duration())) : 1;
        return (int) Math.round(255 * Math.max(10, Math.min(100, opacity)) / 100.0 * strength);
    }

    void reset() {
        color = defaultColor;
        seconds = 10;
        width = 1;
        opacity = 85;
        fade = false;
    }

    void copyFrom(TrailStyle other) {
        color = other.color;
        seconds = other.seconds;
        width = other.width;
        opacity = other.opacity;
        fade = other.fade;
    }
}
