package zsgrooms.modid.history;

public final class RunSplit {
    private String label;
    private long igtMilliseconds;

    private RunSplit() {
    }

    public RunSplit(String label, long igtMilliseconds) {
        this.label = label == null ? "Split" : label;
        this.igtMilliseconds = Math.max(0L, igtMilliseconds);
    }

    public String getLabel() {
        return label;
    }

    public long getIgtMilliseconds() {
        return igtMilliseconds;
    }
}
