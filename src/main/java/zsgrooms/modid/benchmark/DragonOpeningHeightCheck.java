package zsgrooms.modid.benchmark;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.HoldingPatternPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.ZsgRooms;

import java.util.Random;

/** Opt-in perch benchmark preflight: exercises the actual mapped holding-pattern invocation. */
final class DragonOpeningHeightCheck {
    private DragonOpeningHeightCheck() {
    }

    static void run(ServerWorld world) {
        int checks = 0;
        try {
            for (int sample = 0; sample < 16; sample++) {
                for (int age : new int[]{0, 1299, 1300}) {
                    Random nativeRandom = new Random(sample);
                    nativeRandom.nextInt(8);
                    float nativeHeight = nativeRandom.nextFloat();
                    long followingDraw = nativeRandom.nextLong();
                    Vec3d vanilla = target(world, sample, age, false, false, followingDraw);
                    double nodeY = vanilla.y - nativeHeight * 20.0F;
                    check(Math.abs(nodeY - Math.rint(nodeY)) < 0.00001D, "Unexpected native height expression");
                    for (boolean standardized : new boolean[]{false, true}) {
                        for (boolean assisted : new boolean[]{false, true}) {
                            RngStandardization.configure(standardized, false, assisted);
                            Random expectedRandom = new Random(sample);
                            expectedRandom.nextInt(8);
                            float expected = RngStandardization.nextDragonOpeningHeightRoll(
                                    expectedRandom, age, world.getSeed());
                            Vec3d actual = target(world, sample, age, standardized, assisted, followingDraw);
                            check(actual.x == vanilla.x && actual.z == vanilla.z, "Horizontal target changed");
                            check(Math.abs(actual.y - (nodeY + expected * 20.0F)) < 0.00001D,
                                    "Height mixin did not use the expected roll");
                            checks++;
                        }
                    }
                }
            }
            ZsgRooms.LOGGER.info("[DragonOpeningHeightCheck] PASS: {} real target selections; native RNG and horizontal targets preserved", checks);
        } finally {
            RngStandardization.configure(false);
        }
    }

    private static Vec3d target(ServerWorld world, int seed, int age, boolean standardized,
                               boolean assisted, long followingDraw) {
        RngStandardization.configure(standardized, false, assisted);
        EnderDragonEntity dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, world);
        dragon.refreshPositionAndAngles(0, 128, 0, 0, 0);
        dragon.age = age;
        dragon.getRandom().setSeed(seed);
        HoldingPatternPhase phase = dragon.getPhaseManager().create(PhaseType.HOLDING_PATTERN);
        phase.beginPhase();
        phase.serverTick();
        Vec3d target = phase.getTarget();
        check(target != null, "No initial holding-pattern target");
        check(dragon.getRandom().nextLong() == followingDraw, "Native RNG advancement changed");
        return target;
    }

    private static void check(boolean passed, String message) {
        if (!passed) {
            throw new IllegalStateException("[DragonOpeningHeightCheck] FAIL: " + message);
        }
    }
}
