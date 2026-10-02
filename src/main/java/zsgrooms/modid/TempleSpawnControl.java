package zsgrooms.modid;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.StructureFeature;

public final class TempleSpawnControl {
    private static volatile MinecraftServer enabledServer;

    private TempleSpawnControl() { }

    public static void configure(MinecraftServer server, boolean enabled) {
        enabledServer = enabled ? server : null;
    }

    public static void stop(MinecraftServer server) {
        if (enabledServer == server) enabledServer = null;
    }

    public static boolean shouldReject(ServerWorld world, SpawnGroup group, BlockPos pos) {
        if (enabledServer == null || world.getServer() != enabledServer
                || world.getRegistryKey() != World.OVERWORLD || group != SpawnGroup.MONSTER) return false;
        // Uses this loaded chunk's structure references, not a locate search or a world scan.
        return world.getStructureAccessor()
                .getStructuresWithChildren(ChunkSectionPos.from(pos), StructureFeature.DESERT_PYRAMID)
                .anyMatch(start -> start.getChildren().stream().anyMatch(piece -> contains(piece.getBoundingBox(), pos)));
    }

    static boolean contains(BlockBox box, BlockPos pos) {
        if (box.contains(pos)) return true;
        // Vanilla 1.16.1 builds the central chamber below the piece's declared bounding box.
        return pos.getY() >= box.minY - 14 && pos.getY() < box.minY
                && pos.getX() >= box.minX + 7 && pos.getX() <= box.minX + 13
                && pos.getZ() >= box.minZ + 7 && pos.getZ() <= box.minZ + 13;
    }
}
