// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BarterHistoryTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void missingIsUnavailableWhilePresentEmptyIsKnownZero() throws Exception {
        assertFalse(BarterHistory.read(zip("old.mcpr", null), 100).available);
        BarterHistory empty = BarterHistory.read(zip("new.mcpr", track("", false)), 100);
        assertTrue(empty.available);
        assertFalse(empty.truncated);
        assertTrue(empty.events.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> empty.events.add(null));
    }

    @Test void cancelledBackgroundReadStopsWithoutClearingTheInterrupt() throws Exception {
        Path path = zip("cancelled.mcpr", track("", false));
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.io.InterruptedIOException.class, () -> BarterHistory.read(path, 100));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test void roundTripsCountsNbtPositionsAndWorldPlayheadFiltersWithDefensiveCopies() throws Exception {
        ItemStack potion = new ItemStack(Items.POTION);
        potion.getOrCreateTag().putString("Potion", "minecraft:fire_resistance");
        String items = "\"" + encode(new ItemStack(Items.ENDER_PEARL, 8)) + "\",\"" + encode(potion) + "\"";
        BarterHistory history = read(track(event(10, 0, items) + "," + event(10, 0, "") + "," + event(30, 1, ""), true));
        assertTrue(history.available);
        assertTrue(history.truncated);
        assertEquals(3, history.events.size());
        BarterHistory.Event event = history.events.get(0);
        assertEquals(10, event.time);
        assertEquals("minecraft:the_nether", event.dimension);
        assertEquals("00000000-0000-0000-0000-000000000001", event.piglin.toString());
        assertEquals(1.5, event.x);
        assertEquals(64, event.y);
        assertEquals(-2.5, event.z);
        assertEquals(8, event.items.get(0).getCount());
        event.items.get(0).setCount(1);
        event.items.get(1).getOrCreateTag().putString("Potion", "minecraft:water");
        assertEquals(8, event.items.get(0).getCount());
        assertTrue(ItemStack.areEqual(potion, event.items.get(1)));
        assertThrows(UnsupportedOperationException.class, () -> event.items.set(0, ItemStack.EMPTY));
        assertTrue(history.world(0, 9).isEmpty());
        assertEquals(2, history.world(0, 10).size());
        assertEquals(2, history.world(0, 100).size());
        assertEquals(1, history.world(1, 100).size());
        assertThrows(UnsupportedOperationException.class, () -> history.world(1, 100).clear());
    }

    @Test void invalidSchemaTypesVersionsOrderingAndNonfiniteValuesAreRejected() {
        String valid = track(event(10, 0, ""), false);
        for (String invalid : new String[]{
                valid.replace("\"version\":1", "\"version\":2"),
                valid.replace("\"protocol\":736", "\"protocol\":754"),
                valid.replace("\"truncated\":false", "\"truncated\":\"false\""),
                valid.replace("\"time\":10", "\"time\":\"10\""),
                valid.replace("\"time\":10", "\"time\":1e1"),
                valid.replace("\"time\":10", "\"time\":-1"),
                valid.replace("\"time\":10", "\"time\":101"),
                valid.replace("\"world\":0", "\"world\":-1"),
                valid.replace("\"x\":1.5", "\"x\":1e999"),
                valid.replace("\"x\":1.5", "\"x\":null"),
                valid.replace("00000000-0000-0000-0000-000000000001", "0-0-0-0-1"),
                valid.replace("minecraft:the_nether", "../bad dimension"),
                valid.replace("\"world\":0", "\"world\":0,\"world\":1"),
                valid.replace("\"version\":1", "\"version\":1,\"nested\":[[[[]]]]"),
                valid.replace("\"x\":1.5,", ""),
                valid + "{}",
                track(event(11, 0, "") + "," + event(10, 0, ""), false),
                track(event(10, 1, "") + "," + event(11, 0, ""), false),
                valid.substring(0, valid.length() - 1)
        }) assertThrows(IOException.class, () -> read(invalid), invalid);
    }

    @Test void invalidItemPayloadsAreRejectedBeforeVanillaNbtAllocation() throws Exception {
        String validItem = encode(new ItemStack(Items.GRAVEL, 8));
        byte[] original = Base64.getDecoder().decode(validItem);
        byte[] trailing = java.util.Arrays.copyOf(original, original.length + 1);
        byte[] negativeCount = original.clone();
        negativeCount[negativeCount.length - 2] = -1;
        for (String item : new String[]{"", "?", "Ag==", "AAA=", "Af///38BAA==",
                Base64.getEncoder().encodeToString(trailing), Base64.getEncoder().encodeToString(negativeCount),
                malformedNbt(7, Integer.MAX_VALUE, 0), malformedNbt(9, Integer.MAX_VALUE, 10),
                deepNbt(), validItem.substring(0, validItem.length() - 1)}) {
            assertThrows(IOException.class, () -> read(track(event(1, 0, "\"" + item + "\""), false)), item);
        }
        assertTrue(read(track(event(1, 0, "\"AA==\""), false)).events.get(0).items.get(0).isEmpty());
    }

    @Test void archiveJsonAndCountsAreBoundedAndBadTrackDoesNotAffectOtherEntries() throws Exception {
        String event = event(1, 0, "");
        assertThrows(IOException.class, () -> read(track(String.join(",", Collections.nCopies(BarterHistory.MAX_EVENTS + 1, event)), false)));
        assertThrows(IOException.class, () -> read(track(event(1, 0,
                String.join(",", Collections.nCopies(65, "\"AA==\""))), false)));
        assertThrows(IOException.class, () -> read(track(event(1, 0, "\"" + Base64.getEncoder().encodeToString(new byte[8193]) + "\""), false)));
        String manyItems = event(1, 0, String.join(",", Collections.nCopies(64, "\"AA==\"")));
        assertThrows(IOException.class, () -> read(track(String.join(",", Collections.nCopies(513, manyItems)), false)));
        byte[] huge = new byte[BarterHistory.MAX_BYTES + 1];
        assertThrows(IOException.class, () -> BarterHistory.read(new ByteArrayInputStream(huge), 100));
        Path bad = zip("bad.mcpr", "{bad");
        assertThrows(IOException.class, () -> BarterHistory.read(bad, 100));
        try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(bad.toFile())) {
            assertNotNull(archive.getEntry("recording.tmcpr"));
        }
        assertThrows(IOException.class, () -> BarterHistory.read(zip("oversized.mcpr", new String(huge, StandardCharsets.UTF_8)), 100));
    }

    private Path zip(String name, String track) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("recording.tmcpr"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
            if (track != null) {
                zip.putNextEntry(new ZipEntry(BarterHistory.ENTRY));
                zip.write(track.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }
    private static BarterHistory read(String json) throws IOException {
        return BarterHistory.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 100);
    }
    private static String track(String events, boolean truncated) {
        return "{\"version\":1,\"protocol\":736,\"truncated\":" + truncated + ",\"events\":[" + events + "]}";
    }
    private static String event(int time, int world, String items) {
        return "{\"time\":" + time + ",\"world\":" + world + ",\"dimension\":\"minecraft:the_nether\","
                + "\"piglin\":\"00000000-0000-0000-0000-000000000001\",\"x\":1.5,\"y\":64,\"z\":-2.5,\"items\":[" + items + "]}";
    }
    private static String encode(ItemStack stack) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeItemStack(stack);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        } finally { buffer.release(); }
    }
    private static String malformedNbt(int type, int length, int element) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] header = Base64.getDecoder().decode(encode(new ItemStack(Items.POTION)));
            out.write(header, 0, header.length - 1);
            out.writeByte(10); out.writeUTF("");
            out.writeByte(type); out.writeUTF("bad");
            if (type == 9) out.writeByte(element);
            out.writeInt(length); out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private static String deepNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] item = Base64.getDecoder().decode(encode(new ItemStack(Items.POTION)));
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(item, 0, item.length - 1);
            out.writeByte(10); out.writeUTF("");
            for (int i = 0; i < 34; i++) { out.writeByte(10); out.writeUTF("deep"); }
            for (int i = 0; i < 35; i++) out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
