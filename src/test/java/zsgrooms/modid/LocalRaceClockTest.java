package zsgrooms.modid;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.net.RaceFinishArbiter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalRaceClockTest {
    private final UUID player = new UUID(0L, 1L);
    private final Object server = new Object();

    @Test
    void replayStartMarkerUsesExactClockInstantOncePerRaceNotPerWorld() {
        LocalRaceClock clock = new LocalRaceClock();
        assertNull(clock.onResumedTick(server, 1L));
        clock.arm("first", server, player);
        assertNull(clock.onResumedTick(new Object(), 2L));
        LocalRaceClock.Start start = clock.onResumedTick(server, 123L);
        assertEquals("first", start.raceId);
        assertEquals(player, start.player);
        assertEquals(123L, start.nanos);
        assertNull(clock.onResumedTick(server, 124L));
        Object replacement = new Object();
        clock.bindWorld("first", replacement, player);
        assertNull(clock.onResumedTick(replacement, 200L));
        clock.capture(replacement, player, 300L);
        assertEquals(300L - start.nanos, clock.consume("first", replacement, player));
        clock.arm("second", replacement, player);
        assertEquals("second", clock.onResumedTick(replacement, 400L).raceId);
        clock.clear();
        assertNull(clock.onResumedTick(replacement, 500L));
    }

    private long measured(long start, long finish) {
        LocalRaceClock clock = new LocalRaceClock();
        clock.arm("race", server, player);
        clock.onResumedTick(server, start);
        clock.capture(server, player, finish);
        return clock.consume("race", server, player);
    }

    @Test
    void differentAbsoluteOriginsProduceIdenticalDurations() {
        assertEquals(measured(1000L, 601000L), measured(-9000000000L, -8999400000L));
    }

    @Test
    void guestWithLaterStartAndEarlierDurationWinsEvenIfAbsoluteFinishIsLater() {
        long host = measured(1000L * 1000000L, 601000L * 1000000L);
        long guest = measured(1080L * 1000000L, 601030L * 1000000L);
        assertEquals(600000L * 1000000L, host);
        assertEquals(599950L * 1000000L, guest);
        RaceFinishArbiter arbiter = new RaceFinishArbiter();
        arbiter.beginRace("race");
        arbiter.submit("Host", RaceFinishArbiter.completion("race", host, 0L), 1000L, true);
        arbiter.submit("Guest", RaceFinishArbiter.completion("race", guest, Long.MAX_VALUE), 1500L, true);
        assertEquals("Guest", arbiter.poll(3000L).winner);
    }

    @Test
    void clockStartsOnlyOnFirstResumedTickOfArmedServer() {
        LocalRaceClock clock = new LocalRaceClock();
        clock.onResumedTick(server, 10L);
        clock.arm("race", server, player);
        clock.onResumedTick(new Object(), 20L);
        clock.capture(server, player, 30L);
        assertEquals(-1L, clock.consume("race", server, player));
        clock.onResumedTick(server, 100L);
        clock.onResumedTick(server, 200L);
        clock.arm("race", server, player);
        clock.onResumedTick(server, 300L);
        clock.capture(server, player, 400L);
        assertEquals(300L, clock.consume("race", server, player));
    }

    @Test
    void worldResetDoesNotRestartRaceClockAndOldWorldCannotFinish() {
        LocalRaceClock clock = new LocalRaceClock();
        clock.arm("race", server, player);
        clock.onResumedTick(server, 100L);
        Object replacement = new Object();
        clock.bindWorld("race", replacement, player);
        clock.onResumedTick(replacement, 300L);
        clock.capture(server, player, 400L);
        assertEquals(-1L, clock.consume("race", replacement, player));
        clock.capture(replacement, player, 500L);
        assertEquals(400L, clock.consume("race", replacement, player));
    }

    @Test
    void freshRaceCannotReuseOldStartOrFinishEvenWithSameServer() {
        LocalRaceClock clock = new LocalRaceClock();
        clock.arm("first", server, player);
        clock.onResumedTick(server, 100L);
        clock.capture(server, player, 300L);
        clock.arm("second", server, player);
        assertEquals(-1L, clock.consume("first", server, player));
        assertEquals(-1L, clock.consume("second", server, player));
        clock.capture(server, player, 400L);
        assertEquals(-1L, clock.consume("second", server, player));
        clock.onResumedTick(server, 500L);
        clock.capture(server, player, 600L);
        assertEquals(100L, clock.consume("second", server, player));
    }

    @Test
    void captureDoesNotUseLaterPacketProcessingTimeAndIsConsumedOnce() {
        LocalRaceClock clock = new LocalRaceClock();
        clock.arm("race", server, player);
        clock.onResumedTick(server, 100L);
        clock.capture(server, player, 200L);
        clock.capture(server, player, 999999999L);
        assertEquals(100L, clock.consume("race", server, player));
        assertEquals(-1L, clock.consume("race", server, player));
    }

    @Test
    void absentCaptureAndWrongIdentityNeverFallBackToAnotherClock() {
        LocalRaceClock clock = new LocalRaceClock();
        assertEquals(-1L, clock.consume("race", server, player));
        clock.arm("race", server, player);
        clock.onResumedTick(server, 100L);
        clock.capture(server, new UUID(0L, 2L), 200L);
        assertEquals(-1L, clock.consume("race", server, player));
        clock.capture(server, player, 200L);
        assertEquals(-1L, clock.consume("other", server, player));
        assertEquals(100L, clock.consume("race", server, player));
    }

    @Test
    void clearDisarmsPendingStartAndFinish() {
        LocalRaceClock clock = new LocalRaceClock();
        clock.arm("race", server, player);
        clock.clear();
        clock.onResumedTick(server, 100L);
        clock.capture(server, player, 200L);
        assertEquals(-1L, clock.consume("race", server, player));
    }

    @Test
    void pausesAndLoadTimeAfterStartRemainPartOfElapsedDuration() {
        LocalRaceClock clock = new LocalRaceClock();
        clock.arm("race", server, player);
        clock.onResumedTick(server, 100L);
        clock.onResumedTick(server, 900000000000L);
        clock.capture(server, player, 900000000001L);
        assertEquals(899999999901L, clock.consume("race", server, player));
    }

    @Test
    void subtractionSupportsNanoTimeWrapAndRejectsImpossibleDurations() {
        assertEquals(100L, measured(Long.MAX_VALUE - 49L, Long.MIN_VALUE + 50L));
        assertEquals(-1L, measured(100L, 99L));
        assertEquals(-1L, measured(0L, RaceFinishArbiter.MAX_ELAPSED_NANOS + 1L));
    }
}
