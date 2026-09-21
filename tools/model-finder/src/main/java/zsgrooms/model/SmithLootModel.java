package zsgrooms.model;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;
import profotoce59.properties.VillageGenerator;
import profotoce59.reecriture.VillagePools.VillageStructureLoot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SmithLootModel {
    private SmithLootModel() { }

    enum Outcome {
        LAYOUT_REJECTED, NO_SMITH_CHESTS, NO_MODELED_SMITH_LOOT,
        PARTIAL_LOOT_INSUFFICIENT, INSUFFICIENT_IRON, ACCEPTED
    }

    static final class Result {
        final int iron;
        final int ironPickaxes;
        final int diamonds;
        final int smithChests;
        final int modeledChests;
        final boolean modeledGolem;
        final Outcome outcome;
        long layoutNanos;
        long lootNanos;

        Result(int iron, int ironPickaxes, int diamonds, int smithChests, int modeledChests, boolean modeledGolem, Outcome outcome) {
            this.iron = iron;
            this.ironPickaxes = ironPickaxes;
            this.diamonds = diamonds;
            this.smithChests = smithChests;
            this.modeledChests = modeledChests;
            this.modeledGolem = modeledGolem;
            this.outcome = outcome;
        }
    }

    static int iron(long seed, int chunkX, int chunkZ) {
        return evaluate(seed, chunkX, chunkZ).iron;
    }

    static Result evaluate(long seed, int chunkX, int chunkZ) {
        long start = System.nanoTime();
        OverworldTerrainGenerator terrain = new OverworldTerrainGenerator(
                new OverworldBiomeSource(MCVersion.v1_16_1, seed));
        VillageGenerator village = new VillageGenerator(MCVersion.v1_16_1);
        if (!village.generate(terrain, chunkX, chunkZ, new ChunkRand(), false)) {
            Result result = new Result(0, 0, 0, 0, 0, false, Outcome.LAYOUT_REJECTED);
            result.layoutNanos = System.nanoTime() - start;
            return result;
        }
        Set<BPos> smithChests = new HashSet<>();
        boolean golem = false;
        for (VillageGenerator.Piece piece : village.getPieces()) {
            String name = piece.getName();
            if (name.equals("common/iron_golem")) golem = true;
            if (!name.contains("weaponsmith") && !name.contains("tool_smith")
                    && !name.contains("toolsmith") && !name.contains("armorer")) continue;
            List<BPos> offsets = VillageStructureLoot.STRUCTURE_LOOT_OFFSETS.get(name);
            if (offsets == null) continue;
            for (BPos offset : offsets) {
                smithChests.add(piece.pos.add(VillageGenerator.Piece.getTransformedPos(offset, piece.rotation)));
            }
        }
        long layoutNanos = System.nanoTime() - start;
        start = System.nanoTime();
        Result result = summarize(smithChests, smithChests.isEmpty() ? java.util.Collections.emptyList()
                : village.generateLoot(terrain, new ChunkRand()), golem);
        result.layoutNanos = layoutNanos;
        result.lootNanos = System.nanoTime() - start;
        return result;
    }

    static Result summarize(Set<BPos> smithChests, List<Pair<BPos, List<ItemStack>>> loot, boolean golem) {
        int nuggets = 0;
        int ironPickaxes = 0;
        int diamonds = 0;
        Set<BPos> modeledChests = new HashSet<>();
        // Upstream omits loot it cannot confidently model. Omitted loot never earns credit.
        for (Pair<BPos, List<ItemStack>> chest : loot) {
            if (!smithChests.contains(chest.getFirst()) || !modeledChests.add(chest.getFirst())) continue;
            for (ItemStack stack : chest.getSecond()) {
                if (stack.getItem().equals(Items.IRON_INGOT)) nuggets += stack.getCount() * 9;
                else if (stack.getItem().equals(Items.IRON_NUGGET)) nuggets += stack.getCount();
                else if (stack.getItem().equals(Items.IRON_BLOCK)) nuggets += stack.getCount() * 81;
                else if (stack.getItem().equals(Items.IRON_PICKAXE)) ironPickaxes += stack.getCount();
                else if (stack.getItem().equals(Items.DIAMOND)) diamonds += stack.getCount();
            }
        }
        int iron = nuggets / 9;
        // Only one pickaxe is needed. Its credit cannot pay for a bucket or ignition.
        boolean sufficient = iron >= 4 || (iron >= 1 && (ironPickaxes > 0 || diamonds >= 3));
        Outcome outcome = smithChests.isEmpty() ? Outcome.NO_SMITH_CHESTS
                : sufficient ? Outcome.ACCEPTED
                : modeledChests.isEmpty() ? Outcome.NO_MODELED_SMITH_LOOT
                : modeledChests.size() < smithChests.size() ? Outcome.PARTIAL_LOOT_INSUFFICIENT
                : Outcome.INSUFFICIENT_IRON;
        return new Result(iron, ironPickaxes, diamonds, smithChests.size(), modeledChests.size(), golem, outcome);
    }
}
