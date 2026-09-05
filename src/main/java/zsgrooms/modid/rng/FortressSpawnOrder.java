package zsgrooms.modid.rng;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FortressSpawnOrder {
    private static final ThreadLocal<FortressSpawnOrder> ACTIVE = new ThreadLocal<FortressSpawnOrder>();
    private final ServerWorld world;
    private final Map<Long, WorldChunk> targets = new HashMap<Long, WorldChunk>();

    private FortressSpawnOrder(ServerWorld world, List<WorldChunk> chunks) {
        this.world = world;
        List<ChunkPos> positions = new ArrayList<ChunkPos>();
        Map<Long, WorldChunk> byPosition = new HashMap<Long, WorldChunk>();
        for (WorldChunk chunk : chunks) {
            positions.add(chunk.getPos());
            byPosition.put(chunk.getPos().toLong(), chunk);
        }
        for (Map.Entry<Long, Long> entry : orderedTargets(positions).entrySet()) {
            this.targets.put(entry.getKey(), byPosition.get(entry.getValue()));
        }
    }

    public static void prepare(ServerWorld world, List<WorldChunk> chunks) {
        clear();
        if (!chunks.isEmpty()) {
            ACTIVE.set(new FortressSpawnOrder(world, chunks));
        }
    }

    // Reassign only eligible monster-spawn slots; leave vanilla chunk ticking order untouched.
    static Map<Long, Long> orderedTargets(List<ChunkPos> slots) {
        List<ChunkPos> sorted = new ArrayList<ChunkPos>(slots);
        sorted.sort(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
        Map<Long, Long> result = new HashMap<Long, Long>();
        for (int i = 0; i < slots.size(); i++) {
            result.put(slots.get(i).toLong(), sorted.get(i).toLong());
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
