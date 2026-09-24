// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootGsons;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zsgrooms.replayviewer.mixin.PredictionLootContextAccessor;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class TempleLootPrediction {
    private static LootTable table;
    private TempleLootPrediction() { }

    static List<ItemStack> predict(RaceRecording recording, ClientWorld world, BlockPos pos) {
        if (recording == null || recording.predictionSeed == null || !outsideActiveRace()
                || !World.OVERWORLD.equals(world.getRegistryKey())) return null;
        int index = TempleLootModel.chestIndex(recording.predictionSeed, pos.getX(), pos.getY(), pos.getZ());
        if (index < 0 || !world.getBlockState(pos).isOf(Blocks.CHEST)
                || world.getBlockState(pos).get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return null;
        // Require surviving temple geometry, not merely a chest at a potential structure coordinate.
        BlockPos center = new BlockPos((pos.getX() & ~15) + 10, 64, (pos.getZ() & ~15) + 10);
        if (!world.isChunkLoaded(center) || !world.getBlockState(center.add(-2, -10, -2)).isOf(Blocks.CHISELED_SANDSTONE)
                || !world.getBlockState(center.add(2, -10, 2)).isOf(Blocks.CHISELED_SANDSTONE)
                || !world.getBlockState(pos.down()).isOf(Blocks.CUT_SANDSTONE)
                || !world.getBlockState(pos.up(2)).isOf(Blocks.CUT_SANDSTONE)) return null;
        long seed = TempleLootModel.lootSeed(recording.predictionSeed, pos.getX() >> 4, pos.getZ() >> 4, index);
        if (seed == 0) return null; // Vanilla's zero loot seed uses a nondeterministic context.
        try { return generate(seed); }
        catch (IOException | RuntimeException error) { return null; }
    }

    static List<ItemStack> generate(long seed) throws IOException {
        if (table == null) {
            try (InputStream input = LootTable.class.getResourceAsStream("/data/minecraft/loot_tables/chests/desert_pyramid.json")) {
                if (input == null) throw new IOException("Vanilla temple loot table unavailable");
                table = LootGsons.getTableGsonBuilder().create().fromJson(
                        new InputStreamReader(input, StandardCharsets.UTF_8), LootTable.class);
            }
        }
        // This fixed vanilla table uses only RNG and registries, never world state or referenced tables.
        LootContext context = PredictionLootContextAccessor.zsgViewer$create(new Random(seed), 0, null,
                id -> { throw new IllegalStateException("Unexpected referenced loot table"); },
                id -> { throw new IllegalStateException("Unexpected loot predicate"); },
                Collections.emptyMap(), Collections.emptyMap());
        SimpleInventory inventory = new SimpleInventory(27);
        table.supplyInventory(inventory, context);
        List<ItemStack> items = new ArrayList<>(27);
        for (int i = 0; i < 27; i++) items.add(inventory.getStack(i));
        return items;
    }

    static boolean outsideActiveRace() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("zsg-rooms")) return true;
        try {
            Class<?> rooms = Class.forName("zsgrooms.modid.ZsgRooms");
            String name = (String) rooms.getMethod("getActiveRoomName").invoke(null);
            Object game = name == null ? null : rooms.getMethod("getGame", String.class).invoke(null, name);
            if (game == null || !((Boolean) game.getClass().getMethod("getIsInGame").invoke(game))) return true;
            return (Boolean) Class.forName("zsgrooms.modid.replay.ReplayPrototype").getMethod("hasReturnedToRoom").invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) { return false; }
    }
}
