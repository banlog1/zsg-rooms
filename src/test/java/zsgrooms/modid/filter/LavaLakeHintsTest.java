package zsgrooms.modid.filter;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.decorator.ChanceDecoratorConfig;
import net.minecraft.world.gen.decorator.Decorator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LavaLakeHintsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.getGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void attemptPredictionMatchesMinecraftDecoratorAndRandomAdvancement() {
        WorldAccess world = (WorldAccess) Proxy.newProxyInstance(WorldAccess.class.getClassLoader(),
                new Class<?>[]{WorldAccess.class}, (proxy, method, args) -> {
                    if ("getSeaLevel".equals(method.getName())) return 63;
                    throw new AssertionError("Unexpected world access: " + method.getName());
                });
        ChunkGenerator generator = GeneratorOptions.createOverworldGenerator(1);
        int successful = 0;
        for (int i = 0; i < 10000; i++) {
            Random expectedRandom = new Random(i);
            Random actualRandom = new Random(i);
            Optional<BlockPos> expected = Decorator.LAVA_LAKE.getPositions(world, generator, expectedRandom,
                    new ChanceDecoratorConfig(80), new BlockPos(-32, 0, 48)).findFirst();
            Optional<BlockPos> actual = LavaLakeHints.rollPosition(actualRandom, -32, 48, 80, 256, 63);
            assertEquals(expected, actual);
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong());
            if (actual.isPresent()) successful++;
        }
        assertTrue(successful > 0 && successful < 10000);
    }

    @Test
    void accountsForPositiveMaskExtentAtTheSearchBoundary() {
        assertTrue(LavaLakeHints.couldReachSurfaceArea(new BlockPos(-111, 80, 0), 0, 0, 96));
        assertFalse(LavaLakeHints.couldReachSurfaceArea(new BlockPos(-112, 80, 0), 0, 0, 96));
        assertFalse(LavaLakeHints.couldReachSurfaceArea(new BlockPos(80, 80, 80), 0, 0, 96));
        assertFalse(LavaLakeHints.couldReachSurfaceArea(new BlockPos(0, 60, 0), 0, 0, 96));
        assertTrue(LavaLakeHints.couldReachSurfaceArea(new BlockPos(0, 61, 0), 0, 0, 96));
    }
}
