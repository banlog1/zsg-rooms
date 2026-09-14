package zsgrooms.modid.filter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.loot.LootGsons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Rejects only known templates whose loot cannot contain iron. Unknown elements take the full path. */
final class VillageLootPotential {
    private final Map<String, Boolean> templates = new HashMap<String, Boolean>();
    private final Map<String, Boolean> tables = new HashMap<String, Boolean>();

    boolean canSupplyIron(ServerWorld world, StructureStart<?> start) {
        for (StructurePiece piece : start.getChildren()) {
            if (!(piece instanceof PoolStructurePiece)) return true;
            CompoundTag element = piece.getTag().getCompound("pool_element");
            String elementType = element.getString("element_type");
            if (elementType.equals("minecraft:empty_pool_element")) continue;
            if (elementType.equals("minecraft:feature_pool_element")) {
                String feature = element.getCompound("feature").getString("name");
                // Vanilla village trees, flowers, cactus patches and hay/melon piles cannot supply chest loot.
                if (feature.equals("minecraft:tree") || feature.equals("minecraft:flower")
                        || feature.equals("minecraft:random_patch") || feature.equals("minecraft:block_pile")) continue;
            }
            if (!element.contains("location", 8)) return true;
            String name = element.getString("location");
            if (templates.computeIfAbsent(name, key -> template(world, key))) return true;
        }
        return false;
    }

    private boolean template(ServerWorld world, String name) {
        CompoundTag tag = world.getServer().getStructureManager().getStructureOrBlank(new Identifier(name)).toTag(new CompoundTag());
        if (!tag.contains("palette") && !tag.contains("palettes")) return true;
        ListTag blocks = tag.getList("blocks", 10);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag nbt = blocks.getCompound(i).getCompound("nbt");
            if (nbt.contains("Items", 9)) return true;
            if (nbt.contains("LootTable", 8) && tables.computeIfAbsent(nbt.getString("LootTable"), key -> table(world, key))) return true;
        }
        return false;
    }

    private boolean table(ServerWorld world, String name) {
        JsonObject table = LootGsons.getTableGsonBuilder().create().toJsonTree(
                world.getServer().getLootManager().getTable(new Identifier(name))).getAsJsonObject();
        if (table.has("functions") || !table.has("pools")) return true;
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            JsonObject value = pool.getAsJsonObject();
            if (value.has("functions")) return true;
            for (JsonElement entry : value.getAsJsonArray("entries")) {
                JsonObject item = entry.getAsJsonObject();
                if (!item.has("type") || !"minecraft:item".equals(item.get("type").getAsString())) return true;
                String id = item.get("name").getAsString();
                if (id.equals("minecraft:iron_ingot") || id.equals("minecraft:iron_nugget") || id.equals("minecraft:iron_block")) return true;
            }
        }
        return false;
    }
}
