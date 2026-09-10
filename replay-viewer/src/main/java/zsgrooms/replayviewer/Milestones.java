// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Milestones {
    enum Kind {
        NETHER("minecraft:story/enter_the_nether", "Nether", "Entered Nether", 0xFFFF7777),
        BASTION("minecraft:nether/find_bastion", "Bastion", "Entered Bastion", 0xFFCCAADD),
        FORTRESS("minecraft:nether/find_fortress", "Fortress", "Entered Fortress", 0xFFFFBB77),
        STRONGHOLD("minecraft:story/follow_ender_eye", "Stronghold", "Found Stronghold", 0xFF99DDCC),
        END("minecraft:story/enter_the_end", "End", "Entered End", 0xFFEEEE99);

        final String id;
        final String label;
        final String description;
        final int color;
        Kind(String id, String label, String description, int color) {
            this.id = id; this.label = label; this.description = description; this.color = color;
        }
        static Kind fromId(String id) {
            for (Kind kind : values()) if (kind.id.equals(id)) return kind;
            return null;
        }
    }

    static final class Entry {
        final Kind kind;
        final int time;
        final int world;
        Entry(Kind kind, int time, int world) { this.kind = kind; this.time = time; this.world = world; }
        String tooltip() { return kind.description + " - " + ViewerLayout.time(time) + " (world " + world + ")"; }
    }

    private final Set<Kind> seen = new HashSet<>();
    private final List<Entry> entries = new ArrayList<>();
    private int world;

    void newWorld() { world++; seen.clear(); }

    void observe(Kind kind, boolean done, boolean initialSnapshot, int time) {
        if (kind != null && done && seen.add(kind) && !initialSnapshot && entries.size() < 2048) {
            entries.add(new Entry(kind, time, Math.max(1, world)));
        }
    }

    List<Entry> snapshot() {
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparingInt((Entry entry) -> entry.time).thenComparingInt(entry -> entry.kind.ordinal()));
        return Collections.unmodifiableList(copy);
    }
}
