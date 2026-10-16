// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SplitMilestoneReaderTest {
    @TempDir Path directory;
    private final SplitMilestoneReader reader = new SplitMilestoneReader(MilestonePacketTrackerTest.ADVANCEMENT_PACKET_ID);

    @Test void validFramesConsumeExactlyTheOriginalBytesInSingleAndBulkReads() throws Exception {
        byte[] input = concat(frame(0, new byte[]{2, 10, 20}), frame(10, new byte[0]),
                frame(10, new byte[]{(byte) 128, 1, 30, 40}), frame(100, new byte[]{1}));
        for (boolean bulk : new boolean[]{false, true}) {
            CountingInput source = new CountingInput(input);
            try (SplitMilestoneReader.PacketFrames frames = frames(source, input, 100, () -> false)) {
                ByteArrayOutputStream actual = new ByteArrayOutputStream();
                byte[] buffer = new byte[17];
                if (bulk) {
                    int count;
                    while ((count = frames.read(buffer, 3, 11)) >= 0) actual.write(buffer, 3, count);
                } else {
                    int value;
                    while ((value = frames.read()) >= 0) actual.write(value);
                }
                assertArrayEquals(input, actual.toByteArray());
                assertEquals(input.length, source.consumed);
                assertEquals(-1, frames.read());
                frames.verifyComplete();
            }
            assertTrue(source.closed);
        }
    }

    @Test void emptyStreamNeedsAnObservedEofBeforeItIsComplete() throws Exception {
        try (SplitMilestoneReader.PacketFrames frames = frames(new CountingInput(new byte[0]), new byte[0], 0, () -> false)) {
            assertThrows(IOException.class, frames::verifyComplete);
            assertEquals(-1, frames.read());
            frames.verifyComplete();
        }
    }

    @Test void validBytesWithoutEofOrWithWrongCrcOrWrongDeclaredSizeCannotComplete() throws Exception {
        byte[] input = frame(0, new byte[]{1, 2, 3});
        try (SplitMilestoneReader.PacketFrames frames = frames(new CountingInput(input), input, 0, () -> false)) {
            byte[] result = new byte[input.length];
            new java.io.DataInputStream(frames).readFully(result);
            assertThrows(IOException.class, frames::verifyComplete);
            assertEquals(-1, frames.read());
            frames.verifyComplete();
        }
        for (boolean wrongCrc : new boolean[]{true, false}) {
            try (SplitMilestoneReader.PacketFrames frames = new SplitMilestoneReader.PacketFrames(
                    new CountingInput(input), input.length + (wrongCrc ? 0 : 1),
                    crc(input) ^ (wrongCrc ? 1 : 0), 0, () -> false)) {
                drain(frames);
                assertThrows(IOException.class, frames::verifyComplete);
            }
        }
    }

    @Test void everyTruncatedHeaderFailsBeforeExposingAnyFrameBytes() throws Exception {
        byte[] full = frame(0, new byte[]{1});
        for (int length = 1; length < 8; length++) {
            CountingInput source = new CountingInput(Arrays.copyOf(full, length));
            try (SplitMilestoneReader.PacketFrames frames = frames(source, full, 0, () -> false)) {
                assertThrows(EOFException.class, () -> frames.read());
                assertThrows(IOException.class, frames::verifyComplete);
            }
            assertTrue(source.closed);
        }
    }

    @Test void everyTruncatedPayloadFailsDuringPrefetch() throws Exception {
        byte[] full = frame(0, new byte[]{1, 2, 3, 4});
        for (int length = 8; length < full.length; length++) {
            CountingInput source = new CountingInput(Arrays.copyOf(full, length));
            try (SplitMilestoneReader.PacketFrames frames = frames(source, full, 0, () -> false)) {
                assertThrows(EOFException.class, () -> frames.read());
                assertThrows(IOException.class, frames::verifyComplete);
            }
            assertTrue(source.closed);
        }
    }

    @Test void invalidLengthsAreRejectedBeforePayloadAllocationOrReading() throws Exception {
        for (int length : new int[]{-1, Integer.MIN_VALUE, SplitMilestoneReader.MAX_PACKET_BYTES + 1, Integer.MAX_VALUE}) {
            byte[] header = header(0, length);
            CountingInput source = new CountingInput(header);
            try (SplitMilestoneReader.PacketFrames frames = new SplitMilestoneReader.PacketFrames(
                    source, SplitMilestoneReader.MAX_STREAM_BYTES, 0, 100, () -> false)) {
                assertThrows(IOException.class, () -> frames.read());
                assertEquals(8, source.consumed);
                assertThrows(IOException.class, frames::verifyComplete);
            }
        }
    }

    @Test void timestampsMustBeNonnegativeMonotonicAndWithinDuration() throws Exception {
        for (byte[] input : new byte[][]{
                frame(-1, new byte[]{1}), frame(101, new byte[]{1}),
                concat(frame(10, new byte[]{1}), frame(9, new byte[]{1}))}) {
            try (SplitMilestoneReader.PacketFrames frames = frames(new CountingInput(input), input, 100, () -> false)) {
                assertThrows(IOException.class, () -> drain(frames));
                assertThrows(IOException.class, frames::verifyComplete);
            }
        }
    }

    @Test void unterminatedOrNegativePacketIdsAreRejected() throws Exception {
        for (byte[] payload : new byte[][]{
                {(byte) 128}, {(byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128},
                {(byte) 255, (byte) 255, (byte) 255, (byte) 255, 15}}) {
            byte[] input = frame(0, payload);
            try (SplitMilestoneReader.PacketFrames frames = frames(new CountingInput(input), input, 0, () -> false)) {
                assertThrows(IOException.class, () -> frames.read());
            }
        }
    }

    @Test void packetCountAndByteBudgetsCannotReturnPartialSuccess() throws Exception {
        byte[] input = frame(0, new byte[]{1});
        for (String field : new String[]{"count", "bytes"}) {
            CountingInput source = new CountingInput(input);
            try (SplitMilestoneReader.PacketFrames frames = new SplitMilestoneReader.PacketFrames(
                    source, SplitMilestoneReader.MAX_STREAM_BYTES, 0, 0, () -> false)) {
                Field counter = SplitMilestoneReader.PacketFrames.class.getDeclaredField(field);
                counter.setAccessible(true);
                counter.setLong(frames, field.equals("count") ? SplitMilestoneReader.MAX_PACKET_COUNT : SplitMilestoneReader.MAX_STREAM_BYTES);
                assertThrows(IOException.class, () -> frames.read());
                assertEquals(8, source.consumed);
                assertThrows(IOException.class, frames::verifyComplete);
            }
        }
        try (SplitMilestoneReader.PacketFrames frames = new SplitMilestoneReader.PacketFrames(
                new CountingInput(input), input.length - 1, crc(input), 0, () -> false)) {
            assertThrows(IOException.class, () -> frames.read());
        }
    }

    @Test void cancellationBeforeReadAndBetweenFramesDoesNotReadTheNextPayload() throws Exception {
        byte[] one = frame(0, new byte[]{1});
        byte[] input = concat(one, frame(1, new byte[]{1, 2}));
        AtomicBoolean cancelled = new AtomicBoolean(true);
        CountingInput source = new CountingInput(input);
        try (SplitMilestoneReader.PacketFrames frames = frames(source, input, 1, cancelled::get)) {
            assertThrows(InterruptedIOException.class, () -> frames.read());
            assertEquals(0, source.consumed);
        }
        cancelled.set(false);
        source = new CountingInput(input);
        try (SplitMilestoneReader.PacketFrames frames = frames(source, input, 1, cancelled::get)) {
            new java.io.DataInputStream(frames).readFully(new byte[one.length]);
            cancelled.set(true);
            assertThrows(InterruptedIOException.class, () -> frames.read());
            assertEquals(one.length, source.consumed);
            assertThrows(IOException.class, frames::verifyComplete);
        }
        assertTrue(source.closed);
    }

    @Test void cancellationDuringPrefetchIsCheckedAtBoundedIntervals() throws Exception {
        byte[] payload = new byte[20000];
        payload[0] = 1;
        byte[] input = frame(0, payload);
        AtomicInteger checks = new AtomicInteger();
        CountingInput source = new CountingInput(input);
        try (SplitMilestoneReader.PacketFrames frames = frames(source, input, 0, () -> checks.incrementAndGet() == 3)) {
            assertThrows(InterruptedIOException.class, () -> frames.read());
            assertEquals(8 + 8192, source.consumed);
            assertThrows(IOException.class, frames::verifyComplete);
        }
        assertTrue(source.closed);
    }

    @Test void threadInterruptionIsHonoredAndRemainsSet() throws Exception {
        byte[] input = frame(0, new byte[]{1});
        assertFalse(Thread.currentThread().isInterrupted());
        try (SplitMilestoneReader.PacketFrames frames = frames(new CountingInput(input), input, 0, () -> false)) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(InterruptedIOException.class, () -> frames.read());
                assertTrue(Thread.currentThread().isInterrupted());
                assertThrows(InterruptedIOException.class, () -> reader.read(directory.resolve("missing.mcpr"), () -> false));
            } finally { Thread.interrupted(); }
        }
    }

    @Test void bulkReadChecksArgumentsAndZeroLengthDoesNotConsumeAnything() throws Exception {
        byte[] input = frame(0, new byte[]{1});
        CountingInput source = new CountingInput(input);
        try (SplitMilestoneReader.PacketFrames frames = frames(source, input, 0, () -> true)) {
            assertEquals(0, frames.read(new byte[0], 0, 0));
            assertEquals(0, source.consumed);
            assertThrows(NullPointerException.class, () -> frames.read(null, 0, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> frames.read(new byte[4], -1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> frames.read(new byte[4], 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> frames.read(new byte[4], 3, 2));
        }
    }

    @Test void completedArchiveReturnsImmutableMilestonesAndReusesOnlyTheSameIdentity() throws Exception {
        Path path = replay("complete.mcpr", packets(20), completeMetadata());
        byte[] archiveBefore = Files.readAllBytes(path);
        List<Milestones.Entry> first = reader.read(path, () -> false);
        assertEquals(1, first.size());
        assertSame(Milestones.Kind.NETHER, first.get(0).kind);
        assertEquals(20, first.get(0).time);
        assertSame(first, reader.read(path, () -> false));
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        assertArrayEquals(archiveBefore, Files.readAllBytes(path), "Reader must not write to the archive");
        FileTime timestamp = Files.getLastModifiedTime(path);
        replay("complete.mcpr", packets(30), completeMetadata());
        Files.setLastModifiedTime(path, timestamp);
        List<Milestones.Entry> changed = reader.read(path, () -> false);
        assertNotSame(first, changed);
        assertEquals(30, changed.get(0).time);
        assertNull(cache().get(CompletedIndexCache.Key.read(replay("other.mcpr", packets(20), completeMetadata()))));
        assertArchiveReleased(path);
    }

    @Test void cancelledScanDoesNotCacheEvenAfterAllPacketsWereRead() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path baseline = replay("baseline.mcpr", packets(20), completeMetadata());
        reader.read(baseline, () -> { checks.incrementAndGet(); return false; });
        int lastCheck = checks.get();
        Path path = replay("cancelled.mcpr", packets(20), completeMetadata());
        checks.set(0);
        assertThrows(InterruptedIOException.class, () -> reader.read(path, () -> checks.incrementAndGet() >= lastCheck));
        assertNull(cache().get(CompletedIndexCache.Key.read(path)));
        List<Milestones.Entry> completed = reader.read(path, () -> false);
        assertEquals(1, completed.size());
        assertSame(completed, cache().get(CompletedIndexCache.Key.read(path)));
        assertThrows(InterruptedIOException.class, () -> reader.read(path, () -> true));
        assertArchiveReleased(path);
    }

    @Test void truncatedStreamAfterACompletionDoesNotReturnOrCachePartialMilestones() throws Exception {
        byte[] valid = packets(20);
        Path path = replay("partial.mcpr", concat(valid, new byte[]{0, 0, 0}), completeMetadata());
        assertThrows(IOException.class, () -> reader.read(path, () -> false));
        assertNull(cache().get(CompletedIndexCache.Key.read(path)));
        assertThrows(IOException.class, () -> reader.read(path, () -> false));
        assertArchiveReleased(path);
        replay("partial.mcpr", valid, completeMetadata());
        assertEquals(1, reader.read(path, () -> false).size());
    }

    @Test void incompleteOrTruncatedRoomsMetadataIsRejectedAndNotCached() throws Exception {
        for (String metadata : new String[]{"{\"complete\":false,\"truncated\":false}",
                "{\"complete\":true,\"truncated\":true}", "{}"}) {
            Path path = replay("incomplete.mcpr", packets(20), metadata);
            assertThrows(IOException.class, () -> reader.read(path, () -> false));
            assertNull(cache().get(CompletedIndexCache.Key.read(path)));
            assertArchiveReleased(path);
        }
    }

    @Test void standardArchivesWithoutRoomsMetadataStillReadSuccessfullyWithoutCaching() throws Exception {
        Path path = replay("standard.mcpr", packets(20), null);
        List<Milestones.Entry> first = reader.read(path, () -> false);
        assertEquals(1, first.size());
        assertNotSame(first, reader.read(path, () -> false));
        assertArchiveReleased(path);
    }

    @Test void isolatedConstructorAndPreCancelledReadNeedNoGameRegistryOrFile() {
        assertThrows(IllegalArgumentException.class, () -> new SplitMilestoneReader(-1));
        assertThrows(InterruptedIOException.class, () -> reader.read(directory.resolve("missing.mcpr"), () -> true));
    }

    private static SplitMilestoneReader.PacketFrames frames(CountingInput source, byte[] full, int duration,
                                                            BooleanSupplier cancelled) {
        return new SplitMilestoneReader.PacketFrames(source, full.length, crc(full), duration, cancelled);
    }

    private static void drain(SplitMilestoneReader.PacketFrames frames) throws IOException {
        byte[] buffer = new byte[37];
        while (frames.read(buffer, 0, buffer.length) >= 0) { }
    }

    private static byte[] header(int time, int length) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { out.writeInt(time); out.writeInt(length); }
        return bytes.toByteArray();
    }

    private static byte[] frame(int time, byte[] payload) throws IOException {
        return concat(header(time, payload.length), payload);
    }

    private static byte[] packet(int time, int id, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            MilestonePacketTrackerTest.varInt(out, id);
            out.write(payload);
        }
        return frame(time, bytes.toByteArray());
    }

    private static byte[] packets(int completionTime) throws IOException {
        ByteArrayOutputStream login = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(login)) {
            out.writeLong(0);
            out.writeLong(1);
            MilestonePacketTrackerTest.string(out, "Runner");
        }
        String id = Milestones.Kind.NETHER.id;
        return concat(packet(0, 2, login.toByteArray()),
                packet(10, MilestonePacketTrackerTest.ADVANCEMENT_PACKET_ID, MilestonePacketTrackerTest.payload(id, true, true, false, false)),
                packet(completionTime, MilestonePacketTrackerTest.ADVANCEMENT_PACKET_ID, MilestonePacketTrackerTest.payload(id, false, false, false, true)));
    }

    private Path replay(String name, byte[] packets, String races) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip, "recording.tmcpr", packets);
            entry(zip, "metaData.json", ("{\"protocol\":736,\"fileFormatVersion\":14,\"fileFormat\":\"MCPR\",\"duration\":100}")
                    .getBytes(StandardCharsets.UTF_8));
            if (races != null) entry(zip, "zsg-rooms/races.json", races.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }

    private static String completeMetadata() { return "{\"complete\":true,\"truncated\":false}"; }

    private static byte[] concat(byte[]... arrays) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] array : arrays) bytes.write(array);
        return bytes.toByteArray();
    }

    private static long crc(byte[] value) { CRC32 crc = new CRC32(); crc.update(value); return crc.getValue(); }

    @SuppressWarnings("unchecked")
    private static CompletedIndexCache<List<Milestones.Entry>> cache() throws Exception {
        Field field = SplitMilestoneReader.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        return (CompletedIndexCache<List<Milestones.Entry>>) field.get(null);
    }

    private void assertArchiveReleased(Path path) throws IOException {
        Path moved = directory.resolve("released.mcpr");
        Files.move(path, moved);
        Files.move(moved, path);
    }

    private static final class CountingInput extends ByteArrayInputStream {
        int consumed;
        boolean closed;
        CountingInput(byte[] value) { super(value); }
        @Override public synchronized int read() {
            int value = super.read();
            if (value >= 0) consumed++;
            return value;
        }
        @Override public synchronized int read(byte[] buffer, int offset, int length) {
            int count = super.read(buffer, offset, length);
            if (count > 0) consumed += count;
            return count;
        }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
