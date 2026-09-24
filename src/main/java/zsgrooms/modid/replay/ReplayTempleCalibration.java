package zsgrooms.modid.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.DesertTempleGenerator;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.ChunkRandom;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Explicit development fixture only: generates a temple and exports a real-server loot oracle. */
final class ReplayTempleCalibration {
    static void prepare(ServerPlayerEntity player, Path output) throws Exception {
        ServerWorld world = player.getServerWorld();
        long seed = world.getSeed();
        Random placement = new Random(seed + 14357617);
        int cx = placement.nextInt(24), cz = placement.nextInt(24);
        world.getChunk(cx, cz);
        BlockPos center = new BlockPos(cx * 16 + 10, 53, cz * 16 + 10);
        for (BlockPos pos : new BlockPos[]{center.north(2), center.east(2), center.south(2), center.west(2)}) {
            world.removeBlockEntity(pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        ChunkRandom carver = new ChunkRandom(); carver.setCarverSeed(seed, cx, cz);
        DesertTempleGenerator temple = new DesertTempleGenerator(carver, cx * 16, cz * 16);
        ChunkRandom decorator = new ChunkRandom();
        long population = decorator.setPopulationSeed(seed, cx * 16, cz * 16);
        decorator.setDecoratorSeed(population, 3, 4);
        temple.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), decorator,
                new BlockBox(cx * 16, 0, cz * 16, cx * 16 + 15, 255, cz * 16 + 15), new ChunkPos(cx, cz), center);
        world.setBlockState(center, Blocks.AIR.getDefaultState()); // Remove the trap for the fixture.
        JsonObject root = new JsonObject(); root.addProperty("seed", seed);
        JsonArray chests = new JsonArray();
        for (BlockPos pos : new BlockPos[]{center.north(2), center.east(2), center.south(2), center.west(2)}) {
            ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(pos);
            CompoundTag tag = chest.toTag(new CompoundTag());
            if (!LootTables.DESERT_PYRAMID_CHEST.toString().equals(tag.getString("LootTable"))) {
                throw new IllegalStateException("Temple fixture missing unopened loot");
            }
            JsonObject row = new JsonObject(); row.addProperty("x", pos.getX()); row.addProperty("z", pos.getZ());
            row.addProperty("lootSeed", tag.getLong("LootTableSeed"));
            row.add("items", items(world, pos, tag.getLong("LootTableSeed")));
            chests.add(row);
        }
        root.add("chests", chests);
        JsonArray vectors = new JsonArray();
        for (int i = 0; i < 512; i++) {
            long lootSeed = new Random(i * 0x9e3779b97f4a7c15L).nextLong();
            JsonObject row = new JsonObject(); row.addProperty("lootSeed", lootSeed);
            row.add("items", items(world, center, lootSeed)); vectors.add(row);
        }
        root.add("vectors", vectors);
        JsonArray allTables = new JsonArray();
        int chestIndex = 0;
        for (net.minecraft.util.Identifier id : LootTables.getAll()) {
            if (!id.getPath().startsWith("chests/")) continue;
            BlockPos pos = new BlockPos(cx * 16 + 1 + chestIndex % 7 * 2, 90, cz * 16 + 1 + chestIndex / 7 * 2);
            world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
            world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
            world.removeBlockEntity(pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            world.setBlockState(pos, (chestIndex % 2 == 0 ? Blocks.CHEST : Blocks.TRAPPED_CHEST).getDefaultState());
            long lootSeed = 1000L + chestIndex++;
            ((ChestBlockEntity) world.getBlockEntity(pos)).setLootTable(id, lootSeed);
            for (int i = 0; i < 16; i++) {
                JsonObject row = new JsonObject();
                row.addProperty("table", id.toString()); row.addProperty("lootSeed", lootSeed + i);
                row.addProperty("x", pos.getX()); row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
                row.add("items", items(world, pos, lootSeed + i, id));
                allTables.add(row);
            }
        }
        root.add("allTables", allTables);
        JsonArray doubleChest = new JsonArray();
        for (int i = 0; i < 2; i++) {
            BlockPos pos = new BlockPos(cx * 16 + 3 - i, 93, cz * 16 + 1);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.CHEST_TYPE,
                    i == 0 ? net.minecraft.block.enums.ChestType.RIGHT : net.minecraft.block.enums.ChestType.LEFT));
            net.minecraft.util.Identifier id = i == 0 ? LootTables.BASTION_BRIDGE_CHEST : LootTables.BASTION_TREASURE_CHEST;
            ((ChestBlockEntity) world.getBlockEntity(pos)).setLootTable(id, 9876L + i);
            JsonObject row = new JsonObject();
            row.addProperty("x", pos.getX()); row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
            row.add("items", items(world, pos, 9876L + i, id)); doubleChest.add(row);
        }
        root.add("doubleChest", doubleChest);
        try (java.io.Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            new com.google.gson.Gson().toJson(root, writer);
        }
        player.teleport(world, center.getX() + 0.5, 53, center.getZ() + 0.5, 0, 0);
        net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket packet =
                new net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket(world.getChunk(cx, cz), 65535, false);
        if (((ReplayChestLootPacket) packet).zsgRooms$getChestLoot() == null) throw new IllegalStateException("Chest metadata hook missing");
        for (CompoundTag tag : packet.getBlockEntityTagList()) {
            if (tag.contains("LootTable") || tag.contains("LootTableSeed")) throw new IllegalStateException("Loot leaked to live chunk NBT");
        }
        // Let vanilla send the fixture chunk after its render-distance center update.
    }

    private static JsonArray items(ServerWorld world, BlockPos pos, long seed) {
        return items(world, pos, seed, LootTables.DESERT_PYRAMID_CHEST);
    }

    private static JsonArray items(ServerWorld world, BlockPos pos, long seed, net.minecraft.util.Identifier table) {
        SimpleInventory inventory = new SimpleInventory(27);
        boolean maps = table.getPath().equals("chests/shipwreck_map") || table.getPath().startsWith("chests/underwater_ruin_");
        LootContext.Builder context = new LootContext.Builder(world).random(seed);
        if (!maps) context.parameter(LootContextParameters.POSITION, pos);
        world.getServer().getLootManager().getTable(table).supplyInventory(inventory,
                context.build(maps ? LootContextTypes.EMPTY : LootContextTypes.CHEST));
        JsonArray items = new JsonArray();
        for (int i = 0; i < 27; i++) {
            net.minecraft.item.ItemStack item = inventory.getStack(i);
            if (maps && item.getItem() == net.minecraft.item.Items.MAP) item.setCustomName(
                    new net.minecraft.text.LiteralText("Treasure map (destination not predicted)"));
            items.add(item.toTag(new CompoundTag()).toString());
        }
        return items;
    }
}
