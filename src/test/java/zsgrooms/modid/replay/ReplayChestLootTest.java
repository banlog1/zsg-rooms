package zsgrooms.modid.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReplayChestLootTest {
    @Test void withheldUntilReleaseAndOnIncompleteFiles() {
        for (int mode = 0; mode < 3; mode++) {
            ReplayRaceManifest manifest = manifest();
            record(manifest, 5, 1, 0);
            manifest.recordStewOrder(Collections.singletonList("minecraft:saturation"));
            if (mode != 0) manifest.allowTemplePrediction(123L);
            JsonObject data = json(manifest.finish(100, mode != 1));
            assertEquals(mode == 2 ? 1 : 0, data.getAsJsonArray("chestLoot").size());
            assertEquals(mode == 2, data.has("stewOrder"));
        }
    }

    @Test void boundedAndClampedWithWorldAndPacketIdentity() {
        ReplayRaceManifest manifest = manifest();
        manifest.allowTemplePrediction(123L);
        record(manifest, 5, 1, -1);
        record(manifest, 5, 7, 0);
        record(manifest, 6, 8, 1);
        record(manifest, 101, 9, 1);
        JsonObject data = json(manifest.finish(100, true));
        assertEquals(2, data.getAsJsonArray("chestLoot").size());
        JsonObject reset = data.getAsJsonArray("chestLoot").get(1).getAsJsonObject();
        assertEquals(1, reset.get("world").getAsInt());
        assertEquals(8, reset.get("chunk").getAsInt());
        assertEquals(Long.MIN_VALUE, reset.getAsJsonObject("loot").get("seed").getAsLong());
        ReplayRaceManifest bounded = manifest();
        bounded.allowTemplePrediction(1L);
        for (int i = 0; i < 2050; i++) record(bounded, i, i + 1, 0);
        assertEquals(2048, json(bounded.finish(3000, true)).getAsJsonArray("chestLoot").size());
    }

    private static void record(ReplayRaceManifest manifest, int time, int chunk, int world) {
        manifest.recordChestLoot(time, chunk, world, Collections.singletonList(new ReplayChestLootPacket.Loot(
                123, 456, "minecraft:the_nether", "minecraft:chests/bastion_other", Long.MIN_VALUE)));
    }
    private static ReplayRaceManifest manifest() { return new ReplayRaceManifest(new UUID(0, 0), new UUID(0, 1), "Runner", 0); }
    private static JsonObject json(String json) { return new JsonParser().parse(json).getAsJsonObject(); }
}
