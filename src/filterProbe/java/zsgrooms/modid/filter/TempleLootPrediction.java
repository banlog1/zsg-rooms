package zsgrooms.modid.filter;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.feature.StructureFeature;

/** Development-only prediction; generated chest contents are still the validation authority. */
final class TempleLootPrediction {
    static TempleLootProbe predict(ServerWorld contextWorld, long seed, ChunkPos chunk) {
        StructureFeature<?> temple = StructureFeature.DESERT_PYRAMID;
        int index = 0;
        for (StructureFeature<?> feature : Registry.STRUCTURE_FEATURE) {
            if (feature == temple) break;
            if (feature.method_28663() == temple.method_28663()) index++;
        }
        ChunkRandom random = new ChunkRandom();
        long populationSeed = random.setPopulationSeed(seed, chunk.x << 4, chunk.z << 4);
        random.setDecoratorSeed(populationSeed, index, temple.method_28663().ordinal());
        TempleLootProbe result = new TempleLootProbe();
        // All four basement chests lie in the temple's starting chunk. Vanilla assigns four consecutive longs.
        for (int chest = 0; chest < 4; chest++) {
            long lootSeed = random.nextLong();
            if (lootSeed == 0) continue;
            LootContext context = new LootContext.Builder(contextWorld).random(lootSeed)
                    .parameter(LootContextParameters.POSITION, new BlockPos((chunk.x << 4) + 10, 53, (chunk.z << 4) + 10))
                    .build(LootContextTypes.CHEST);
            result.chests++;
            for (ItemStack stack : contextWorld.getServer().getLootManager().getTable(LootTables.DESERT_PYRAMID_CHEST).generateLoot(context)) {
                if (stack.getItem() == Items.IRON_INGOT) result.iron += stack.getCount();
                else if (stack.getItem() == Items.DIAMOND) result.diamonds += stack.getCount();
            }
        }
        return result;
    }

    static void verify(ServerWorld world, ChunkPos chunk, TempleLootProbe actual) {
        TempleLootProbe predicted = predict(world, world.getSeed(), chunk);
        if (actual.chests != predicted.chests || actual.iron != predicted.iron || actual.diamonds != predicted.diamonds) {
            throw new IllegalStateException("Temple loot prediction differs from generated chest totals");
        }
    }
}
