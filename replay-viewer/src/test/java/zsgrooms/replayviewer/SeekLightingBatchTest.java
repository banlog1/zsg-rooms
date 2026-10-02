// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SeekLightingBatchTest {
    @Test void noDeferredWorkDoesNotDrainOtherUpdates() {
        SeekLightingBatch batch = new SeekLightingBatch(() -> fail("Unexpected drain"));
        batch.flush();
    }
    @Test void repeatedRequestsCoalesceButBarriersKeepSeparateBatches() {
        AtomicInteger drains = new AtomicInteger();
        SeekLightingBatch batch = new SeekLightingBatch(drains::incrementAndGet);
        for (int i=0;i<1000;i++) batch.defer();
        batch.flush(); batch.flush();
        assertEquals(1,drains.get());
        batch.defer(); batch.flush();
        assertEquals(2,drains.get());
    }
    @Test void failedDrainDoesNotLeaveStaleWork() {
        SeekLightingBatch batch = new SeekLightingBatch(() -> { throw new IllegalStateException("test"); });
        batch.defer();
        assertThrows(IllegalStateException.class,batch::flush);
        assertDoesNotThrow(batch::flush);
    }
}
