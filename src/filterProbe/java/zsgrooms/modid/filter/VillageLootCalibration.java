package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.ZsgRooms;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sampled vanilla-world calibration only. Does not participate in production acceptance. */
final class VillageLootCalibration {
    private final JsonArray samples;
    private final Path output;
    private int next;
    private boolean finished;

    private VillageLootCalibration(JsonArray samples, Path output) { this.samples = samples; this.output = output; }

    static void run(MinecraftServer server) {
        try (Reader reader = Files.newBufferedReader(Paths.get(System.getProperty("zsgrooms.villageCalibration.input")), StandardCharsets.UTF_8)) {
            JsonObject input = new JsonParser().parse(reader).getAsJsonObject();
            if (!"OFFLINE_CALIBRATION_ONLY".equals(input.get("purpose").getAsString())) throw new IllegalArgumentException("Calibration input required");
            JsonArray samples = input.getAsJsonArray("samples");
            if (samples.size() == 0 || samples.size() > 68) throw new IllegalArgumentException("Use 1-68 samples");
            Path output = Paths.get(System.getProperty("zsgrooms.villageCalibration.output"));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.createFile(output);
            VillageLootCalibration calibration = new VillageLootCalibration(samples, output);
            ServerTickEvents.END_SERVER_TICK.register(calibration::tick);
        } catch (Exception error) {
            ZsgRooms.LOGGER.error("[VillageLootCalibration] FAIL: {}", error.getClass().getSimpleName());
            server.stop(false);
        }
    }

