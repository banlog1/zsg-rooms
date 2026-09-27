package zsgrooms.modid;

import java.util.function.Function;
import java.util.function.Predicate;

/** The AA race goal is independent of advancement announcements and their packet visibility. */
public final class AaThunderless {
    public static final String FILTER = "rooms-aa-thunderless-v4";
    public static final String LABEL = "AA Thunderless";
    public static final String EXCLUDED = "minecraft:adventure/very_very_frightening";
    public static final String GOAL = "All advancements except Very Very Frightening";
    public static final String DESCRIPTION = "Win condition: " + GOAL
            + ". Exiting the End does not finish the race. Desert temple AA seed filter.";

    private AaThunderless() {}

    public static boolean isFilter(String filter) {
        return FILTER.equals(filter);
    }

    public static boolean isRequired(String id) {
        if (id == null || EXCLUDED.equals(id) || id.endsWith("/root")) return false;
        return id.startsWith("minecraft:story/") || id.startsWith("minecraft:nether/")
                || id.startsWith("minecraft:end/") || id.startsWith("minecraft:adventure/")
                || id.startsWith("minecraft:husbandry/");
    }

    static <T> boolean isComplete(Iterable<T> advancements, Function<T, String> id, Predicate<T> done) {
        boolean hasRequired = false;
        for (T advancement : advancements) {
            if (!isRequired(id.apply(advancement))) continue;
            hasRequired = true;
            if (!done.test(advancement)) return false;
        }
        return hasRequired;
    }
}
