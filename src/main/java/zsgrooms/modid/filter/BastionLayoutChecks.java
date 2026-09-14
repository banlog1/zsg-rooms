package zsgrooms.modid.filter;

import java.util.Collection;

/** Minecraft 1.16.1 template identities, independent of a piece's world position or rotation. */
public final class BastionLayoutChecks {
    private static final String STABLES = "minecraft:bastion/hoglin_stable/";

    private BastionLayoutChecks() {
    }

    public static boolean isStables(Collection<String> templates) {
        return templates.stream().anyMatch(value -> value.startsWith(STABLES));
    }

    public static boolean hasGoodGap(Collection<String> templates) {
        return templates.contains(STABLES + "walls/side_wall_1");
    }

    public static boolean hasTripleRampart(Collection<String> templates) {
        return templates.contains(STABLES + "ramparts/ramparts_1");
    }

    public static boolean accepts(Collection<String> templates) {
        return !templates.isEmpty() && (!isStables(templates) || hasGoodGap(templates) && hasTripleRampart(templates));
    }
}
