package zsgrooms.model;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;
import profotoce59.properties.VillageGenerator;
import profotoce59.reecriture.VillagePools.VillageStructureLoot;
import profotoce59.reecriture.VillagePools.CommonVillageJigsawBlocks;
import profotoce59.reecriture.VillagePools.PlainsVillageJigsawBlock;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/** Offline calibration export, not invoked by seed search. Input: sample ID, seed, chunk X/Z. */
public final class VillageCalibrationExport {
    public static void main(String[] args) {
        // Diagnostic-only override; never applied by the production finder.
        if (Arrays.asList(args).contains("correct-well")) {
            PlainsVillageJigsawBlock.JIGSAW_BLOCKS.put("common/well_bottom",
                    CommonVillageJigsawBlocks.JIGSAW_BLOCKS.get("common/well_bottom"));
        }
        boolean layoutOnly = Arrays.asList(args).contains("layout");
        System.out.println(layoutOnly ? "id\tindex\ttemplate\tx\ty\tz\trotation"
                : "id\tbiome\tkind\tx\ty\tz\ttemplate\tmodeled\tiron\tpickaxes\tdiamonds");
        try (Scanner input = new Scanner(System.in)) {
            while (input.hasNextInt()) {
                int id = input.nextInt();
                long seed = input.nextLong();
                int cx = input.nextInt(), cz = input.nextInt();
                OverworldTerrainGenerator terrain = new OverworldTerrainGenerator(new OverworldBiomeSource(MCVersion.v1_16_1, seed));
                VillageGenerator village = new VillageGenerator(MCVersion.v1_16_1);
                if (!village.generate(terrain, cx, cz, new ChunkRand(), false)) throw new IllegalStateException("Layout rejected: " + id);
                if (layoutOnly) {
                    int index = 0;
                    for (VillageGenerator.Piece piece : village.getPieces()) {
                        System.out.printf("%d\t%d\t%s\t%d\t%d\t%d\t%s%n", id, index++, piece.getName(),
                                piece.pos.getX(), piece.pos.getY(), piece.pos.getZ(), piece.rotation.name());
                    }
                    continue;
                }
                Map<BPos, String> smiths = new LinkedHashMap<>();
                String biome = "unknown";
                for (VillageGenerator.Piece piece : village.getPieces()) {
                    String name = piece.getName();
                    if (biome.equals("unknown") && !name.startsWith("common/")) biome = name.split("/")[0];
                    if (!name.contains("weaponsmith") && !name.contains("tool_smith")
                            && !name.contains("toolsmith") && !name.contains("armorer")) continue;
                    List<BPos> offsets = VillageStructureLoot.STRUCTURE_LOOT_OFFSETS.get(name);
                    if (offsets != null) for (BPos offset : offsets) {
                        smiths.put(piece.pos.add(VillageGenerator.Piece.getTransformedPos(offset, piece.rotation)), name);
                    }
                }
                List<Pair<BPos, List<ItemStack>>> loot = village.generateLoot(terrain, new ChunkRand());
                SmithLootModel.Result total = SmithLootModel.summarize(smiths.keySet(), loot, false);
                System.out.printf("%d\t%s\tsummary\t%d\t0\t%d\t-\t%d\t%d\t%d\t%d%n",
                        id, biome, cx, cz, total.modeledChests, total.iron, total.ironPickaxes, total.diamonds);
                for (Map.Entry<BPos, String> chest : smiths.entrySet()) {
                    SmithLootModel.Result result = SmithLootModel.summarize(Collections.singleton(chest.getKey()), loot, false);
                    BPos pos = chest.getKey();
                    System.out.printf("%d\t%s\tchest\t%d\t%d\t%d\t%s\t%d\t%d\t%d\t%d%n", id, biome,
                            pos.getX(), pos.getY(), pos.getZ(), chest.getValue(), result.modeledChests,
                            result.iron, result.ironPickaxes, result.diamonds);
                }
            }
        }
    }
}
