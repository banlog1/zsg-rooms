// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure, immutable geometry shared by rendering, tooltips and clicks. */
final class MilestonePlacement {
    static final int HEIGHT = 16;
    static final int MARKER_WIDTH = 16;
    static final int GAP = 2;
    static final int TOOLTIP_EVENTS = 5;
    final int width;
    final List<Group> groups;

    MilestonePlacement(List<Milestones.Entry> source, int width, int duration) {
        this.width = Math.max(0, width);
        List<Milestones.Entry> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingInt((Milestones.Entry entry) -> entry.time)
                .thenComparingInt(entry -> entry.kind.ordinal()).thenComparingInt(entry -> entry.world));
        List<Group> placed = new ArrayList<>();
        int start = 0;
        while (start < sorted.size()) {
            int first = position(sorted.get(start).time, duration);
            int last = first;
            int end = start + 1;
            // Connected collisions collapse together; no marker/hit rectangles can overlap.
            while (end < sorted.size()) {
                int next = position(sorted.get(end).time, duration);
                if (next - last >= MARKER_WIDTH + GAP) break;
                last = next;
                end++;
            }
            int center = first + (last - first) / 2;
            int left = Math.max(0, center - MARKER_WIDTH / 2);
            placed.add(new Group(left, Math.min(this.width, left + MARKER_WIDTH),
                    sorted.subList(start, end)));
            start = end;
        }
        groups = Collections.unmodifiableList(placed);
    }

    private int position(int time, int duration) {
        int margin = Math.min(MARKER_WIDTH / 2, width / 2);
        long clamped = Math.max(0, Math.min(time, Math.max(1, duration)));
        return margin + (int) (clamped * Math.max(0, width - MARKER_WIDTH) / Math.max(1, duration));
    }

    Group hit(int x, int y, int height) {
        if (x < 0 || x >= width || y < 0 || y >= Math.min(HEIGHT, height)) return null;
        int low = 0, high = groups.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Group group = groups.get(middle);
            if (x < group.left) high = middle - 1;
            else if (x >= group.right) low = middle + 1;
            else return group;
        }
        return null;
    }

    static String eventText(Milestones.Entry entry) {
        long time = Math.max(0, entry.time);
        return entry.kind.description + " - " + String.format(Locale.ROOT, "%d:%02d.%03d",
                time / 60000, time / 1000 % 60, time % 1000) + " (world " + entry.world + ")";
    }

    static final class Group {
        final int left, right;
        final List<Milestones.Entry> entries;

        Group(int left, int right, List<Milestones.Entry> entries) {
            this.left = left;
            this.right = right;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }

        boolean grouped() { return entries.size() > 1; }

        String[] tooltip() {
            List<String> lines = new ArrayList<>();
            if (grouped()) lines.add(entries.size() + " milestones");
            for (int i = 0; i < Math.min(TOOLTIP_EVENTS, entries.size()); i++) {
                lines.add(eventText(entries.get(i)));
            }
            if (entries.size() > TOOLTIP_EVENTS) lines.add("+" + (entries.size() - TOOLTIP_EVENTS) + " more");
            return lines.toArray(new String[0]);
        }

        int pages(int pageSize) { return (entries.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize); }

        List<Milestones.Entry> page(int page, int pageSize) {
            int size = Math.max(1, pageSize);
            int start = Math.max(0, Math.min(page, pages(size) - 1)) * size;
            return entries.subList(start, Math.min(entries.size(), start + size));
        }
    }
}
