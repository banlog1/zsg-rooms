package zsgrooms.modid.history;

import zsgrooms.modid.InGame;
import zsgrooms.modid.ZsgSeedBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunHistoryTracker {
    private static final Map<String, String> SPLIT_LABELS = new LinkedHashMap<String, String>();
    private static ActiveRun activeRun;

    static {
        SPLIT_LABELS.put("minecraft:nether/root", "Entered Nether");
        SPLIT_LABELS.put("minecraft:nether/find_bastion", "Entered Bastion");
        SPLIT_LABELS.put("minecraft:nether/find_fortress", "Entered Fortress");
        SPLIT_LABELS.put("minecraft:story/follow_ender_eye", "Found Stronghold");
        SPLIT_LABELS.put("minecraft:end/root", "Entered End");
    }

    private RunHistoryTracker() {
    }

    public static synchronized void recordAdvancement(InGame game, String advancementId, long igtMilliseconds) {
        String label = SPLIT_LABELS.get(advancementId);
        if (game == null || game.areCheatsAllowed() || label == null || igtMilliseconds <= 0L) {
            return;
        }
        ActiveRun run = activeRun(game);
        if (!run.splits.containsKey(label)) {
            run.splits.put(label, igtMilliseconds);
        }
    }

    public static synchronized boolean completeRun(InGame game, long igtMilliseconds) {
        if (game == null || game.areCheatsAllowed()) {
            activeRun = null;
            return false;
        }
        ActiveRun run = activeRun(game);
        long finalTime = Math.max(0L, igtMilliseconds);
        run.splits.put("Run Complete", finalTime);
        List<RunSplit> splits = new ArrayList<RunSplit>();
        for (Map.Entry<String, Long> split : run.splits.entrySet()) {
            splits.add(new RunSplit(split.getKey(), split.getValue()));
        }
        String filterId = ZsgSeedBridge.normalizeSeedType(game.targetStructure);
        RunHistoryStore.getDefault().add(new CompletedRun(
                System.currentTimeMillis(), finalTime, filterId, ZsgSeedBridge.seedTypeLabel(filterId), splits));
        activeRun = null;
        return true;
    }

    public static synchronized void resetCurrentRun() {
        activeRun = null;
    }

    public static List<CompletedRun> getRecentRuns() {
        return RunHistoryStore.getDefault().getRecentRuns();
    }

    public static String splitLabel(String advancementId) {
        return SPLIT_LABELS.get(advancementId);
    }

    private static ActiveRun activeRun(InGame game) {
        String key = game.roomName + "|" + game.getSeed();
        if (activeRun == null || !activeRun.key.equals(key)) {
            activeRun = new ActiveRun(key);
        }
        return activeRun;
    }

    private static final class ActiveRun {
        private final String key;
        private final LinkedHashMap<String, Long> splits = new LinkedHashMap<String, Long>();

        private ActiveRun(String key) {
            this.key = key;
        }
    }
}
