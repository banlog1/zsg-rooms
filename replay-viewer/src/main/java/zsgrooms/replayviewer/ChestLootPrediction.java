// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootGsons;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import zsgrooms.replayviewer.mixin.PredictionLootContextAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class ChestLootPrediction {
    private static final Map<String, LootTable> TABLES = new HashMap<>();
    private ChestLootPrediction() { }

    static List<ItemStack> predict(RaceRecording recording, ChestLootHistory history, ClientWorld world,
                                   BlockPos pos, int worldIndex, int time) {
        if (recording == null || recording.predictionSeed == null || !TempleLootPrediction.outsideActiveRace()) return null;
        // Older temple-only recordings retain their coordinate-model preview. New recordings never
        // fall back after an identity has been invalidated, or invent metadata for unseen chests.
        if (!recording.lootMetadata) return TempleLootPrediction.predict(recording, world, pos);
        if (history == null || !world.isChunkLoaded(pos)) return null;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        BlockPos first = pos, second = pos;
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type != ChestType.SINGLE) {
            BlockPos other = pos.offset(ChestBlock.getFacing(state));
            if (!world.isChunkLoaded(other)) return null;
            BlockState partner = world.getBlockState(other);
            if (partner.getBlock() != state.getBlock() || partner.get(ChestBlock.FACING) != state.get(ChestBlock.FACING)
                    || partner.get(ChestBlock.CHEST_TYPE) != type.getOpposite()) return null;
            if (type == ChestType.RIGHT) second = other; else first = other;
        }
        try {
            List<ItemStack> items = half(recording, history, world, first, worldIndex, time);
            if (items == null || first.equals(second)) return items;
            List<ItemStack> rest = half(recording, history, world, second, worldIndex, time);
            if (rest == null) return null;
            items.addAll(rest);
            return items;
        } catch (IOException | RuntimeException error) { return null; }
    }

    private static List<ItemStack> half(RaceRecording recording, ChestLootHistory history, ClientWorld world, BlockPos pos,
                                        int worldIndex, int time) throws IOException {
        ChestLoot loot = history.at(worldIndex, world.getRegistryKey().getValue().toString(), pos.asLong(), time);
        if (loot == null || loot.state != Block.getRawIdFromState(world.getBlockState(pos))) return null;
        return generate(loot.table, loot.seed, recording.stewOrder);
    }

    static List<ItemStack> generate(String id, long seed, List<String> stewOrder) throws IOException {
        if (seed == 0 || !id.matches("minecraft:chests/(village/)?[a-z_]+")) throw new IOException("Unsupported loot table");
        boolean stew = id.equals("minecraft:chests/shipwreck_supply");
        if (stew && stewOrder.size() != 6) throw new IOException("Stew effect order unavailable");
        String cacheKey = stew ? id + stewOrder : id;
        LootTable table = TABLES.get(cacheKey);
        if (table == null) {
            try (InputStream input = LootTable.class.getResourceAsStream("/data/minecraft/loot_tables/" + id.substring(10) + ".json")) {
                if (input == null) throw new IOException("Vanilla loot table unavailable");
                table = gson(stewOrder).fromJson(
                        new InputStreamReader(input, StandardCharsets.UTF_8), LootTable.class);
                TABLES.put(cacheKey, table);
            }
        }
        // Vanilla 1.16.1 chest tables only need RNG/registries, except exploration maps. Omitting
        // POSITION makes that function leave an unresolved map without a world lookup or RNG draw.
        LootContext context = PredictionLootContextAccessor.zsgViewer$create(new Random(seed), 0, null,
                key -> { throw new IllegalStateException("Unexpected referenced loot table"); },
                key -> { throw new IllegalStateException("Unexpected loot predicate"); },
                Collections.emptyMap(), Collections.emptyMap());
        SimpleInventory inventory = new SimpleInventory(27);
        table.supplyInventory(inventory, context);
        List<ItemStack> items = new ArrayList<>(27);
        boolean maps = id.equals("minecraft:chests/shipwreck_map") || id.startsWith("minecraft:chests/underwater_ruin_");
        for (int i = 0; i < 27; i++) {
            ItemStack item = inventory.getStack(i);
            if (maps && item.getItem() == Items.MAP) item.setCustomName(new LiteralText("Treasure map (destination not predicted)"));
            items.add(item);
        }
        return items;
    }

    private static com.google.gson.Gson gson(List<String> order) {
        com.google.gson.Gson vanilla = LootGsons.getTableGsonBuilder().create();
        return LootGsons.getTableGsonBuilder().registerTypeHierarchyAdapter(net.minecraft.loot.function.LootFunction.class,
                (com.google.gson.JsonDeserializer<net.minecraft.loot.function.LootFunction>) (json, type, context) -> {
                    com.google.gson.JsonObject function = json.getAsJsonObject();
                    if (!"minecraft:set_stew_effect".equals(function.get("function").getAsString()))
                        return vanilla.fromJson(json, net.minecraft.loot.function.LootFunction.class);
                    Map<String, net.minecraft.loot.UniformLootTableRange> ranges = new HashMap<>();
                    for (com.google.gson.JsonElement element : function.getAsJsonArray("effects")) {
                        com.google.gson.JsonObject effect = element.getAsJsonObject();
                        ranges.put(effect.get("type").getAsString(), vanilla.fromJson(effect.get("duration"), net.minecraft.loot.UniformLootTableRange.class));
                    }
                    Map<net.minecraft.entity.effect.StatusEffect, net.minecraft.loot.UniformLootTableRange> effects = new java.util.LinkedHashMap<>();
                    for (String name : order) {
                        if (!ranges.containsKey(name)) throw new com.google.gson.JsonParseException("Unexpected stew effect");
                        effects.put(net.minecraft.util.registry.Registry.STATUS_EFFECT.get(new net.minecraft.util.Identifier(name)), ranges.get(name));
                    }
                    if (effects.size() != ranges.size()) throw new com.google.gson.JsonParseException("Incomplete stew effect order");
                    net.minecraft.loot.condition.LootCondition[] conditions = function.has("conditions")
                            ? vanilla.fromJson(function.get("conditions"), net.minecraft.loot.condition.LootCondition[].class)
                            : new net.minecraft.loot.condition.LootCondition[0];
                    return zsgrooms.replayviewer.mixin.PredictionStewAccessor.zsgViewer$create(conditions, effects);
                }).create();
    }
}
