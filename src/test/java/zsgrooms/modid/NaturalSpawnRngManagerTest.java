package zsgrooms.modid;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zsgrooms.modid.rng.NaturalSpawnCycle;
import zsgrooms.modid.rng.NaturalSpawnRngManager;
import zsgrooms.modid.rng.NaturalSpawnScope;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalSpawnRngManagerTest {
    private static final long WORLD_SEED = 918273645L;
    private static final String NETHER = "minecraft:the_nether";
    private static final int MONSTER = 0;

    @AfterEach
    public void disableStandardization() {
        RngStandardization.configure(false, false);
    }

    @Test
    public void sameSectionAndCycleProduceSameStreams() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle first = cycle(new NaturalSpawnRngManager(WORLD_SEED), 4, -7);
        NaturalSpawnCycle repeated = cycle(new NaturalSpawnRngManager(WORLD_SEED), 4, -7);

        assertEquals(first.getCycleIndex(), repeated.getCycleIndex());
        assertEquals(first.getPositionRandom().nextLong(), repeated.getPositionRandom().nextLong());
        assertEquals(
                first.getAttemptCountRandom().nextLong(),
                repeated.getAttemptCountRandom().nextLong());
        assertEquals(first.getOffsetRandom().nextLong(), repeated.getOffsetRandom().nextLong());
        assertEquals(first.getSelectionRandom().nextLong(), repeated.getSelectionRandom().nextLong());
        assertEquals(first.getSpawnCheckRandom().nextLong(), repeated.getSpawnCheckRandom().nextLong());
    }

    @Test
    public void configureResetsSpawnCycleSequence() {
        RngStandardization.configure(true, false);
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(WORLD_SEED);
        long first = cycle(manager, 4, -7).getSelectionRandom().nextLong();
        cycle(manager, 4, -7);

        RngStandardization.configure(true, false);

        NaturalSpawnCycle reset = cycle(manager, 4, -7);
        assertEquals(0L, reset.getCycleIndex());
        assertEquals(first, reset.getSelectionRandom().nextLong());
    }

    @Test
    public void advancingOneChunkDoesNotAffectAnotherChunk() {
        RngStandardization.configure(true, false);
        NaturalSpawnRngManager contaminated = new NaturalSpawnRngManager(WORLD_SEED);
        cycle(contaminated, 1, 1);
        cycle(contaminated, 1, 1);
        long actual = cycle(contaminated, 8, 3).getOffsetRandom().nextLong();

        NaturalSpawnRngManager clean = new NaturalSpawnRngManager(WORLD_SEED);
        long expected = cycle(clean, 8, 3).getOffsetRandom().nextLong();

        assertEquals(expected, actual);
    }

    @Test
    public void otherStandardizedChannelsDoNotAffectSpawnCycles() {
        RngStandardization.configure(true, false);
        NaturalSpawnRngManager expectedManager = new NaturalSpawnRngManager(WORLD_SEED);
        cycle(expectedManager, 2, 5);
        long expected = cycle(expectedManager, 2, 5).getSelectionRandom().nextLong();

        NaturalSpawnRngManager actualManager = new NaturalSpawnRngManager(WORLD_SEED);
        cycle(actualManager, 2, 5);
        RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:the_nether|minecraft:blaze");
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        long actual = cycle(actualManager, 2, 5).getSelectionRandom().nextLong();

        assertEquals(expected, actual);
    }

    @Test
    public void consumingPositionStreamDoesNotAffectSelectionStream() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle consumed = cycle(new NaturalSpawnRngManager(WORLD_SEED), 9, -2);
        consumed.getPositionRandom().nextLong();
        consumed.getPositionRandom().nextInt(16);
        consumed.getOffsetRandom().nextInt(6);
        long actual = consumed.getSelectionRandom().nextLong();

        NaturalSpawnCycle clean = cycle(new NaturalSpawnRngManager(WORLD_SEED), 9, -2);
        long expected = clean.getSelectionRandom().nextLong();

        assertEquals(expected, actual);
    }

    @Test
    public void differentCycleIndexesUseDifferentSeeds() {
        RngStandardization.configure(true, false);
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(WORLD_SEED);
        NaturalSpawnCycle first = cycle(manager, 4, -7);
        NaturalSpawnCycle second = cycle(manager, 4, -7);

        assertEquals(0L, first.getCycleIndex());
        assertEquals(1L, second.getCycleIndex());
        assertNotEquals(
                first.getSelectionRandom().nextLong(),
                second.getSelectionRandom().nextLong());
    }

    @Test
    public void consumingOnePackDoesNotShiftTheNextPack() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle consumed = cycle(
                new NaturalSpawnRngManager(WORLD_SEED), 5, -8);
        consumed.getAttemptCountRandom().nextFloat();
        consumed.nextOffsetInt(6);
        consumed.nextOffsetInt(6);
        consumed.getSelectionRandom().nextInt(20);
        consumed.getSpawnCheckRandom().nextFloat();
        consumed.beginPack();

        NaturalSpawnCycle clean = cycle(
                new NaturalSpawnRngManager(WORLD_SEED), 5, -8);
        clean.beginPack();

        assertEquals(1, consumed.getPackIndex());
        assertEquals(clean.getAttemptCountRandom().nextLong(),
                consumed.getAttemptCountRandom().nextLong());
        assertEquals(clean.getOffsetRandom().nextLong(),
                consumed.getOffsetRandom().nextLong());
        assertEquals(clean.getSelectionRandom().nextLong(),
                consumed.getSelectionRandom().nextLong());
        assertEquals(clean.getSpawnCheckRandom().nextLong(),
                consumed.getSpawnCheckRandom().nextLong());
    }

    @Test
    public void offsetRollsTrackTheVanillaAttemptIndex() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle spawnCycle = cycle(
                new NaturalSpawnRngManager(WORLD_SEED), 6, -4);

        assertEquals(-1, spawnCycle.getAttemptIndex());
        spawnCycle.setCurrentAttemptFortress(true);
        for (int roll = 0; roll < 4; roll++) {
            spawnCycle.nextOffsetInt(6);
        }
        assertFalse(spawnCycle.isCurrentAttemptFortress());
        assertEquals(0, spawnCycle.getAttemptIndex());
        spawnCycle.setCurrentAttemptFortress(true);
        for (int roll = 0; roll < 4; roll++) {
            spawnCycle.nextOffsetInt(6);
        }
        assertFalse(spawnCycle.isCurrentAttemptFortress());
        assertEquals(1, spawnCycle.getAttemptIndex());
    }

    @Test
    public void dimensionAndSpawnGroupArePartOfSectionSeed() {
        RngStandardization.configure(true, false);
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(WORLD_SEED);
        long netherMonster = manager.nextCycle(
                        new Identifier(NETHER), SpawnGroup.MONSTER, new ChunkPos(3, 3))
                .getPositionRandom().nextLong();
        long overworldMonster = manager.nextCycle(
                        new Identifier("minecraft:overworld"),
                        SpawnGroup.MONSTER,
                        new ChunkPos(3, 3))
                .getPositionRandom().nextLong();
        long netherCreature = manager.nextCycle(
                        new Identifier(NETHER), SpawnGroup.CREATURE, new ChunkPos(3, 3))
                .getPositionRandom().nextLong();

        assertNotEquals(netherMonster, overworldMonster);
        assertNotEquals(netherMonster, netherCreature);
    }

    @Test
    public void skippedRestrictionChecksDoNotShiftLaterAttempts() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle checked = cycle(new NaturalSpawnRngManager(WORLD_SEED), 6, -4);
        NaturalSpawnCycle skipped = cycle(new NaturalSpawnRngManager(WORLD_SEED), 6, -4);

        for (int attempt = 0; attempt < 12; attempt++) {
            beginAttempt(checked);
            beginAttempt(skipped);
            Random checks = checked.getSpawnCheckRandom();
            if (attempt % 3 == 2) {
                assertEquals(checks.nextInt(32), skipped.getSpawnCheckRandom().nextInt(32));
                assertEquals(checks.nextInt(8), skipped.getSpawnCheckRandom().nextInt(8));
            } else {
                checks.nextInt(32);
                checks.nextInt(8);
            }
        }
    }

    @Test
    public void extraRestrictionRollsDoNotShiftLaterAttemptsOrOtherStreams() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle extra = cycle(new NaturalSpawnRngManager(WORLD_SEED), 6, -4);
        NaturalSpawnCycle normal = cycle(new NaturalSpawnRngManager(WORLD_SEED), 6, -4);
        beginAttempt(extra);
        beginAttempt(normal);
        normal.getSpawnCheckRandom().nextInt(32);
        for (int roll = 0; roll < 17; roll++) {
            extra.getSpawnCheckRandom().nextInt(32);
        }

        beginAttempt(extra);
        beginAttempt(normal);
        assertEquals(normal.getSpawnCheckRandom().nextLong(), extra.getSpawnCheckRandom().nextLong());
        assertEquals(normal.getOffsetRandom().nextLong(), extra.getOffsetRandom().nextLong());
        assertEquals(normal.getSelectionRandom().nextLong(), extra.getSelectionRandom().nextLong());
        assertEquals(normal.getAttemptCountRandom().nextLong(), extra.getAttemptCountRandom().nextLong());
        assertEquals(normal.getPositionRandom().nextLong(), extra.getPositionRandom().nextLong());
    }

    @Test
    public void repeatedAccessWithinAnAttemptContinuesTheRestrictionStream() {
        RngStandardization.configure(true, false);
        NaturalSpawnCycle repeated = cycle(new NaturalSpawnRngManager(WORLD_SEED), 6, -4);
        NaturalSpawnCycle retained = cycle(new NaturalSpawnRngManager(WORLD_SEED), 6, -4);
        beginAttempt(repeated);
        beginAttempt(retained);
        Random checks = retained.getSpawnCheckRandom();

        for (int roll = 0; roll < 10; roll++) {
            assertEquals(checks.nextInt(32), repeated.getSpawnCheckRandom().nextInt(32));
        }
    }

    @Test
    public void restrictionSequencesAreDistinctPerAttemptAndResetOnConfigure() {
        RngStandardization.configure(true, false);
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(WORLD_SEED);
        NaturalSpawnCycle original = cycle(manager, 6, -4);
        beginAttempt(original);
        long first = original.getSpawnCheckRandom().nextLong();
        beginAttempt(original);
        long second = original.getSpawnCheckRandom().nextLong();
        assertNotEquals(first, second);

        RngStandardization.configure(true, false);
        NaturalSpawnCycle reset = cycle(manager, 6, -4);
        beginAttempt(reset);
        assertEquals(first, reset.getSpawnCheckRandom().nextLong());
        beginAttempt(reset);
        assertEquals(second, reset.getSpawnCheckRandom().nextLong());

        reset.beginPack();
        beginAttempt(reset);
        assertNotEquals(first, reset.getSpawnCheckRandom().nextLong());
    }

    private void beginAttempt(NaturalSpawnCycle cycle) {
        for (int roll = 0; roll < 4; roll++) {
            cycle.nextOffsetInt(6);
        }
    }

    @Test
    public void scopeCoversEveryNetherMonsterChunkOnlyWhenEnabled() {
        assertTrue(NaturalSpawnScope.applies(
                true, World.NETHER, SpawnGroup.MONSTER));
        assertFalse(NaturalSpawnScope.applies(
                false, World.NETHER, SpawnGroup.MONSTER));
        assertFalse(NaturalSpawnScope.applies(
                true, World.OVERWORLD, SpawnGroup.MONSTER));
        assertFalse(NaturalSpawnScope.applies(
                true, World.END, SpawnGroup.MONSTER));
        assertFalse(NaturalSpawnScope.applies(
                true, World.NETHER, SpawnGroup.CREATURE));
    }

    private NaturalSpawnCycle cycle(
            NaturalSpawnRngManager manager,
            int chunkX,
            int chunkZ
    ) {
        NaturalSpawnCycle cycle = manager.nextCycle(
                new Identifier(NETHER),
                SpawnGroup.MONSTER,
                new ChunkPos(chunkX, chunkZ));
        cycle.beginPack();
        return cycle;
    }
}
