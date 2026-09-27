package zsgrooms.modid.filter;

import net.minecraft.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.structure.EndCityGenerator;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.level.storage.LevelStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Offline template-layout calibration only. No server, world or chunks are created. */
class AaEndLayoutParityTest {
    @TempDir Path directory;

    @Test
    void endCityPiecesAndShipPositionsMatchVanilla() throws Exception {
        assumeTrue(Boolean.getBoolean("zsgrooms.modelParity"));
        Bootstrap.initialize();
        LevelStorage storage = new LevelStorage(directory, directory, null);
        try (LevelStorage.Session session = storage.createSession("templates")) {
            StructureManager templates = new StructureManager(null, session, null) {
                private final Map<Identifier, Structure> loaded = new HashMap<>();
                @Override public Structure getStructureOrBlank(Identifier id) {
                    return loaded.computeIfAbsent(id, key -> {
                        String path = "/data/" + key.getNamespace() + "/structures/" + key.getPath() + ".nbt";
                        try (InputStream input = getClass().getResourceAsStream(path)) {
                            assertNotNull(input, path);
                            Structure structure = new Structure();
                            structure.fromTag(NbtIo.readCompressed(input));
                            return structure;
                        } catch (Exception e) { throw new AssertionError("Cannot load template " + key, e); }
                    });
                }
            };
            Process process = new ProcessBuilder(Paths.get("run/filter-worker/aa-end-test.exe").toAbsolutePath().toString()).start();
            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                for (int i = 0; i < 128; i++) {
                    String line = input.readLine();
                    assertNotNull(line);
                    int[] actual = Arrays.stream(line.split(" ")).mapToInt(Integer::parseInt).toArray();
                    long seed = i * 0x9e3779b97f4a7c15L;
                    Random random = new Random(seed);
                    random.setSeed((actual[1] >> 4) * random.nextLong() ^ (actual[2] >> 4) * random.nextLong() ^ seed);
                    BlockRotation rotation = BlockRotation.values()[random.nextInt(4)];
                    ArrayList<StructurePiece> pieces = new ArrayList<>();
                    EndCityGenerator.addPieces(templates, new BlockPos(actual[1] + 8, 0, actual[2] + 8), rotation, pieces, random);
                    assertEquals(pieces.size(), actual[5], "Layout " + i);
                    int ships = 0;
                    for (StructurePiece piece : pieces) {
                        CompoundTag tag = piece.getTag();
                        if (!tag.getString("Template").equals("ship")) continue;
                        ships++;
                        BlockPos center = new BlockPos(6, 0, 14).rotate(BlockRotation.valueOf(tag.getString("Rot")))
                                .add(tag.getInt("TPX"), 0, tag.getInt("TPZ"));
                        assertEquals(center.getX(), actual[7], "Ship X " + i);
                        assertEquals(center.getZ(), actual[8], "Ship Z " + i);
                    }
                    assertEquals(ships, actual[6], "Ship count " + i);
                }
                assertNull(input.readLine());
                assertEquals(0, process.waitFor());
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }
}
