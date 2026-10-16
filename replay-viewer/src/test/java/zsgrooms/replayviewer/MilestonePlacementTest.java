// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MilestonePlacementTest {
    private static Milestones.Entry event(int time, Milestones.Kind kind, int world) {
        return new Milestones.Entry(kind, time, world);
    }

    @Test void endpointsAreVisibleAndProjectionClampsWithoutOverflow() {
        MilestonePlacement layout = new MilestonePlacement(Arrays.asList(
                event(Integer.MIN_VALUE, Milestones.Kind.NETHER, 1),
                event(Integer.MAX_VALUE, Milestones.Kind.END, 2)), 100, Integer.MAX_VALUE);
        assertEquals(0, layout.groups.get(0).left);
        assertEquals(100, layout.groups.get(1).right);
        assertSame(layout.groups.get(0), layout.hit(0, 0, 16));
        assertSame(layout.groups.get(1), layout.hit(99, 15, 16));
        MilestonePlacement largest = new MilestonePlacement(Arrays.asList(
                event(0, Milestones.Kind.NETHER, 1), event(Integer.MAX_VALUE, Milestones.Kind.END, 1)),
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, largest.groups.get(1).right);
    }

    @Test void nearbyEventsCollapseAndTiesSortByKindThenWorld() {
        Milestones.Entry end = event(1000, Milestones.Kind.END, 1);
        Milestones.Entry second = event(1000, Milestones.Kind.NETHER, 2);
        Milestones.Entry first = event(1000, Milestones.Kind.NETHER, 1);
        MilestonePlacement layout = new MilestonePlacement(Arrays.asList(end, second, first), 200, 10000);
        assertEquals(1, layout.groups.size());
        assertEquals(Arrays.asList(first, second, end), layout.groups.get(0).entries);
        assertTrue(layout.groups.get(0).grouped());
    }

    @Test void collisionThresholdAndConnectedChainsAreExplicit() {
        // With width 116 and duration 100, timestamps map one-to-one onto pixels plus eight.
        MilestonePlacement grouped = new MilestonePlacement(Arrays.asList(
                event(0, Milestones.Kind.NETHER, 1), event(17, Milestones.Kind.BASTION, 1),
                event(34, Milestones.Kind.FORTRESS, 1)), 116, 100);
        assertEquals(1, grouped.groups.size());
        assertEquals(17, grouped.groups.get(0).left);
        MilestonePlacement separate = new MilestonePlacement(Arrays.asList(
                event(0, Milestones.Kind.NETHER, 1), event(18, Milestones.Kind.END, 1)), 116, 100);
        assertEquals(2, separate.groups.size());
        assertNull(separate.hit(16, 5, 16));
        assertNull(separate.hit(17, 5, 16));
        assertSame(separate.groups.get(1), separate.hit(18, 5, 16));
    }

    @Test void narrowAndEmptyLayoutsStayInsideTheirBounds() {
        List<Milestones.Entry> events = Arrays.asList(event(-5, Milestones.Kind.NETHER, 1),
                event(1000, Milestones.Kind.END, 1));
        for (int width : new int[] {-1, 0, 1, 2, 7, 15, 16, 17}) {
            MilestonePlacement layout = new MilestonePlacement(events, width, 0);
            assertEquals(1, layout.groups.size());
            MilestonePlacement.Group group = layout.groups.get(0);
            assertEquals(0, group.left);
            assertEquals(Math.max(0, Math.min(16, width)), group.right);
            assertNull(layout.hit(-1, 0, 16));
            assertNull(layout.hit(Math.max(0, width), 0, 16));
            if (width > 0) assertSame(group, layout.hit(0, 0, 16));
        }
        MilestonePlacement empty = new MilestonePlacement(Collections.emptyList(), 100, 1000);
        assertTrue(empty.groups.isEmpty());
        assertNull(empty.hit(3, 8, 16));
    }

    @Test void hitTestingUsesHalfOpenBoundsAndNeverCapturesTimelineBelow() {
        MilestonePlacement layout = new MilestonePlacement(Collections.singletonList(
                event(0, Milestones.Kind.NETHER, 1)), 100, 1000);
        MilestonePlacement.Group group = layout.groups.get(0);
        assertSame(group, layout.hit(15, 15, 20));
        assertNull(layout.hit(16, 15, 20));
        assertNull(layout.hit(15, 16, 20));
        assertNull(layout.hit(15, 14, 14));
        assertSame(group, layout.hit(15, 13, 14));
        assertNull(layout.hit(15, -1, 16));
        assertNull(layout.hit(15, 0, 0));
    }

    @Test void everyEventSurvivesDenseGroupingAndEveryChooserPageIsReachable() {
        List<Milestones.Entry> events = new ArrayList<>();
        for (int i = 0; i < 2048; i++) events.add(event(1234, Milestones.Kind.values()[i % 5], i + 1));
        MilestonePlacement.Group group = new MilestonePlacement(events, 320, 10000).groups.get(0);
        assertEquals(2048, group.entries.size());
        for (int size = 1; size <= 6; size++) {
            List<Milestones.Entry> reached = new ArrayList<>();
            for (int page = 0; page < group.pages(size); page++) reached.addAll(group.page(page, size));
            assertEquals(group.entries, reached);
            assertEquals(group.page(0, size), group.page(-1, size));
            assertEquals(group.page(group.pages(size) - 1, size), group.page(Integer.MAX_VALUE, size));
        }
        assertEquals(2048, group.pages(0));
    }

    @Test void tooltipIsBoundedAndIncludesExactEventTimeAndWorld() {
        List<Milestones.Entry> events = new ArrayList<>();
        for (int i = 0; i < 2048; i++) events.add(event(61234 + i, Milestones.Kind.FORTRESS, i + 1));
        MilestonePlacement.Group group = new MilestonePlacement(events, 16, 100000).groups.get(0);
        String[] lines = group.tooltip();
        assertEquals(7, lines.length);
        assertEquals("2048 milestones", lines[0]);
        assertEquals("Entered Fortress - 1:01.234 (world 1)", lines[1]);
        assertEquals("+2043 more", lines[6]);
        assertEquals("Entered Fortress - 1:03.281 (world 2048)",
                MilestonePlacement.eventText(group.page(341, 6).get(1)));
        MilestonePlacement.Group single = new MilestonePlacement(events.subList(0, 1), 100, 100000).groups.get(0);
        assertFalse(single.grouped());
        assertArrayEquals(new String[] {lines[1]}, single.tooltip());
    }

    @Test void timestampFormattingDoesNotDependOnLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"));
            assertEquals("Entered End - 60:00.001 (world 2)",
                    MilestonePlacement.eventText(event(3600001, Milestones.Kind.END, 2)));
        } finally { Locale.setDefault(previous); }
    }

    @Test void layoutIsImmutableAndIndependentOfInputOrder() {
        Random random = new Random(781);
        List<Milestones.Entry> events = new ArrayList<>();
        for (int i = 0; i < 150; i++) events.add(event(random.nextInt(100000), Milestones.Kind.values()[i % 5], i));
        MilestonePlacement first = new MilestonePlacement(events, 600, 100000);
        Collections.shuffle(events, random);
        MilestonePlacement second = new MilestonePlacement(events, 600, 100000);
        assertEquals(first.groups.size(), second.groups.size());
        int total = 0;
        for (int i = 0; i < first.groups.size(); i++) {
            MilestonePlacement.Group a = first.groups.get(i), b = second.groups.get(i);
            assertEquals(a.left, b.left);
            assertEquals(a.right, b.right);
            assertEquals(a.entries, b.entries);
            total += a.entries.size();
            if (i > 0) assertTrue(first.groups.get(i - 1).right + 2 <= a.left);
            for (int x = a.left; x < a.right; x++) assertSame(a, first.hit(x, 8, 16));
        }
        assertEquals(events.size(), total);
        events.clear();
        assertEquals(150, first.groups.stream().mapToInt(group -> group.entries.size()).sum());
        assertThrows(UnsupportedOperationException.class, () -> first.groups.clear());
        assertThrows(UnsupportedOperationException.class, () -> first.groups.get(0).entries.clear());
        assertThrows(UnsupportedOperationException.class, () -> first.groups.get(0).page(0, 6).clear());
    }
}
