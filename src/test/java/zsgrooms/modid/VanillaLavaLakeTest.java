package zsgrooms.modid;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.SingleStateFeatureConfig;
import net.minecraft.world.gen.feature.StructureFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.modid.SurfacePoolRepair.Kind.*;

class VanillaLavaLakeTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.getGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void cavityDepthAndStoneEdgingMatchActualMinecraftLakeFeature() {
        for (long seed = 0; seed < 128; seed++) {
            Map<BlockPos, BlockState> vanilla = new HashMap<>();
            ServerWorldAccess world = (ServerWorldAccess) Proxy.newProxyInstance(ServerWorldAccess.class.getClassLoader(),
                    new Class<?>[]{ServerWorldAccess.class}, (proxy, method, args) -> {
                        BlockPos pos = args != null && args.length > 0 && args[0] instanceof BlockPos ? (BlockPos) args[0] : null;
                        if ("getBlockState".equals(method.getName())) return vanilla.getOrDefault(pos,
                                pos.getY() <= 64 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
                        if ("isAir".equals(method.getName())) return pos.getY() > 64;
                        if ("setBlockState".equals(method.getName())) {
                            vanilla.put(pos.toImmutable(), (BlockState) args[1]);
                            return true;
                        }
                        throw new AssertionError("Unexpected lake world access: " + method.getName());
                    });
            StructureAccessor noVillages = new StructureAccessor(world, null) {
                @Override
                public Stream<? extends StructureStart<?>> getStructuresWithChildren(ChunkSectionPos pos, StructureFeature<?> feature) {
                    return Stream.empty();
                }
            };
            assertTrue(Feature.LAKE.generate(world, noVillages, null, new Random(seed), CENTER.add(-8, 0, -8),
                    new SingleStateFeatureConfig(Blocks.LAVA.getDefaultState())));
            SurfacePoolRepair.Terrain terrain = new StoneTerrain();
            Map<BlockPos, SurfacePoolRepair.Kind> plan = VanillaLavaLake.plan(terrain, CENTER, seed);
            assertNotNull(plan);
            Map<BlockPos, BlockState> actual = new HashMap<>();
            plan.forEach((pos, kind) -> actual.put(pos, kind == LAVA ? Blocks.LAVA.getDefaultState()
                    : kind == CAVE_AIR ? Blocks.CAVE_AIR.getDefaultState() : Blocks.STONE.getDefaultState()));
            assertEquals(vanilla, actual, "Geometry/lining mismatch for sample " + seed);
        }
    }

    @Test
    void locationSeedsAreIndependentRepeatableAndProduceDifferentShapes() {
        long seed = SurfacePoolRepair.lakeSeed(91, "rooms-temple-v5", CENTER);
        long other = SurfacePoolRepair.lakeSeed(91, "rooms-temple-v5", CENTER.east(4));
        assertNotEquals(seed, other);
        Map<BlockPos, SurfacePoolRepair.Kind> expected = VanillaLavaLake.plan(new StoneTerrain(), CENTER, seed);
        VanillaLavaLake.plan(new StoneTerrain(), CENTER.east(4), other);
        assertEquals(expected, VanillaLavaLake.plan(new StoneTerrain(), CENTER, seed));
        Set<Set<BlockPos>> outlines = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            Map<BlockPos, SurfacePoolRepair.Kind> plan = VanillaLavaLake.plan(new StoneTerrain(), CENTER, i);
            Set<BlockPos> surface = new HashSet<>();
            plan.forEach((pos, kind) -> { if (kind == LAVA && pos.getY() == 63) surface.add(pos); });
            outlines.add(surface);
            assertTrue(surface.size() > 9);
            int minX = surface.stream().mapToInt(BlockPos::getX).min().getAsInt();
            int maxX = surface.stream().mapToInt(BlockPos::getX).max().getAsInt();
            int minZ = surface.stream().mapToInt(BlockPos::getZ).min().getAsInt();
            int maxZ = surface.stream().mapToInt(BlockPos::getZ).max().getAsInt();
            assertTrue(surface.size() < (maxX - minX + 1) * (maxZ - minZ + 1), "Not a rectangular basin");
        }
        assertEquals(32, outlines.size());
    }

    private static final class StoneTerrain implements SurfacePoolRepair.Terrain {
        public int surfaceY(int x, int z) { return 64; }
        public SurfacePoolRepair.Kind kind(BlockPos pos) { return pos.getY() <= 64 ? STONE : AIR; }
        public boolean protectedAt(BlockPos pos) { return false; }
        public boolean unfrozen(BlockPos pos) { return true; }
        public boolean apply(Map<BlockPos, SurfacePoolRepair.Kind> patch) { throw new AssertionError("Planner wrote terrain"); }
    }
}
