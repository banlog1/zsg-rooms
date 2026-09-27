package zsgrooms.model;

import com.seedfinding.mcbiome.source.EndBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.EndCity;
import com.seedfinding.mcterrain.terrain.EndTerrainGenerator;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AaEndModelTest {
    @Test
    void cityTerrainMatchesSeedFindingAndFirstGatewayMatchesVanillaOrder() throws Exception {
        Process process = new ProcessBuilder(Path.of("run/filter-worker/aa-end-test.exe").toAbsolutePath().toString()).start();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (int i = 0; i < 128; i++) {
                String line = input.readLine();
                assertNotNull(line, "Native End test ended early");
                int[] actual = Arrays.stream(line.split(" ")).mapToInt(Integer::parseInt).toArray();
                assertEquals(11, actual.length);
                assertEquals(i, actual[0]);
                long seed = i * 0x9e3779b97f4a7c15L;
                EndCity city = new EndCity(MCVersion.v1_16_1);
                ChunkRand random = new ChunkRand();
                int regionX = i % 11 - 5;
                regionX += regionX < 0 ? -4 : 4;
                var pos = city.getInRegion(seed, regionX, i % 13 - 6, random);
                assertEquals(pos.getX() * 16, actual[1]);
                assertEquals(pos.getZ() * 16, actual[2]);
                EndBiomeSource biomes = new EndBiomeSource(MCVersion.v1_16_1, seed);
                EndTerrainGenerator terrain = new EndTerrainGenerator(biomes);
                assertEquals(city.canSpawn(pos, biomes), actual[3] != 0, "Biome " + i);
                assertEquals(EndCity.getAverageYPosition(terrain, pos.getX(), pos.getZ()) >= 60, actual[4] != 0, "Terrain " + i);
                // Ship layouts use the offline vanilla oracle: the pinned SeedFinding
                // model attaches small-tower bridges to the wrong selected floor.
                ArrayList<Integer> gateways = new ArrayList<>();
                for (int j = 0; j < 20; j++) gateways.add(j);
                Collections.shuffle(gateways, new Random(seed));
                int gateway = gateways.get(19);
                double angle = 2.0 * (-Math.PI + (Math.PI / 20) * gateway);
                assertEquals((int) Math.floor(96 * Math.cos(angle)), actual[9], "Gateway X " + i);
                assertEquals((int) Math.floor(96 * Math.sin(angle)), actual[10], "Gateway Z " + i);
            }
            assertNull(input.readLine());
            assertEquals(0, process.waitFor());
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }
}
