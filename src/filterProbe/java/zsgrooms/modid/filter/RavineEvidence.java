package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RavineEvidence {
    private static final ThreadLocal<Long> SEED = new ThreadLocal<Long>();
    private static final ConcurrentHashMap<Long, Set<Middle>> MIDDLES = new ConcurrentHashMap<Long, Set<Middle>>();

    public static final class Middle {
        public final double x, y, z, radius;
        Middle(double x, double y, double z, double radius) { this.x = x; this.y = y; this.z = z; this.radius = radius; }
        public BlockPos pos() { return new BlockPos(x, y, z); }
        @Override public int hashCode() { return Objects.hash(x, y, z, radius); }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Middle)) return false;
            Middle other = (Middle) value;
            return x == other.x && y == other.y && z == other.z && radius == other.radius;
        }
    }

    public static Long enter(long seed) {
        Long previous = SEED.get();
        SEED.set(seed);
        return previous;
    }

    public static void restore(Long seed) {
        if (seed == null) SEED.remove(); else SEED.set(seed);
    }

    public static void middle(double x, double y, double z, double radius) {
        Long seed = SEED.get();
        if (seed != null && radius >= 18 && y - radius <= 8 && y + radius >= 40) {
            MIDDLES.computeIfAbsent(seed, key -> ConcurrentHashMap.newKeySet()).add(new Middle(x, y, z, radius));
        }
    }

    public static List<Middle> get(long seed) {
        List<Middle> result = new ArrayList<Middle>(MIDDLES.getOrDefault(seed, java.util.Collections.emptySet()));
        result.sort(Comparator.comparingDouble((Middle value) -> value.x).thenComparingDouble(value -> value.z)
                .thenComparingDouble(value -> value.y).thenComparingDouble(value -> value.radius));
        return result;
    }

    static void clear(long seed) { MIDDLES.remove(seed); }
}
