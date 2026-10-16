// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReplaySplitsTest {
    @TempDir Path directory;

    private RaceRecording read(JsonObject data) throws Exception {
        Path file = directory.resolve(UUID.randomUUID() + ".mcpr");
        RaceRecordingTest.zip(file, data.toString(), 100000);
        return RaceRecording.read(file);
    }

    private JsonObject data(int start) {
        return RaceRecordingTest.data(UUID.randomUUID().toString(), start, UUID.randomUUID().toString());
    }

    private ReplaySplits table(RaceRecording recording, Milestones.Entry... events) {
        return new ReplaySplits(new RaceReplayLibrary.Entry(recording, recording.races.get(0)), Arrays.asList(events));
    }

    @Test void showsRaceElapsedRatherThanAbsoluteRecordingTime() throws Exception {
        RaceRecording a = read(data(8000)), b = read(data(15000));
        ReplaySplits.Split first = table(a, new Milestones.Entry(Milestones.Kind.NETHER, 18000, 1))
                .worlds.get(0).splits.get(Milestones.Kind.NETHER);
        ReplaySplits.Split second = table(b, new Milestones.Entry(Milestones.Kind.NETHER, 25000, 1))
                .worlds.get(0).splits.get(Milestones.Kind.NETHER);
        assertEquals(10000, first.elapsed);
        assertTrue(first.jumpable);
        assertEquals(first.elapsed, second.elapsed);
        assertNotEquals(first.time, second.time);
        assertEquals(-1, first.igt);
    }

    @Test void repeatedMilestonesStayInTheirOwnWorldsAndUseFirstCompletion() throws Exception {
        ReplaySplits splits = table(read(data(8000)),
                new Milestones.Entry(Milestones.Kind.NETHER, 20000, 1),
                new Milestones.Entry(Milestones.Kind.NETHER, 19000, 1),
                new Milestones.Entry(Milestones.Kind.FORTRESS, 23000, 1),
                new Milestones.Entry(Milestones.Kind.NETHER, 45000, 2),
                new Milestones.Entry(Milestones.Kind.END, 70000, 2));
        assertEquals(2, splits.worlds.size());
        assertEquals(19000, splits.worlds.get(0).splits.get(Milestones.Kind.NETHER).time);
        assertEquals(45000, splits.worlds.get(1).splits.get(Milestones.Kind.NETHER).time);
        assertNull(splits.worlds.get(0).splits.get(Milestones.Kind.END));
        assertNull(splits.worlds.get(1).splits.get(Milestones.Kind.FORTRESS));
        assertEquals(1, splits.worldAt(60000));
        assertEquals(0, splits.worldAt(35000));
    }

    @Test void retainsRecordedGapSplitsButRejectsBeforeRaceEndBoundaryAndMismatchedWorld() throws Exception {
        ReplaySplits splits = table(read(data(8000)),
                new Milestones.Entry(Milestones.Kind.NETHER, 7000, 1),
                new Milestones.Entry(Milestones.Kind.BASTION, 35000, 1),
                new Milestones.Entry(Milestones.Kind.FORTRESS, 45000, 1),
                new Milestones.Entry(Milestones.Kind.END, 100000, 2));
        assertEquals(1, splits.worlds.get(0).splits.size());
        ReplaySplits.Split gap = splits.worlds.get(0).splits.get(Milestones.Kind.BASTION);
        assertEquals(35000, gap.time);
        assertEquals(27000, gap.elapsed);
        assertFalse(gap.jumpable);
        assertTrue(splits.worlds.get(1).splits.isEmpty());
    }

    @Test void worldsOfTheNextRaceDoNotLeakIntoThisRace() throws Exception {
        JsonObject json = data(8000);
        JsonObject first = json.getAsJsonArray("races").get(0).getAsJsonObject();
        first.addProperty("firstWorldIndex", 0);
        first.addProperty("endOffsetNanos", 50000L * 1000000L);
        JsonObject next = new JsonObject();
        next.addProperty("raceId", "next-race");
        next.addProperty("firstWorldIndex", 1);
        next.addProperty("startOffsetNanos", 50000L * 1000000L);
        next.addProperty("endOffsetNanos", 100000L * 1000000L);
        json.getAsJsonArray("races").add(next);
        ReplaySplits splits = table(read(json), new Milestones.Entry(Milestones.Kind.NETHER, 45000, 2));
        assertEquals(1, splits.worlds.size());
        assertTrue(splits.worlds.get(0).splits.isEmpty());
    }

    @Test void firstRecordedCompletionRemainsTheSplitEvenIfItWasInsideLoading() throws Exception {
        ReplaySplits splits = table(read(data(8000)),
                new Milestones.Entry(Milestones.Kind.END, 35000, 2),
                new Milestones.Entry(Milestones.Kind.END, 45000, 2));
        ReplaySplits.Split first = splits.worlds.get(1).splits.get(Milestones.Kind.END);
        assertEquals(35000, first.time);
        assertFalse(first.jumpable);
    }

    @Test void missingMilestonesAreNotEstimatedFromFinishOrCoverage() throws Exception {
        ReplaySplits splits = table(read(data(8000)));
        assertEquals(2, splits.worlds.size());
        for (ReplaySplits.World world : splits.worlds) assertTrue(world.splits.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> splits.worlds.clear());
    }

    @Test void fractionalRaceStartRetainsCoverageAndFloorsElapsedMilliseconds() throws Exception {
        JsonObject json = data(8000);
        json.getAsJsonArray("races").get(0).getAsJsonObject().addProperty("startOffsetNanos", 8000500000L);
        ReplaySplits splits = table(read(json), new Milestones.Entry(Milestones.Kind.NETHER, 9000, 1));
        assertEquals(999, splits.worlds.get(0).splits.get(Milestones.Kind.NETHER).elapsed);
    }

    @Test void allFollowModesArePreservedButFreecamBecomesPlayerFollow() {
        for (int mode = 0; mode < 6; mode++) assertEquals(mode < 2 ? 2 : mode, ReplaySplits.followMode(mode));
    }

    @Test void dragonCompletionHasAnExplicitRecordedMilestone() {
        assertEquals(Milestones.Kind.DRAGON, Milestones.Kind.fromId("minecraft:end/kill_dragon"));
        Milestones milestones = new Milestones();
        milestones.newWorld();
        milestones.observe(Milestones.Kind.DRAGON, true, false, 25000);
        assertEquals(1, milestones.snapshot().size());
        assertEquals(Milestones.Kind.DRAGON, milestones.snapshot().get(0).kind);
    }
}
