package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonObject;
import static org.junit.jupiter.api.Assertions.*;

class SpawnModelCheckTest {
    @Test void clearPassAndFailureDoNotGenerateSpawn() {
        SpawnModelCheck check = new SpawnModelCheck(BlockPos.ORIGIN, () -> { throw new AssertionError("Unexpected native spawn"); });
        assertTrue(check.within(32, BlockPos.ORIGIN));
        assertFalse(check.within(32, new BlockPos(49, 0, 0)));
        assertEquals(0, check.nativeRequests);
    }

    @Test void boundaryUsesNativeOnlyOnceAndReusesIt() {
        AtomicInteger calls = new AtomicInteger();
        SpawnModelCheck check = new SpawnModelCheck(BlockPos.ORIGIN, () -> { calls.incrementAndGet(); return new BlockPos(48, 64, 0); });
        assertTrue(check.within(32, new BlockPos(40, 0, 0)));
        assertFalse(check.within(32, BlockPos.ORIGIN));
        assertEquals(1, calls.get());
    }

    @Test void exactModeNeverUsesModelAndAlternativesAreAnOr() {
        SpawnModelCheck check = new SpawnModelCheck(null, () -> BlockPos.ORIGIN);
        assertTrue(check.within(32, new BlockPos(100, 0, 0), new BlockPos(32, 0, 32)));
        assertFalse(check.within(32, new BlockPos(-33, 0, -33)));
        assertEquals(1, check.nativeRequests);
        assertEquals(0, check.modelPasses);
    }

    @Test void exactPoolEnvelopeKeepsCircularCornerRejection() {
        SpawnModelCheck check = new SpawnModelCheck(null, () -> new BlockPos(128, 0, 128));
        assertFalse(check.canReachPoolArea(BlockPos.ORIGIN));
    }

    @Test void validatesWorkerHandoffAndPreservesLegacyExactJobs() {
        JsonObject job = new JsonObject();
        assertNull(FilterVerifierWorker.spawnPrediction(job));
        job.addProperty("spawnModelX", 12);
        assertThrows(IllegalStateException.class, () -> FilterVerifierWorker.spawnPrediction(job));
        job.addProperty("spawnModelZ", -32);
        job.addProperty("spawnModelRevision", "wrong");
        assertThrows(IllegalStateException.class, () -> FilterVerifierWorker.spawnPrediction(job));
        job.addProperty("spawnModelRevision", CubiomesSpawnModel.REVISION);
        assertEquals(new BlockPos(12, 64, -32), FilterVerifierWorker.spawnPrediction(job));
        job.addProperty("spawnModelX", Long.MIN_VALUE);
        assertThrows(IllegalStateException.class, () -> FilterVerifierWorker.spawnPrediction(job));
    }
}
