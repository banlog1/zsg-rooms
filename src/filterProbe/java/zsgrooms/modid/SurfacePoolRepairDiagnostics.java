package zsgrooms.modid;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.TreeMap;

/** Offline-only explanation of safe-site rejection counts. */
public final class SurfacePoolRepairDiagnostics {
    public static final int SCAN_RADIUS = SurfacePoolRepair.SCAN_RADIUS;
    public static final int MAX_POOL_RADIUS = SurfacePoolRepair.MAX_POOL_RADIUS;
    private SurfacePoolRepairDiagnostics() { }

    public static void inspect(ServerWorld world, BlockPos origin, String profile) {
        SurfaceLavaPoolGuarantee.MinecraftTerrain terrain = new SurfaceLavaPoolGuarantee.MinecraftTerrain(world, origin);
        terrain.prepare(SCAN_RADIUS);
        Map<String, Integer> reasons = new TreeMap<>();
        for (BlockPos column : SurfacePoolRepair.candidates(origin, world.getSeed(), profile)) {
            BlockPos center = new BlockPos(column.getX(), terrain.surfaceY(column.getX(), column.getZ()), column.getZ());
            String reason;
            if (center.getY() < SurfacePoolRepair.MIN_POOL_Y || center.getY() > SurfacePoolRepair.MAX_POOL_Y) reason = "height";
            else if (terrain.protectedAt(center)) reason = "protected center";
            else reason = VanillaLavaLake.plan(terrain, center, SurfacePoolRepair.lakeSeed(world.getSeed(), profile, center)) == null
                    ? "lake terrain/clearance rejected" : "safe lake cavity (before water/access checks)";
            reasons.put(reason, reasons.getOrDefault(reason, 0) + 1);
        }
        ZsgRooms.LOGGER.info("[SurfacePoolTest] candidate sites: {}", reasons);
    }

    public static boolean separated(BlockPos first, BlockPos second, BlockPos origin) {
        return SurfacePoolRepair.separated(second, java.util.Collections.singletonList(first), origin, false);
    }

}
