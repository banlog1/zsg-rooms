package zsgrooms.modid;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.world.ServerWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class RuinedPortalGenerationTracker {
    private static final int MAX_PORTALS_PER_WORLD = 128;
    private static final Map<ServerWorld, List<BlockBox>> GENERATED_PORTALS =
            new WeakHashMap<ServerWorld, List<BlockBox>>();

    private RuinedPortalGenerationTracker() {
    }

    public static synchronized void markGenerated(ServerWorldAccess worldAccess, BlockBox bounds) {
        if (worldAccess == null || bounds == null || !(worldAccess.getWorld() instanceof ServerWorld)) {
            return;
        }
        ServerWorld world = (ServerWorld) worldAccess.getWorld();
        List<BlockBox> portals = GENERATED_PORTALS.get(world);
        if (portals == null) {
            portals = new ArrayList<BlockBox>();
            GENERATED_PORTALS.put(world, portals);
        }
        BlockBox generated = new BlockBox(bounds);
        for (BlockBox existing : portals) {
            if (sameBounds(existing, generated)) {
                return;
            }
        }
        if (portals.size() >= MAX_PORTALS_PER_WORLD) {
            portals.remove(0);
        }
        portals.add(generated);
    }

    public static synchronized boolean wasGenerated(ServerWorld world, BlockBox bounds) {
        if (world == null || bounds == null) {
            return false;
        }
        List<BlockBox> portals = GENERATED_PORTALS.get(world);
        if (portals == null) {
            return false;
        }
        for (BlockBox generated : portals) {
            if (generated.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameBounds(BlockBox first, BlockBox second) {
        return first.minX == second.minX && first.minY == second.minY && first.minZ == second.minZ
                && first.maxX == second.maxX && first.maxY == second.maxY && first.maxZ == second.maxZ;
    }
}
