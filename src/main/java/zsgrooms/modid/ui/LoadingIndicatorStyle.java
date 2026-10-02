package zsgrooms.modid.ui;

public enum LoadingIndicatorStyle {
    VANILLA("Vanilla"), ZSG("ZSG");

    public final String label;

    LoadingIndicatorStyle(String label) { this.label = label; }

    static LoadingIndicatorStyle parse(String value) {
        try { return valueOf(value.trim()); }
        catch (IllegalArgumentException | NullPointerException ignored) { return ZSG; }
    }
}
