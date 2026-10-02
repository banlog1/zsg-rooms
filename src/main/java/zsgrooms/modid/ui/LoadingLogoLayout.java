package zsgrooms.modid.ui;

/** Logo bounds follow the real chunk map, rather than a separate progress widget. */
public final class LoadingLogoLayout {

    private LoadingLogoLayout() { }

    public static int logoSize(int mapSize) {
        return Math.max(1, mapSize - 8);
    }

    public static int filledHeight(int percent, int size) {
        return Math.max(0, Math.min(100, percent)) * size / 100;
    }
}
