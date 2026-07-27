package zsgrooms.modid;

import java.util.Random;

public final class StriderJockeyControl {
    private static volatile boolean enabled;

    private StriderJockeyControl() {
    }

    public static void configure(boolean shouldEnable) {
        enabled = shouldEnable;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static JockeyOutcome consumeVanillaSelectionRolls(Random random) {
        if (random.nextInt(30) != 0) {
            random.nextInt(10);
        }
        return JockeyOutcome.NO_JOCKEY;
    }

    public enum JockeyOutcome {
        NO_JOCKEY
    }
}
