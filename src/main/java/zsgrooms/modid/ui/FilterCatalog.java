package zsgrooms.modid.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class FilterCatalog {
    enum Group {
        ROOMS("ZSG Rooms"), EXISTING("Existing Filters"), OTHER("Other");

        final String label;

        Group(String label) { this.label = label; }
    }

    static final class Entry {
        final String id;
        final Group group;
        final String image;

        Entry(String id, Group group, String image) {
            this.id = id;
            this.group = group;
            this.image = image;
        }
    }

    static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("rooms-mix", Group.ROOMS, null),
            new Entry("rooms-temple-v5", Group.ROOMS, "dt"),
            new Entry("rooms-village-v5", Group.ROOMS, "village"),
            new Entry("rooms-shipwreck-v5", Group.ROOMS, "shipwreck"),
            new Entry("zsg", Group.EXISTING, "bt"),
            new Entry("zsgop", Group.EXISTING, "bt"),
            new Entry("zsgvillage", Group.EXISTING, "village"),
            new Entry("zsgvillageop", Group.EXISTING, "village"),
            new Entry("zsgshipwreck", Group.EXISTING, "shipwreck"),
            new Entry("zsgshipwreckop", Group.EXISTING, "shipwreck"),
            new Entry("zsgtemple", Group.EXISTING, "dt"),
            new Entry("zsgtempleop", Group.EXISTING, "dt"),
            new Entry("zsgjungletemple", Group.EXISTING, null),
            new Entry("zsgjungletempleop", Group.EXISTING, null),
            new Entry("rpseedbank", Group.EXISTING, "rp"),
            new Entry("random", Group.OTHER, null),
            new Entry("room", Group.OTHER, null),
            new Entry("manual", Group.OTHER, null)));

    static List<Entry> entries(Group group) {
        return ENTRIES.stream().filter(entry -> entry.group == group).collect(Collectors.toList());
    }

    static Entry find(String id) {
        return ENTRIES.stream().filter(entry -> entry.id.equals(id)).findFirst().orElse(ENTRIES.get(4));
    }

    private FilterCatalog() {}
}
