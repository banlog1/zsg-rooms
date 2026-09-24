package zsgrooms.modid.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReplayChestCaptureTest {
    private ReplayRaceManifest manifest() { return new ReplayRaceManifest(UUID.randomUUID(), UUID.randomUUID(), "Runner", 0); }
    private JsonArray openings(ReplayRaceManifest manifest, int end) {
        return new JsonParser().parse(manifest.finish(end, true)).getAsJsonObject().getAsJsonArray("chestOpenings");
    }

    @Test void successfulOpeningIsRecordedOnceWithGlobalWindowOrdinal() {
        ReplayChestCapture capture = new ReplayChestCapture();
        ReplayRaceManifest manifest = manifest();
        capture.opened(manifest, 1, 0, 1, 27);
        capture.interaction(10, 0, "minecraft:overworld", 1, 1, 2, 2);
        capture.opened(manifest, 15, 0, 2, 27);
        capture.opened(manifest, 20, 0, 3, 27);
        JsonArray rows = openings(manifest, 100);
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).getAsJsonObject().get("opening").getAsInt());
        assertEquals(15, rows.get(0).getAsJsonObject().get("time").getAsInt());
    }

    @Test void cancelledExpiredWrongWorldAndWrongSizedInteractionsStayUnknown() {
        ReplayChestCapture capture = new ReplayChestCapture();
        ReplayRaceManifest manifest = manifest();
        capture.interaction(0, 0, "minecraft:overworld", 1, 1, 2, 2);
        capture.clear(); capture.opened(manifest, 1, 0, 1, 27);
        capture.interaction(0, 0, "minecraft:overworld", 1, 1, 2, 2);
        capture.opened(manifest, 6000, 0, 2, 27);
        capture.interaction(6000, 0, "minecraft:overworld", 1, 1, 2, 2);
        capture.opened(manifest, 6100, 1, 3, 27);
        capture.interaction(6100, 1, "minecraft:overworld", 1, 2, 2, 3);
        capture.opened(manifest, 6200, 1, 4, 27);
        assertEquals(0, openings(manifest, 7000).size());
    }

    @Test void doubleChestAndResetUseSeparateWorldsAndFinalizationClampsMarkers() {
        ReplayChestCapture capture = new ReplayChestCapture();
        ReplayRaceManifest manifest = manifest();
        capture.interaction(10, 0, "minecraft:overworld", 1, 2, 2, 3);
        capture.opened(manifest, 20, 0, 1, 54);
        capture.clear();
        capture.interaction(30, 1, "minecraft:overworld", 1, 2, 2, 3);
        capture.opened(manifest, 40, 1, 1, 54);
        JsonArray rows = openings(manifest, 35);
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).getAsJsonObject().get("second").getAsLong());
    }
}
