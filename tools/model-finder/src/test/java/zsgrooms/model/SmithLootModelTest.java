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

    @Test
    void pickaxeCreditPreservesActualIronAndOnlyReplacesOnePickaxe() {
        for (int iron = 0; iron <= 4; iron++) {
            for (int picks = 0; picks <= 2; picks++) {
                for (int diamonds = 0; diamonds <= 6; diamonds++) {
                    var loot = List.of(new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_INGOT, iron),
                            new ItemStack(Items.IRON_PICKAXE, picks), new ItemStack(Items.DIAMOND, diamonds))));
                    var result = SmithLootModel.summarize(Set.of(FIRST), loot, false);
                    boolean expected = iron >= 4 || (iron >= 1 && (picks >= 1 || diamonds >= 3));
                    assertEquals(expected, result.outcome == SmithLootModel.Outcome.ACCEPTED,
                            "iron=" + iron + ", picks=" + picks + ", diamonds=" + diamonds);
                    assertEquals(iron, result.iron);
                    assertEquals(picks, result.ironPickaxes);
                    assertEquals(diamonds, result.diamonds);
                }
            }
        }
    }

    @Test
    void diamondsCanCombineAcrossSmithChestsButDuplicatesDoNotCount() {
        var first = new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.DIAMOND, 2)));
        var second = new Pair<>(SECOND, List.of(new ItemStack(Items.DIAMOND, 1)));
        assertEquals(SmithLootModel.Outcome.ACCEPTED,
                SmithLootModel.summarize(Set.of(FIRST, SECOND), List.of(first, second), false).outcome);
        var duplicate = SmithLootModel.summarize(Set.of(FIRST), List.of(first, first), false);
        assertEquals(2, duplicate.diamonds);
        assertEquals(SmithLootModel.Outcome.INSUFFICIENT_IRON, duplicate.outcome);
    }

    @Test
    void pickaxesAndDiamondsFromOtherChestsDoNotEarnCredit() {
        var smith = new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.IRON_AXE, 1)));
        var other = new Pair<>(SECOND, List.of(new ItemStack(Items.IRON_PICKAXE, 1), new ItemStack(Items.DIAMOND, 3)));
        var result = SmithLootModel.summarize(Set.of(FIRST), List.of(smith, other), true);
        assertEquals(0, result.ironPickaxes);
        assertEquals(0, result.diamonds);
        assertEquals(SmithLootModel.Outcome.INSUFFICIENT_IRON, result.outcome);
    }

    @Test
    void pickaxeStillNeedsOneWholeIngotAndUnknownLootGetsNoCredit() {
        for (int nuggets : new int[]{8, 9}) {
            var chest = new Pair<>(FIRST, List.of(new ItemStack(Items.IRON_NUGGET, nuggets), new ItemStack(Items.IRON_PICKAXE, 1)));
            var result = SmithLootModel.summarize(Set.of(FIRST, SECOND), List.of(chest), false);
            assertEquals(nuggets == 9 ? SmithLootModel.Outcome.ACCEPTED : SmithLootModel.Outcome.PARTIAL_LOOT_INSUFFICIENT,
                    result.outcome);
        }
    }
}
