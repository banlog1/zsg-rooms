// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Race-local splits, kept separate for every recorded world/reset. */
final class ReplaySplits {
    final RaceReplayLibrary.Entry entry;
    final List<World> worlds;

    ReplaySplits(RaceReplayLibrary.Entry entry, List<Milestones.Entry> events) {
        this.entry = entry;
        RaceRecording recording = entry.recording;
        RaceRecording.Race race = entry.race;
        Map<Integer, World> found = new LinkedHashMap<>();
        for (RaceRecording.Interval interval : recording.intervals) {
            long start = Math.max(race.start, interval.start * 1000000L);
            int time = (int) ((start + 999999L) / 1000000L);
            if (time < interval.end && recording.targetTime(race, time * 1000000L - race.start) == time) {
                found.putIfAbsent(interval.world, new World(interval.world));
            }
        }
        for (Milestones.Entry event : events) {
            long nanos = event.time * 1000000L;
            if (event.time < 0 || nanos < race.start || nanos >= race.end || event.time >= recording.duration) continue;
            RaceRecording.Interval interval = recording.intervalAt(event.time);
            // Packet JoinGame ordinals are one-based; manifest world indices are zero-based.
            if (interval != null && event.world != interval.world + 1) continue;
            World world = found.get(event.world - 1);
            if (world == null) continue;
            Split previous = world.splits.get(event.kind);
            if (previous == null || event.time < previous.time) {
                world.splits.put(event.kind, new Split(event.kind, event.time, world.index,
                        (event.time * 1000000L - race.start) / 1000000L,
                        recording.timing.at(event.time).igt, covered(recording, race, event.time)));
            }
        }
        worlds = Collections.unmodifiableList(new ArrayList<>(found.values()));
    }

    static boolean covered(RaceRecording recording, RaceRecording.Race race, int time) {
        return time >= 0 && time < recording.duration
                && recording.targetTime(race, time * 1000000L - race.start) == time;
    }

    int worldAt(int time) {
        RaceRecording.Interval interval = entry.recording.intervalAt(time);
        if (interval != null) for (int i = 0; i < worlds.size(); i++) {
            if (worlds.get(i).index == interval.world) return i;
        }
        return 0;
    }

    static int followMode(int current) { return current >= 2 && current <= 5 ? current : 2; }

    static final class World {
        final int index;
        final Map<Milestones.Kind, Split> splits = new EnumMap<>(Milestones.Kind.class);
        World(int index) { this.index = index; }
    }

    static final class Split {
        final Milestones.Kind kind;
        final int time, world;
        final long elapsed, igt;
        final boolean jumpable;
        Split(Milestones.Kind kind, int time, int world, long elapsed, long igt, boolean jumpable) {
            this.kind = kind; this.time = time; this.world = world; this.elapsed = elapsed; this.igt = igt;
            this.jumpable = jumpable;
        }
    }
}
