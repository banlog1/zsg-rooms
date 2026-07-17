package zsgrooms.modid.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RunHistoryStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void historyPersistsOnlyTheTenNewestRuns() {
        Path historyFile = temporaryDirectory.resolve("history.json");
        RunHistoryStore store = new RunHistoryStore(historyFile);
        for (int index = 0; index < 12; index++) {
            store.add(run(index));
        }

        List<CompletedRun> recent = new RunHistoryStore(historyFile).getRecentRuns();
        assertEquals(10, recent.size());
        assertEquals(11L, recent.get(0).getCompletedAt());
        assertEquals(2L, recent.get(9).getCompletedAt());
        assertEquals("Entered Nether", recent.get(0).getSplits().get(0).getLabel());
        assertEquals(1100L, recent.get(0).getSplits().get(0).getIgtMilliseconds());
    }

    @Test
    public void knownRaceMilestonesHaveHistoryLabels() {
        assertEquals("Entered Nether", RunHistoryTracker.splitLabel("minecraft:nether/root"));
        assertEquals("Entered Bastion", RunHistoryTracker.splitLabel("minecraft:nether/find_bastion"));
        assertEquals("Entered Fortress", RunHistoryTracker.splitLabel("minecraft:nether/find_fortress"));
        assertEquals("Found Stronghold", RunHistoryTracker.splitLabel("minecraft:story/follow_ender_eye"));
        assertEquals("Entered End", RunHistoryTracker.splitLabel("minecraft:end/root"));
    }

    private CompletedRun run(int index) {
        return new CompletedRun(index, index * 1000L, "zsg", "ZSG Mapless",
                Arrays.asList(new RunSplit("Entered Nether", index * 100L)));
    }
}
