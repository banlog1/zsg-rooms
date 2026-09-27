package zsgrooms.modid;

import net.minecraft.advancement.Advancement;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class AaThunderlessTest {
    @Test
    void everyVanillaAdvancementIsRequiredExceptLightningAndRoots() throws Exception {
        try (ZipFile jar = new ZipFile(new File(Advancement.class.getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            List<String> ids = jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.startsWith("data/minecraft/advancements/") && name.endsWith(".json")
                            && !name.contains("/recipes/"))
                    .map(name -> "minecraft:" + name.substring("data/minecraft/advancements/".length(), name.length() - 5))
                    .collect(Collectors.toList());
            assertTrue(ids.size() > 75, "Must exercise the full bundled vanilla advancement set");
            for (String id : ids) {
                assertEquals(!id.endsWith("/root") && !AaThunderless.EXCLUDED.equals(id), AaThunderless.isRequired(id), id);
            }
            Set<String> done = new HashSet<>(ids);
            done.remove(AaThunderless.EXCLUDED);
            assertTrue(AaThunderless.isComplete(ids, id -> id, done::contains));
            for (String id : ids) {
                if (!AaThunderless.isRequired(id)) continue;
                done.remove(id);
                assertFalse(AaThunderless.isComplete(ids, value -> value, done::contains), id);
                done.add(id);
            }
        }
        assertFalse(AaThunderless.isRequired("minecraft:recipes/tools/diamond_pickaxe"));
        assertFalse(AaThunderless.isRequired("mod:adventure/extra"));
        assertFalse(AaThunderless.isComplete(Collections.<String>emptyList(), id -> id, id -> true));
    }

    @Test
    void selectedAaSeedKeepsItsGoalAndDoesNotLeakIntoOrdinaryFilters() {
        for (String filter : Arrays.asList(AaThunderless.FILTER, "rooms-temple-v5", "random", "rooms-mix")) {
            String seed = ZsgSeedBridge.buildSeedForStructure("123", filter, 4);
            InGame game = new InGame(seed, "aa-test", InGame.SeedType.FIXED, false);
            game.targetStructure = filter;
            assertEquals(AaThunderless.FILTER.equals(filter), game.isAaThunderless());
        }
        for (int roll = 0; roll < 100; roll++) assertFalse(AaThunderless.isFilter(ZsgRoomsSeedMode.filterForRoll(roll)));
        assertEquals("desert_pyramid", StructureSpawnProximity.structureKeyForFilter(AaThunderless.FILTER));
        assertTrue(SurfaceLavaPoolGuarantee.appliesTo(AaThunderless.FILTER));
    }
}
