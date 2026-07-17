package zsgrooms.modid.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompletedRun {
    private long completedAt;
    private long igtMilliseconds;
    private String filterId;
    private String filterLabel;
    private List<RunSplit> splits;

    private CompletedRun() {
    }

    public CompletedRun(long completedAt, long igtMilliseconds, String filterId, String filterLabel,
            List<RunSplit> splits) {
        this.completedAt = completedAt;
        this.igtMilliseconds = Math.max(0L, igtMilliseconds);
        this.filterId = filterId == null ? "unknown" : filterId;
        this.filterLabel = filterLabel == null ? "Unknown Filter" : filterLabel;
        this.splits = splits == null ? new ArrayList<RunSplit>() : new ArrayList<RunSplit>(splits);
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public long getIgtMilliseconds() {
        return igtMilliseconds;
    }

    public String getFilterId() {
        return filterId;
    }

    public String getFilterLabel() {
        return filterLabel;
    }

    public List<RunSplit> getSplits() {
        if (splits == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<RunSplit>(splits));
    }
}
