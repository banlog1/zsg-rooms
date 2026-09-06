package zsgrooms.modid.rng;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

public final class FortressSpawnOrder {
    private static final ThreadLocal<FortressSpawnOrder> ACTIVE = new ThreadLocal<FortressSpawnOrder>();
    private final ServerWorld world;
    private final Map<Long, WorldChunk> targets;

    private FortressSpawnOrder(ServerWorld world, List<WorldChunk> chunks) {
        this.world = world;
        this.targets = orderedTargets(chunks, chunk -> chunk.getPos().toLong());
    }

    public static void prepare(ServerWorld world, List<WorldChunk> chunks) {
        clear();
        if (!chunks.isEmpty()) {
            ACTIVE.set(new FortressSpawnOrder(world, chunks));
        }
    }

    // Reassign only eligible monster-spawn slots; leave vanilla chunk ticking order untouched.
    static <T> Map<Long, T> orderedTargets(List<T> slots, ToLongFunction<T> position) {
        List<T> sorted = new ArrayList<T>(slots);
        sorted.sort((left, right) -> {
            long a = position.applyAsLong(left);
            long b = position.applyAsLong(right);
            int xOrder = Integer.compare(ChunkPos.getPackedX(a), ChunkPos.getPackedX(b));
            return xOrder != 0 ? xOrder : Integer.compare(ChunkPos.getPackedZ(a), ChunkPos.getPackedZ(b));
        });
        Map<Long, T> result = new HashMap<Long, T>();
        for (int i = 0; i < slots.size(); i++) {
            result.put(position.applyAsLong(slots.get(i)), sorted.get(i));
        }
        return result;
    }

    public static WorldChunk target(ServerWorld world, WorldChunk original) {
        FortressSpawnOrder order = ACTIVE.get();
        if (order == null || order.world != world) {
            return original;
        }
        return order.targets.getOrDefault(original.getPos().toLong(), original);
    }

    public static void clear() {
        ACTIVE.remove();
    }
}
