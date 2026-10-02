package zsgrooms.modid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.replay.ReplayPrototype;
import zsgrooms.modid.ui.ZsgInGameActions;

/** Applies only this client's assignment; remote finishes never close its world. */
public final class RaceSequenceClient {
    private static String raceId = "";
    private static int launchedStage;
    private static boolean resultShown;
    private static Object previousWorld;
    private static boolean transitioning;
    private static String pendingReport;
    private static String pendingAction;
    private static int pendingStage;
    private static boolean withdrawRequested;
    private static long retryAt;

    public static boolean canPlay(InGame game) {
        if (game == null || !game.getIsInGame()) return false;
        RaceSequence race = game.getSequence();
        if (race == null) return true;
        RaceSequence.Runner runner = race.runner(ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()));
        return runner != null && !runner.done() && runner.stage == launchedStage && !transitioning && pendingReport == null;
    }

    public static int stage(InGame game) {
        if (game.getSequence() == null) return 0;
        RaceSequence.Runner runner = game.getSequence().runner(ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()));
        return runner == null ? 0 : runner.stage;
    }

    public static void report(InGame game, String action, long elapsed) {
        if (game.getSequence() == null) return;
        observeRace(game);
        if ("dnf".equals(action)) withdrawRequested = true;
        if (pendingReport != null || game.getSequence() == null) return;
        if ("dnf".equals(action)) withdrawRequested = false;
        pendingAction = "finish".equals(action) ? "complete_run" : "forfeit";
        pendingStage = stage(game);
        pendingReport = RaceSequence.report(game.getRaceId(), pendingStage, elapsed, action);
        retryAt = System.nanoTime() + 2_000_000_000L;
        ZsgRoomsClient.sendRoomAction(pendingAction, game.roomName, pendingReport);
    }

    public static void tick(MinecraftClient client, InGame game) {
        RaceSequence race = game == null ? null : game.getSequence();
        if (race == null) {
            raceId = "";
            pendingReport = null;
            transitioning = false;
            previousWorld = null;
            withdrawRequested = false;
            return;
        }
        observeRace(game);
        RaceSequence.Runner runner = race.runner(ZsgRoomsClient.localPlayerName(client));
        if (runner == null) return;
        if (pendingReport != null && (runner.done() || runner.stage != pendingStage)) {
            pendingReport = null;
            retryAt = 0;
        }
        if (withdrawRequested && !runner.done() && pendingReport == null) report(game, "dnf", -1);
        if (pendingReport != null && System.nanoTime() >= retryAt) {
            retryAt = System.nanoTime() + 2_000_000_000L;
            ZsgRoomsClient.sendRoomAction(pendingAction, game.roomName, pendingReport);
        }
        if (client.currentScreen instanceof CreditsScreen) {
            if (runner.done() || runner.stage != launchedStage) client.currentScreen.onClose();
            return;
        }
        if (runner.done()) {
            previousWorld = null;
            transitioning = false;
            if ((runner.stopped || runner.wonByForfeit) && client.world != null && client.player != null
                    && (client.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen
                        || client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) client.openScreen(null);
            if (!resultShown && client.world != null && client.player != null && client.currentScreen == null) {
                resultShown = true;
                if (runner.finished) ReplayPrototype.raceFinished(raceId, runner.elapsedNanos,
                        SpeedRunIgtBridge.currentInGameTimeMilliseconds());
                ZsgInGameActions.showSequenceResult(client, race, runner);
            }
            return;
        }
        if (transitioning) {
            if (client.world != null && client.world != previousWorld && client.player != null && client.currentScreen == null) {
                transitioning = false;
                previousWorld = null;
            }
            return;
        }
        if (runner.stage <= launchedStage || !game.isSynchronizedStartReleased() || client.world == null || client.currentScreen != null
                || client.player == null || System.nanoTime() < retryAt) return;
        String seed = race.seed(runner.stage);
        if (seed == null) return;
        previousWorld = client.world;
        ReplayPrototype.expectSequenceSeed(seed);
        String previousSeed = game.getSeed();
        game.setSeed(seed);
        client.openScreen(null);
        if (ZsgSeedBridge.launchSeedWithAtum(seed)) {
            launchedStage = runner.stage;
            transitioning = true;
            ZsgRoomsClient.resetLocalAdvancementTracking();
        } else {
            game.setSeed(previousSeed);
            ReplayPrototype.expectSequenceSeed(null);
            retryAt = System.nanoTime() + 5_000_000_000L;
            client.inGameHud.getChatHud().addMessage(new LiteralText("[ZSG Room] Next seed could not load. Retrying..."));
        }
    }

    private static void observeRace(InGame game) {
        if (raceId.equals(game.getRaceId())) return;
        raceId = game.getRaceId();
        launchedStage = 0;
        resultShown = false;
        transitioning = false;
        pendingReport = null;
        retryAt = 0;
        previousWorld = null;
        withdrawRequested = false;
    }
}
