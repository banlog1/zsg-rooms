package zsgrooms.modid.rng;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.RngStandardization;

import java.lang.reflect.Field;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class NaturalSpawnCycleTest {
    private static final NaturalSpawnSection SECTION = new NaturalSpawnSection("minecraft:the_nether", 0, -53, 6);

    @Test
    public void cachedAndUncachedSeedApisMatchOriginalFormula() {
        for (long worldSeed : new long[] {0L, 1L, -1L, 918273645L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (long index : new long[] {0L, 1L, 128L, Long.MAX_VALUE}) {
                long cached = RngStandardization.naturalSpawnCycleSeed(worldSeed, SECTION.seedKey(), index);
                assertEquals(legacyEventSeed(worldSeed, "natural_spawn_cycle", SECTION.seedKey(), index), cached);
                for (String stream : new String[] {"position", "attempt_count|pack=0", "offset|pack=2",
                        "selection|pack=1", "spawn_check|attempt=-1|pack=0", "spawn_check|attempt=11|pack=2"}) {
                    long expected = legacyStreamSeed(worldSeed, index, stream);
                    assertEquals(expected, RngStandardization.naturalSpawnStreamSeed(cached, stream));
                    assertEquals(expected, RngStandardization.naturalSpawnStreamSeed(worldSeed, SECTION.seedKey(), index, stream));
                }
            }
        }
    }

    @Test
    public void allStreamsMatchOriginalSeedsAcrossPacksAndSkippedChecks() {
        for (long worldSeed : new long[] {0L, -7192789L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (long index : new long[] {0L, 1L, 128L}) {
                NaturalSpawnCycle cycle = new NaturalSpawnCycle(worldSeed, SECTION, index);
                assertDraws(new Random(legacyStreamSeed(worldSeed, index, "position")), cycle.getPositionRandom());
                for (int pack = 0; pack < 3; pack++) {
                    cycle.beginPack();
                    assertDraws(expected(worldSeed, index, "attempt_count", pack), cycle.getAttemptCountRandom());
                    Random offsets = expected(worldSeed, index, "offset", pack);
                    for (int attempt = 0; attempt < 12; attempt++) {
                        for (int roll = 0; roll < 4; roll++) {
                            assertEquals(offsets.nextInt(6), cycle.nextOffsetInt(6));
                        }
                        assertEquals(attempt, cycle.getAttemptIndex());
                        if (attempt % 3 != 1) {
                            continue;
                        }
                        Random checks = expected(worldSeed, index, "spawn_check|attempt=" + attempt, pack);
                        assertDraws(checks, cycle.getSpawnCheckRandom());
                        assertDraws(checks, cycle.getSpawnCheckRandom());
                    }
                    assertDraws(expected(worldSeed, index, "selection", pack), cycle.getSelectionRandom());
                }
            }
        }
    }

    @Test
    public void rejectedCyclesAllocateOnlyPositionRandom() throws Exception {
        NaturalSpawnCycle cycle = new NaturalSpawnCycle(17L, SECTION, 0L);
        assertNotNull(cycle.getPositionRandom());
        assertNull(randomField(cycle, "attemptCountRandom"));
        assertNull(randomField(cycle, "offsetRandom"));
        assertNull(randomField(cycle, "selectionRandom"));
        assertNull(randomField(cycle, "spawnCheckRandom"));
    }

    @Test
    public void skippedPacksDoNotAllocateUnusedStreamsAndFirstUseGetsCurrentSeed() throws Exception {
        NaturalSpawnCycle cycle = new NaturalSpawnCycle(17L, SECTION, 3L);
        cycle.beginPack();
        cycle.getAttemptCountRandom().nextFloat();
        cycle.beginPack();
        cycle.getAttemptCountRandom().nextFloat();
        cycle.beginPack();
        assertNull(randomField(cycle, "offsetRandom"));
        assertNull(randomField(cycle, "selectionRandom"));
        assertNull(randomField(cycle, "spawnCheckRandom"));
        assertDraws(expected(17L, 3L, "selection", 2), cycle.getSelectionRandom());
        assertDraws(expected(17L, 3L, "offset", 2), cycle.getOffsetRandom());
        assertDraws(expected(17L, 3L, "spawn_check|attempt=-1", 2), cycle.getSpawnCheckRandom());
    }

    @Test
    public void usedPackStreamsAreReusedAndReseededIncludingGaussianCache() {
        NaturalSpawnCycle cycle = new NaturalSpawnCycle(17L, SECTION, 3L);
        cycle.beginPack();
        Random count = cycle.getAttemptCountRandom();
        Random offset = cycle.getOffsetRandom();
        Random selection = cycle.getSelectionRandom();
        Random check = cycle.getSpawnCheckRandom();
        count.nextGaussian();
        offset.nextGaussian();
        selection.nextGaussian();
        check.nextGaussian();
        cycle.beginPack();
        assertSame(count, cycle.getAttemptCountRandom());
        assertSame(offset, cycle.getOffsetRandom());
        assertSame(selection, cycle.getSelectionRandom());
        assertSame(check, cycle.getSpawnCheckRandom());
        assertDraws(expected(17L, 3L, "attempt_count", 1), count);
        assertDraws(expected(17L, 3L, "offset", 1), offset);
        assertDraws(expected(17L, 3L, "selection", 1), selection);
        assertDraws(expected(17L, 3L, "spawn_check|attempt=-1", 1), check);
    }

    @Test
    public void packStreamsStillRejectAccessBeforeBeginPack() {
        NaturalSpawnCycle cycle = new NaturalSpawnCycle(17L, SECTION, 0L);
        assertThrows(IllegalStateException.class, cycle::getAttemptCountRandom);
        assertThrows(IllegalStateException.class, cycle::getOffsetRandom);
        assertThrows(IllegalStateException.class, cycle::getSelectionRandom);
        assertThrows(IllegalStateException.class, cycle::getSpawnCheckRandom);
        assertThrows(IllegalStateException.class, () -> cycle.nextOffsetInt(6));
    }

    private static Object randomField(NaturalSpawnCycle cycle, String name) throws Exception {
        Field field = NaturalSpawnCycle.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(cycle);
    }

    private static Random expected(long worldSeed, long index, String stream, int pack) {
        return new Random(legacyStreamSeed(worldSeed, index, stream + "|pack=" + pack));
    }

    private static void assertDraws(Random expected, Random actual) {
        assertEquals(expected.nextInt(6), actual.nextInt(6));
        assertEquals(expected.nextInt(41), actual.nextInt(41));
        assertEquals(expected.nextFloat(), actual.nextFloat());
        assertEquals(expected.nextLong(), actual.nextLong());
        assertEquals(expected.nextGaussian(), actual.nextGaussian());
    }

    // Keep the pre-optimization formula independent of the production seed helpers.
    private static long legacyStreamSeed(long worldSeed, long index, String stream) {
        long cycleSeed = legacyEventSeed(worldSeed, "natural_spawn_cycle", SECTION.seedKey(), index);
        return legacyEventSeed(cycleSeed, "natural_spawn_stream", stream, 0L);
    }

    private static long legacyEventSeed(long seed, String channel, String key, long index) {
        long hash = 0xcbf29ce484222325L ^ seed;
        for (String value : new String[] {channel, key}) {
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i);
                hash *= 0x100000001b3L;
            }
        }
        hash ^= index * 0x9e3779b97f4a7c15L;
        hash ^= hash >>> 30;
        hash *= 0xbf58476d1ce4e5b9L;
        hash ^= hash >>> 27;
        hash *= 0x94d049bb133111ebL;
        return hash ^ (hash >>> 31);
    }
}
