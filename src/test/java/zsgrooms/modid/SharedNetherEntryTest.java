package zsgrooms.modid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SharedNetherEntryTest {
    @Test
    void referenceUsesOriginalSpawnIncludingHeightAndNegativeCoordinateFlooring() {
        assertEquals(new BlockPos(10, 72, -11),
                SharedNetherEntryState.referenceForSpawn(new BlockPos(80, 72, -81)));
        assertEquals(new BlockPos(-1, 64, -1),
                SharedNetherEntryState.referenceForSpawn(new BlockPos(-1, 64, -1)));
    }

    @Test
    void reinitializationDoesNotFollowRelocatedWorldSpawn() {
        SharedNetherEntryState state = new SharedNetherEntryState();
        BlockPos expected = state.initialize(new BlockPos(80, 72, -81));
        assertEquals(expected, state.initialize(new BlockPos(400, 90, 400)));
    }

    @Test
    void saveReloadKeepsReferenceAndPerPlayerConsumption() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SharedNetherEntryState state = new SharedNetherEntryState();
        BlockPos expected = state.initialize(new BlockPos(80, 72, -81));
        assertFalse(state.hasEntered(first));
        state.complete(first);
        state.complete(first);
        SharedNetherEntryState restored = new SharedNetherEntryState();
        restored.fromTag(state.toTag(new CompoundTag()));
        assertEquals(expected, restored.initialize(BlockPos.ORIGIN));
        assertTrue(restored.hasEntered(first));
        assertFalse(restored.hasEntered(second));
        assertEquals(1, restored.toTag(new CompoundTag()).getList("EnteredPlayers", 8).size());
    }

    @Test
    void freshSameSeedWorldRepeatsReferenceButAllowsFirstEntryAgain() {
        UUID player = UUID.randomUUID();
        BlockPos spawn = new BlockPos(80, 72, -81);
        SharedNetherEntryState previous = new SharedNetherEntryState();
        BlockPos expected = previous.initialize(spawn);
        previous.complete(player);
        SharedNetherEntryState reset = new SharedNetherEntryState();
        assertEquals(expected, reset.initialize(spawn));
        assertFalse(reset.hasEntered(player));
    }

    @Test
    void orientationIgnoresUnrelatedVanillaHistoryAndRngRule() {
        int expected = SharedNetherEntry.orientationRoll(new Random(1), 4, true, 123456L);
        Random unrelated = new Random(2);
        for (int i = 0; i < 100; i++) {
            unrelated.nextLong();
            RngStandardization.configure(i % 2 == 0);
            assertEquals(expected, SharedNetherEntry.orientationRoll(unrelated, 4, true, 123456L));
        }
        RngStandardization.configure(false);
    }

    @Test
    void disabledRollIsVanillaAndEnabledRollStillAdvancesVanillaExactlyOnce() {
        for (boolean enabled : new boolean[]{false, true}) {
            Random actual = new Random(55);
            Random vanilla = new Random(55);
            int expected = vanilla.nextInt(4);
            int result = SharedNetherEntry.orientationRoll(actual, 4, enabled, 123456L);
            if (!enabled) {
                assertEquals(expected, result);
            }
            assertEquals(vanilla.nextLong(), actual.nextLong());
        }
    }
}
