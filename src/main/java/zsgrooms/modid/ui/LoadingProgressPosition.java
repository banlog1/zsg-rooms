package zsgrooms.modid.ui;

public enum LoadingProgressPosition {
    CENTER("Center"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right");

    private static final int PADDING = 12;
    private static final int TEXT_OFFSET = 19;
    public final String label;

    LoadingProgressPosition(String label) { this.label = label; }

    public int mapX(int width, int size) {
        if (this == CENTER) return width / 2;
        return this == TOP_LEFT || this == BOTTOM_LEFT
                ? PADDING + size / 2 : width - PADDING - (size - size / 2);
    }

    public int mapY(int height, int size) {
        if (this == CENTER) return height / 2 + 30;
        return this == TOP_LEFT || this == TOP_RIGHT
                ? PADDING + TEXT_OFFSET + size / 2 : height - PADDING - (size - size / 2);
    }

    public int textY(int height, int size) {
        return this == CENTER ? height / 2 - 34 : mapY(height, size) - size / 2 - TEXT_OFFSET;
    }

    static LoadingProgressPosition parse(String value) {
        try { return valueOf(value.trim()); }
        catch (IllegalArgumentException | NullPointerException ignored) { return CENTER; }
    }
}