    private void tick(MinecraftServer server) {
        if (finished) return;
        JsonObject sample = samples.get(next).getAsJsonObject();
        long started = System.nanoTime();
        try {
            JsonObject result;
            try (ProbeWorlds worlds = new ProbeWorlds(server, Long.parseLong(sample.get("seed").getAsString()), UUID.randomUUID().toString());
                 AutoCloseable updates = () -> server.runTasks(() -> server.getTaskCount() == 0)) {
                result = inspect(server.getOverworld(), sample);
            }
            result.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000L);
            Files.write(output, (result.toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            ZsgRooms.LOGGER.info("[VillageLootCalibration] sample={}, biome={}, recovered={}, modeledMatch={}/{}, resourcesPass={}, ms={}",
                    sample.get("id"), sample.get("biome"), sample.get("recovered"), result.get("matching"), result.get("modeled"),
                    result.get("resourcesPass"), result.get("elapsedMs"));
        } catch (Exception error) {
            finished = true;
            ZsgRooms.LOGGER.error("[VillageLootCalibration] FAIL: sample={}, {}", sample.get("id"), error.getClass().getSimpleName());
            server.stop(false);
            return;
        }
        if (++next == samples.size()) {
            finished = true;
            ZsgRooms.LOGGER.info("[VillageLootCalibration] COMPLETE: {} samples (completion does not imply model agreement)", next);
            server.stop(false);
        }
    }

    private static JsonObject inspect(ServerWorld world, JsonObject sample) {
        StructureStart<?> start = GeneratedStructureProbe.start(world, StructureFeature.VILLAGE,
                new ChunkPos(sample.get("chunkX").getAsInt(), sample.get("chunkZ").getAsInt()));
        if (start == null) throw new IllegalStateException("Missing village start");
        JsonArray layout = new JsonArray();
        JsonArray wellConnectors = new JsonArray();
        if (sample.has("includeLayout") && sample.get("includeLayout").getAsBoolean()) {
            for (StructurePiece piece : start.getChildren()) {
                JsonObject entry = new JsonObject();
                CompoundTag tag = piece.getTag();
                entry.addProperty("name", tag.getCompound("pool_element").getString("location"));
                entry.addProperty("x", tag.getInt("PosX"));
                entry.addProperty("y", tag.getInt("PosY"));
                entry.addProperty("z", tag.getInt("PosZ"));
                entry.addProperty("rotation", tag.getString("rotation"));
                entry.addProperty("nbt", tag.toString());
                layout.add(entry);
            }
            net.minecraft.structure.Structure well = world.getServer().getStructureManager().getStructureOrBlank(
                    new net.minecraft.util.Identifier("minecraft:village/common/well_bottom"));
            for (net.minecraft.structure.Structure.StructureBlockInfo info : well.getInfosForBlock(
                    BlockPos.ORIGIN, new net.minecraft.structure.StructurePlacementData(), net.minecraft.block.Blocks.JIGSAW)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("x", info.pos.getX()); entry.addProperty("y", info.pos.getY()); entry.addProperty("z", info.pos.getZ());
                entry.addProperty("state", info.state.toString()); entry.addProperty("nbt", info.tag.toString());
                wellConnectors.add(entry);
            }
        }
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        // Generate actual smith buildings as well as predicted chest chunks, not the whole village.
        for (StructurePiece piece : new ArrayList<>(start.getChildren())) {
            String name = piece.getTag().getCompound("pool_element").getString("location");
            if (!smith(name)) continue;
            BlockBox box = piece.getBoundingBox();
            for (int x = box.minX >> 4; x <= box.maxX >> 4; x++) for (int z = box.minZ >> 4; z <= box.maxZ >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
        for (JsonElement value : sample.getAsJsonArray("chests")) {
            JsonObject chest = value.getAsJsonObject();
            chunks.add(new ChunkPos(chest.get("x").getAsInt() >> 4, chest.get("z").getAsInt() >> 4));
        }
        if (chunks.size() > 128) throw new IllegalStateException("Too many smith chunks");
        for (ChunkPos chunk : chunks) world.getChunk(chunk.x, chunk.z, ChunkStatus.FULL);
        Map<BlockPos, JsonObject> actual = new LinkedHashMap<>();
        int iron = 0, pickaxes = 0, diamonds = 0;
        for (ChunkPos pos : chunks) {
            WorldChunk chunk = world.getWorldChunk(new BlockPos(pos.x << 4, 0, pos.z << 4));
            for (BlockPos chestPos : new ArrayList<>(chunk.getBlockEntityPositions())) {
                if (!start.getBoundingBox().contains(chestPos)) continue;
                BlockEntity entity = world.getBlockEntity(chestPos);
                if (!(entity instanceof ChestBlockEntity)) continue;
                CompoundTag tag = entity.toTag(new CompoundTag());
                String table = tag.getString("LootTable");
                if (!table.startsWith("minecraft:chests/village/") || !smith(table)) continue;
                ChestBlockEntity copy = new ChestBlockEntity();
                copy.fromTag(world.getBlockState(chestPos), tag);
                copy.setLocation(world, chestPos);
                copy.checkLootInteraction(null);
                int ci = 0, cp = 0, cd = 0;
                for (int slot = 0; slot < copy.size(); slot++) {
                    net.minecraft.item.ItemStack stack = copy.getStack(slot);
                    if (stack.getItem() == Items.IRON_INGOT) ci += stack.getCount();
                    if (stack.getItem() == Items.IRON_PICKAXE) cp += stack.getCount();
                    if (stack.getItem() == Items.DIAMOND) cd += stack.getCount();
                }
                if (!tag.equals(entity.toTag(new CompoundTag()))) throw new IllegalStateException("Original chest changed");
                JsonObject loot = new JsonObject();
                loot.addProperty("x", chestPos.getX()); loot.addProperty("y", chestPos.getY()); loot.addProperty("z", chestPos.getZ());
                loot.addProperty("table", table);
                loot.addProperty("iron", ci); loot.addProperty("pickaxes", cp); loot.addProperty("diamonds", cd);
                actual.put(chestPos.toImmutable(), loot);
                iron += ci; pickaxes += cp; diamonds += cd;
            }
        }
        JsonArray comparisons = new JsonArray();
        int modeled = 0, matching = 0;
        for (JsonElement value : sample.getAsJsonArray("chests")) {
            JsonObject predicted = value.getAsJsonObject();
            JsonObject comparison = new JsonObject();
            comparison.add("predicted", predicted);
            JsonObject found = null;
            for (Map.Entry<BlockPos, JsonObject> chest : actual.entrySet()) {
                if (chest.getKey().getX() == predicted.get("x").getAsInt() && chest.getKey().getZ() == predicted.get("z").getAsInt()) {
                    if (found != null) throw new IllegalStateException("Ambiguous chest column");
                    found = chest.getValue();
                }
            }
            if (found != null) comparison.add("actual", found);
            boolean match = found != null && found.get("iron").equals(predicted.get("iron"))
                    && found.get("pickaxes").equals(predicted.get("pickaxes")) && found.get("diamonds").equals(predicted.get("diamonds"));
            comparison.addProperty("match", match);
            if (predicted.get("modeled").getAsBoolean()) { modeled++; if (match) matching++; }
            comparisons.add(comparison);
        }
        JsonObject result = new JsonObject();
        if (layout.size() > 0) {
            result.add("layout", layout);
            result.add("wellConnectors", wellConnectors);
        }
        result.add("id", sample.get("id")); result.add("biome", sample.get("biome")); result.add("recovered", sample.get("recovered"));
        result.addProperty("modeled", modeled); result.addProperty("matching", matching);
        result.addProperty("actualIron", iron); result.addProperty("actualPickaxes", pickaxes); result.addProperty("actualDiamonds", diamonds);
        result.addProperty("resourcesPass", iron >= 4 || (iron >= 1 && (pickaxes > 0 || diamonds >= 3)));
        result.add("comparisons", comparisons);
        JsonArray allActual = new JsonArray();
        actual.values().forEach(allActual::add);
        result.add("actualSmithChests", allActual);
        return result;
    }

    private static boolean smith(String name) {
        return name.contains("weaponsmith") || name.contains("tool_smith") || name.contains("toolsmith") || name.contains("armorer");
    }
}
