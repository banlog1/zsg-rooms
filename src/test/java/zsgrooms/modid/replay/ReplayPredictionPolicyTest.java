package zsgrooms.modid.replay;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReplayPredictionPolicyTest {
    @Test void unannouncedRoomDisconnectAndResetNeverReleaseSeed() {
        ReplayPredictionPolicy policy = new ReplayPredictionPolicy(42, true);
        policy.disconnected(false);
        assertNull(policy.releasedSeed());
        policy.disconnected(true);
        assertNull(policy.releasedSeed());
        policy.matchEnded();
        assertEquals(Long.valueOf(42), policy.releasedSeed());
        policy.joinedRoom();
        assertNull(policy.releasedSeed());
    }

    @Test void onlyFinalSingleplayerDisconnectReleasesSeed() {
        ReplayPredictionPolicy policy = new ReplayPredictionPolicy(Long.MIN_VALUE, false);
        assertNull(policy.releasedSeed());
        policy.disconnected(false);
        assertNull(policy.releasedSeed());
        policy.disconnected(true);
        assertEquals(Long.valueOf(Long.MIN_VALUE), policy.releasedSeed());
        policy.joinedRoom();
        policy.disconnected(true);
        assertNull(policy.releasedSeed());
    }

    @Test void savedOrFailedManifestDoesNotImplyPredictionPermission() {
        ReplayRaceManifest plain = manifest();
        plain.startRace("race", new UUID(0, 1), 0);
        plain.finishRace("race", 100, 0);
        assertFalse(new JsonParser().parse(plain.finish(1000, true)).getAsJsonObject().has("templePredictionSeed"));
        ReplayRaceManifest released = manifest();
        released.allowTemplePrediction(Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, new JsonParser().parse(released.finish(1000, true)).getAsJsonObject()
                .get("templePredictionSeed").getAsLong());
        ReplayRaceManifest failed = manifest();
        failed.allowTemplePrediction(42L);
        assertFalse(new JsonParser().parse(failed.finish(1000, false)).getAsJsonObject().has("templePredictionSeed"));
    }

    private ReplayRaceManifest manifest() { return new ReplayRaceManifest(new UUID(0, 0), new UUID(0, 1), "Runner", 0); }
}
