package zsgrooms.modid.benchmark;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.HoldingPatternPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.mixin.EnderDragonFightAccessor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Random;

/** Development-only check of the real perch redirect, including the vanilla zero-cycle window. */
final class DragonPerchRollCheck {
    private DragonPerchRollCheck() {}

    static void run(ServerWorld world) throws ReflectiveOperationException {
        Field pathField = null;
        for (Field field : HoldingPatternPhase.class.getDeclaredFields()) {
            if (field.getType() == Path.class) pathField = field;
        }
        check(pathField != null, "Holding-pattern path field is unavailable");
        pathField.setAccessible(true);
        int previousCrystals = world.getEnderDragonFight().getAliveEndCrystals();
        int checks = 0;
        try {
            for (int crystals : new int[]{0, 10}) {
                ((EnderDragonFightAccessor) world.getEnderDragonFight()).zsgRooms$setEndCrystalsAlive(crystals);
                int bound = crystals + 3;
                for (int sample = 0; sample < 16; sample++) {
                    for (int age : new int[]{0, 1299, 1300}) {
                        for (boolean enabled : new boolean[]{false, true}) {
                            prepare(enabled, sample, bound, world.getSeed());
                            Random expectedRandom = new Random(sample);
                            int expected = RngStandardization.nextDragonPerchRoll(expectedRandom, bound, age, world.getSeed());
                            prepare(enabled, sample, bound, world.getSeed());
                            EnderDragonEntity dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, world);
                            dragon.refreshPositionAndAngles(0, 128, 0, 0, 0);
                            dragon.age = age;
                            dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
                            HoldingPatternPhase phase = dragon.getPhaseManager().create(PhaseType.HOLDING_PATTERN);
                            pathField.set(phase, new Path(Collections.emptyList(), BlockPos.ORIGIN, false));
                            dragon.getRandom().setSeed(sample);
                            phase.serverTick();
                            boolean landing = dragon.getPhaseManager().getCurrent().getType() == PhaseType.LANDING_APPROACH;
                            check(landing == (expected == 0), "Wrong perch decision at age " + age + ", crystals " + crystals);
                            // A successful perch returns immediately: exactly one native draw must have been consumed.
                            if (landing) check(dragon.getRandom().nextLong() == expectedRandom.nextLong(), "Native RNG consumption changed");
                            checks++;
                        }
                    }
                }
            }
            ZsgRooms.LOGGER.info("[DragonPerchRollCheck] PASS: {} real perch decisions at ages 0, 1299 and 1300, with 0 and 10 crystals", checks);
        } finally {
            ((EnderDragonFightAccessor) world.getEnderDragonFight()).zsgRooms$setEndCrystalsAlive(previousCrystals);
            RngStandardization.configure(false);
        }
    }

    private static void prepare(boolean enabled, int priorChecks, int bound, long seed) {
        RngStandardization.configure(enabled);
        for (int i = 0; i < priorChecks; i++)
            RngStandardization.nextDragonPerchRoll(new Random(i), bound, 1300, seed);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new IllegalStateException("[DragonPerchRollCheck] FAIL: " + message);
    }
}
