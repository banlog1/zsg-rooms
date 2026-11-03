// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalSoundTrackTest {
    @Test void readsExactEventsWithoutLoadingTheWholeRecordingIntoMemory() throws Exception {
        try (LocalSoundTrack track = read(normal())) {
            assertEquals(3, track.eventCount()); assertEquals(1000, track.endTime());
            LocalSoundTrack.Event event = track.event(1);
            assertEquals(100, event.time); assertEquals(0, event.world); assertEquals(42, event.entity);
            assertEquals(2, event.channel); assertEquals(7, event.category);
            assertEquals("minecraft:block.stone.step", event.sound);
            assertEquals(0.15F, event.volume); assertEquals(1.25F, event.pitch);
            assertEquals(12.125, event.x); assertEquals(64, event.y); assertEquals(-90.75, event.z);
            assertEquals(0, track.after(0)); assertEquals(1, track.after(50)); assertEquals(2, track.after(100));
            assertEquals(3, track.after(10000));
        }
    }

    @Test void authorityIsPerChannelWorldPlayerAndExplicitActiveContext() throws Exception {
        Fixture file = new Fixture(3).context(10, 0, 42, 3).event(20, 0, 42, 1)
                .context(30, -1, -1, 0).context(40, 1, 42, 1).event(50, 1, 42, 1).end(100);
        try (LocalSoundTrack track = read(file)) {
            assertEquals(0, track.coverage(9, 0, 42)); assertEquals(3, track.coverage(10, 0, 42));
            assertEquals(0, track.coverage(20, 0, 99)); assertEquals(0, track.coverage(20, 1, 42));
            assertEquals(0, track.coverage(30, 0, 42)); assertEquals(0, track.coverage(39, 0, 42));
            assertEquals(0, track.coverage(40, 0, 42)); assertEquals(1, track.coverage(40, 1, 42));
            assertEquals(1, track.coverage(99, 1, 42)); assertEquals(0, track.coverage(100, 1, 42));
            assertEquals(3, track.coverage(20, 0, 42));
        }
    }

    @Test void sameMillisecondContextHandoffUsesLastContextAndKeepsEventOrder() throws Exception {
        try (LocalSoundTrack track = read(new Fixture(127).context(0, 0, 42, 127).event(10, 0, 42, 1)
                .context(20, -1, -1, 0).context(20, 1, 7, 127).event(20, 1, 7, 2).event(20, 1, 7, 4).end(100))) {
            assertEquals(127, track.coverage(20, 1, 7)); assertEquals(0, track.coverage(20, 0, 42));
            assertEquals(1, track.event(0).channel); assertEquals(2, track.event(1).channel); assertEquals(4, track.event(2).channel);
        }
    }

    @Test void everyTruncationAndTrailingDataIsRejectedRatherThanClaimingAuthority() throws Exception {
        byte[] bytes = normal().bytes();
        for (int length = 0; length < bytes.length; length++) {
            byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(IOException.class, () -> LocalSoundTrack.read(new ByteArrayInputStream(truncated), 10000));
        }
        assertThrows(IOException.class, () -> LocalSoundTrack.read(new ByteArrayInputStream(Arrays.copyOf(bytes, bytes.length + 1)), 10000));
    }

    @Test void rejectsUnsupportedVersionsMasksUnknownRecordsInvalidEventsAndIdentityCrossing() throws Exception {
        byte[] bytes = normal().bytes();
        for (int offset : new int[] {0, 4, 8}) {
            byte[] invalid = bytes.clone(); ByteBuffer.wrap(invalid).putInt(offset, -1);
            assertThrows(IOException.class, () -> LocalSoundTrack.read(new ByteArrayInputStream(invalid), 1000));
        }
        assertThrows(IOException.class, () -> read(new Fixture(1).context(0, 0, 42, 3).end(10)));
        assertThrows(IOException.class, () -> read(new Fixture(127).context(0, -1, 42, 127).end(10)));
        assertThrows(IOException.class, () -> read(new Fixture(127).event(10, 0, 42, 1).end(20)));
        assertThrows(IOException.class, () -> read(new Fixture(127).context(0, 0, 42, 127).event(10, 1, 42, 1).end(20)));
        assertThrows(IOException.class, () -> read(new Fixture(127).context(0, 0, 42, 127).event(10, 0, 7, 1).end(20)));
        assertThrows(IOException.class, () -> read(new Fixture(127).context(20, 0, 42, 127).event(10, 0, 42, 1).end(30)));
        assertThrows(IOException.class, () -> read(new Fixture(127).context(0, 0, 42, 127).event(10, 0, 42, 3).end(20)));
        assertThrows(IOException.class, () -> read(new Fixture(127).context(0, 0, 42, 127).event(10, 0, 42, 1).end(9)));
        assertThrows(IOException.class, () -> LocalSoundTrack.read(new ByteArrayInputStream(bytes), 99));
        byte[] bad = bytes.clone(); bad[12] = 9;
        assertThrows(IOException.class, () -> LocalSoundTrack.read(new ByteArrayInputStream(bad), 1000));
    }

    @Test void malformedPositionsVolumesPitchCategoryAndIdentifierCannotReachSoundEngine() throws Exception {
        byte[] bytes = normal().bytes();
        // Header + context + event type/time/world/entity.
        int payload = 12 + 17 + 13;
        for (int kind = 0; kind < 7; kind++) {
            byte[] invalid = bytes.clone(); ByteBuffer data = ByteBuffer.wrap(invalid);
            switch (kind) {
                case 0: invalid[payload + 1] = 10; break;
                case 1: data.putFloat(payload + 2, Float.NaN); break;
                case 2: data.putFloat(payload + 6, 0); break;
                case 3: data.putDouble(payload + 10, Double.POSITIVE_INFINITY); break;
                case 4: data.putShort(payload + 34, (short) 257); break;
                case 5: invalid[payload + 36] = 'A'; break;
                case 6: invalid[payload] = (byte) 128; break;
                default: fail();
            }
            assertThrows(IOException.class, () -> LocalSoundTrack.read(new ByteArrayInputStream(invalid), 1000));
        }
    }

    @Test void closeDisablesAuthorityAndDiskReads() throws Exception {
        LocalSoundTrack track = read(normal()); track.close(); track.close();
        assertEquals(0, track.coverage(50, 0, 42));
        assertThrows(IOException.class, () -> track.event(0)); assertThrows(IOException.class, () -> track.after(0));
    }

    @Test void unsupportedCustomAndWorldSoundsNeverDisablePlayerSoundReconstruction() {
        assertTrue(LocalSoundTrack.playerSoundSupported(1, "minecraft:block.stone.step"));
        assertTrue(LocalSoundTrack.playerSoundSupported(16, "minecraft:entity.generic.eat"));
        assertTrue(LocalSoundTrack.playerSoundSupported(64, "minecraft:block.stone.fall"));
        assertFalse(LocalSoundTrack.playerSoundSupported(1, "custom:block.stone.step"));
        assertFalse(LocalSoundTrack.playerSoundSupported(16, "custom:player.consume"));
        assertFalse(LocalSoundTrack.playerSoundSupported(64, "custom:soft_landing"));
        assertFalse(LocalSoundTrack.playerSoundSupported(32, "minecraft:block.chest.open"));
        assertFalse(LocalSoundTrack.playerSoundSupported(16, "minecraft:ui.button.click"));
    }

    static Fixture normal() throws IOException {
        return new Fixture(127).context(0, 0, 42, 127).event(50, 0, 42, 1).event(100, 0, 42, 2).event(150, 0, 42, 4).end(1000);
    }
    static LocalSoundTrack read(Fixture fixture) throws IOException { return LocalSoundTrack.read(new ByteArrayInputStream(fixture.bytes()), 10000); }
    static final class Fixture {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream data = new DataOutputStream(bytes);
        Fixture(int supported) throws IOException { data.writeInt(0x5A534E44); data.writeInt(1); data.writeInt(supported); }
        Fixture context(int time, int world, int entity, int mask) throws IOException {
            data.writeByte(1); data.writeInt(time); data.writeInt(world); data.writeInt(entity); data.writeInt(mask); return this;
        }
        Fixture event(int time, int world, int entity, int channel) throws IOException {
            data.writeByte(2); data.writeInt(time); data.writeInt(world); data.writeInt(entity);
            data.writeByte(channel); data.writeByte(7); data.writeFloat(0.15F); data.writeFloat(1.25F);
            data.writeDouble(12.125); data.writeDouble(64); data.writeDouble(-90.75); data.writeUTF("minecraft:block.stone.step"); return this;
        }
        Fixture end(int time) throws IOException { data.writeByte(0); data.writeInt(time); return this; }
        byte[] bytes() { return bytes.toByteArray(); }
    }
}
