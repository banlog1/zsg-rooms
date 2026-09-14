package zsgrooms.model.nether;

import Xinyuiii.enumType.BastionType;
import Xinyuiii.properties.BastionGenerator;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import org.junit.jupiter.api.Test;
import xyz.duncanruns.zsg.javabits.ZSGJavaBits;
import zsgrooms.modid.filter.BastionLayoutChecks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NetherModelTest {
    @Test
    void obsidianMatchesUnmodifiedZsgAcrossTypesRotationsCoordinatesAndSisters() throws Exception {
        BastionGenerator generator = new BastionGenerator(MCVersion.v1_16_1);
        Class<?> holder = Class.forName("xyz.duncanruns.zsg.javabits.ZSGJavaBits$ChestInfo");
        var constructor = holder.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object chestInfo = constructor.newInstance();
        Method upstream = ZSGJavaBits.class.getDeclaredMethod("respondBastionObsidian", BastionGenerator.class, holder);
        upstream.setAccessible(true);
        EnumSet<BastionType> types = EnumSet.noneOf(BastionType.class);
        EnumSet<BlockRotation> rotations = EnumSet.noneOf(BlockRotation.class);
        for (int i = 0; i < 128; i++) {
            long lower = (i * 0x9e3779b97f4a7c15L) & 0xffffffffffffL;
            int bx = i % 13 - 6, bz = i % 11 - 5;
            CPos pos = new CPos(bx, bz);
            generator.generate(lower, pos);
            types.add(generator.getType());
            rotations.add(generator.getPieces().get(0).rotation);
            int actual = ZsgNetherChecks.obsidianScore(generator, pos);
            byte[] request = ByteBuffer.allocate(10).putLong(lower).put((byte) bx).put((byte) bz).array();
            assertEquals(call(upstream, request, generator, chestInfo), actual, "ZSG loot vector " + i);
            generator.generate(lower | (0xabcdL << 48), pos);
            assertEquals(actual, ZsgNetherChecks.obsidianScore(generator, pos), "Sister loot vector " + i);
        }
        assertEquals(4, types.size());
        assertEquals(4, rotations.size());
    }

    @Test
    void terrainMatchesUnmodifiedZsgAndIsLower48Invariant() throws Exception {
        Method upstream = ZSGJavaBits.class.getDeclaredMethod("respondCheckForTerrain");
        upstream.setAccessible(true);
        int passes = 0;
        for (int i = 0; i < 20; i++) {
            long lower = (i * 0x9e3779b97f4a7c15L) & 0xffffffffffffL;
            int bx = i == 0 ? 0 : i % 13 - 6, bz = i == 0 ? 0 : i % 11 - 5;
            int fx = i % 33 - 16, fz = i % 29 - 14;
            byte[] request = ByteBuffer.allocate(12).putLong(lower).put((byte) bx).put((byte) bz)
                    .put((byte) fx).put((byte) fz).array();
            boolean expected = call(upstream, request) == 1;
            assertEquals(expected, ZsgNetherChecks.terrain(lower, bx, bz, fx, fz), "ZSG terrain vector " + i);
            assertEquals(expected, ZsgNetherChecks.terrain(lower | (0xabcdL << 48), bx, bz, fx, fz), "Sister terrain vector " + i);
            if (expected) passes++;
        }
        assertTrue(passes > 0 && passes < 20, "Both accepting and rejecting terrain cases are covered");
    }

    @Test
    void stablesRequireBothPiecesAndOtherTypesKeepZsgEligibility() {
        String prefix = "minecraft:bastion/hoglin_stable/";
        assertFalse(BastionLayoutChecks.accepts(List.of(prefix + "air_base")));
        assertFalse(BastionLayoutChecks.accepts(List.of(prefix + "walls/side_wall_1")));
        assertFalse(BastionLayoutChecks.accepts(List.of(prefix + "ramparts/ramparts_1")));
        assertTrue(BastionLayoutChecks.accepts(List.of(prefix + "walls/side_wall_1", prefix + "ramparts/ramparts_1")));
        assertTrue(BastionLayoutChecks.accepts(List.of("minecraft:bastion/bridge/starting_pieces/entrance_base")));
        BastionGenerator generator = new BastionGenerator(MCVersion.v1_16_1);
        int eligibleStables = 0, rejectedStables = 0;
        for (int i = 0; i < 128; i++) {
            generator.generate((i * 0x9e3779b97f4a7c15L) & 0xffffffffffffL, new CPos(0, 0));
            List<String> names = generator.getPieces().stream().map(piece -> "minecraft:bastion/" + piece.getName()).collect(Collectors.toList());
            if (generator.getType() == BastionType.STABLES) {
                if (BastionLayoutChecks.accepts(names)) eligibleStables++; else rejectedStables++;
            } else assertTrue(BastionLayoutChecks.accepts(names));
        }
        assertTrue(eligibleStables > 0 && rejectedStables > 0,
                "Actual modeled stables cover both layout outcomes: " + eligibleStables + "/" + rejectedStables);
    }

    @Test
    void preservesZsgBarterAllowanceAndChestSelection() {
        assertEquals(7, ZsgNetherChecks.expectedTradedObsidian(BastionType.BRIDGE));
        assertEquals(4, ZsgNetherChecks.expectedTradedObsidian(BastionType.STABLES));
        for (int y : new int[]{35, 72}) assertTrue(ZsgNetherChecks.eligibleChest(BastionType.STABLES,
                new BPos(0, y, 0), BPos.ORIGIN, BlockRotation.NONE));
        assertFalse(ZsgNetherChecks.eligibleChest(BastionType.STABLES, new BPos(0, 73, 0), BPos.ORIGIN, BlockRotation.NONE));
    }

    @Test
    void rejectsInvalidRequestsWithoutMinecraftFallback() {
        NetherModel model = new NetherModel();
        assertThrows(IllegalArgumentException.class, () -> model.evaluate(1L << 48, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> model.evaluate(1, -7, 0, 0, 0));
    }

    private static int call(Method method, byte[] request, Object... args) throws Exception {
        InputStream previousInput = System.in;
        PrintStream previousOutput = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(request));
            System.setOut(new PrintStream(bytes));
            assertEquals(Boolean.TRUE, method.invoke(null, args));
            assertEquals(1, bytes.size());
            return bytes.toByteArray()[0];
        } finally {
            System.setIn(previousInput);
            System.setOut(previousOutput);
        }
    }
}
