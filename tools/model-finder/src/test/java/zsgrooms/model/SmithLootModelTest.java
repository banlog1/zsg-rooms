package zsgrooms.model;

import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SmithLootModelTest {
    private static final BPos FIRST = new BPos(0, 64, 0);
    private static final BPos SECOND = new BPos(16, 64, 0);

    @Test
    void golemPresenceDoesNotAffectAcceptance() {
        var loot = List.of(new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_INGOT, 4))));
        var without = SmithLootModel.summarize(Set.of(FIRST), loot, false);
        var with = SmithLootModel.summarize(Set.of(FIRST), loot, true);
        assertEquals(SmithLootModel.Outcome.ACCEPTED, without.outcome);
        assertEquals(with.outcome, without.outcome);
        assertEquals(with.iron, without.iron);
        assertFalse(without.modeledGolem);
    }

    @Test
    void noSmithAndOmittedLootHaveDifferentOutcomes() {
        assertEquals(SmithLootModel.Outcome.NO_SMITH_CHESTS,
                SmithLootModel.summarize(Set.of(), List.of(), false).outcome);
        assertEquals(SmithLootModel.Outcome.NO_MODELED_SMITH_LOOT,
                SmithLootModel.summarize(Set.of(FIRST), List.of(), true).outcome);
    }

    @Test
    void incompletePredictionsAreNotCalledConfirmedInsufficientIron() {
        var loot = List.of(new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_INGOT, 3))));
        assertEquals(SmithLootModel.Outcome.PARTIAL_LOOT_INSUFFICIENT,
                SmithLootModel.summarize(Set.of(FIRST, SECOND), loot, false).outcome);
        assertEquals(SmithLootModel.Outcome.INSUFFICIENT_IRON,
                SmithLootModel.summarize(Set.of(FIRST), loot, false).outcome);
    }

    @Test
    void onlySmithLootCountsAndDuplicateChestsDoNotCountTwice() {
        var chest = new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_INGOT, 2)));
        var nonSmith = new Pair<>(SECOND, List.of(new ItemStack(Items.IRON_BLOCK, 2)));
        var result = SmithLootModel.summarize(Set.of(FIRST), List.of(chest, chest, nonSmith), false);
        assertEquals(2, result.iron);
        assertEquals(1, result.modeledChests);
        assertEquals(SmithLootModel.Outcome.INSUFFICIENT_IRON, result.outcome);
    }

    @Test
    void nuggetsCountAtTheFourIngotBoundary() {
        var result = SmithLootModel.summarize(Set.of(FIRST), List.of(new Pair<>(FIRST,
                List.of(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.IRON_NUGGET, 9)))), false);
        assertEquals(4, result.iron);
        assertEquals(SmithLootModel.Outcome.ACCEPTED, result.outcome);
    }
}
