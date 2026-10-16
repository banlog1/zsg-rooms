// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MilestonePacketTrackerTest {
    static final int ADVANCEMENT_PACKET_ID = 0x57;

    @Test void eachCurrentKindCompletesFromItsWireDefinitionIncludingDragon() throws Exception {
        for (Milestones.Kind kind : Milestones.Kind.values()) {
            MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
            deliver(tracker, 10, payload(kind.id, true, true, false, false));
            assertTrue(tracker.snapshot().isEmpty());
            deliver(tracker, 20, payload(kind.id, false, false, false, true));
            List<Milestones.Entry> result = tracker.snapshot();
            assertEquals(1, result.size(), kind.id);
            assertSame(kind, result.get(0).kind);
            assertEquals(20, result.get(0).time);
            assertEquals(1, result.get(0).world);
        }
    }

    @Test void alreadyCompletedInitialSnapshotDoesNotBecomeALaterSplit() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        deliver(tracker, 10, payload(Milestones.Kind.NETHER.id, true, true, false, true));
        deliver(tracker, 20, payload(Milestones.Kind.NETHER.id, false, false, false, true));
        assertTrue(tracker.snapshot().isEmpty());
    }

    @Test void unknownDefinitionAndUnknownKindAreIgnored() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        deliver(tracker, 10, payload(Milestones.Kind.END.id, false, false, false, true));
        deliver(tracker, 20, payload("minecraft:adventure/shoot_arrow", false, true, false, true));
        assertTrue(tracker.snapshot().isEmpty());
    }

    @Test void revokedProgressDoesNotDuplicateACompletion() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        deliver(tracker, 10, payload(Milestones.Kind.BASTION.id, false, true, false, true));
        deliver(tracker, 20, payload(Milestones.Kind.BASTION.id, false, false, false, false));
        deliver(tracker, 30, payload(Milestones.Kind.BASTION.id, false, false, false, true));
        assertEquals(1, tracker.snapshot().size());
        assertEquals(10, tracker.snapshot().get(0).time);
    }

    @Test void removedDefinitionAndClearSnapshotStopUsingStaleRequirements() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        String id = Milestones.Kind.FORTRESS.id;
        deliver(tracker, 10, payload(id, false, true, false, false));
        deliver(tracker, 20, payload(id, false, false, true, true));
        assertTrue(tracker.snapshot().isEmpty());
        deliver(tracker, 30, payload(id, false, true, false, false));
        deliver(tracker, 40, payload(id, true, false, false, null));
        deliver(tracker, 50, payload(id, false, false, false, true));
        assertTrue(tracker.snapshot().isEmpty());
        deliver(tracker, 60, payload(id, false, true, false, true));
        assertEquals(60, tracker.snapshot().get(0).time);
    }

    @Test void joinGameClearsDefinitionsAndAllowsTheNextWorldToCompleteAgain() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        join(tracker, 0);
        deliver(tracker, 10, payload(Milestones.Kind.NETHER.id, false, true, false, true));
        List<Milestones.Entry> firstWorld = tracker.snapshot();
        join(tracker, 20);
        deliver(tracker, 30, payload(Milestones.Kind.NETHER.id, false, false, false, true));
        assertEquals(1, tracker.snapshot().size());
        deliver(tracker, 40, payload(Milestones.Kind.NETHER.id, false, true, false, true));
        assertEquals(2, tracker.snapshot().size());
        assertEquals(2, tracker.snapshot().get(1).world);
        assertEquals(40, tracker.snapshot().get(1).time);
        assertEquals(1, firstWorld.size(), "Earlier snapshots must not mutate");
        assertThrows(UnsupportedOperationException.class, () -> firstWorld.clear());
    }

    @Test void unrelatedPacketAndWrongNetworkPhaseAreNotDecoded() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        for (State state : new State[]{State.LOGIN, State.PLAY}) {
            ByteBuf bytes = Unpooled.wrappedBuffer(new byte[]{(byte) 255});
            PacketData data = new PacketData(10, new Packet(registry(state),
                    state == State.LOGIN ? ADVANCEMENT_PACKET_ID : ADVANCEMENT_PACKET_ID + 1, bytes));
            try { tracker.packet(data); }
            finally { data.release(); }
            assertEquals(0, bytes.refCnt());
        }
        assertTrue(tracker.snapshot().isEmpty());
    }

    @Test void packetIsBorrowedWithoutMovingItsReaderIndexOrRetainingIt() throws Exception {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        ByteBuf bytes = Unpooled.buffer();
        bytes.writeByte(123);
        bytes.writeBytes(payload(Milestones.Kind.END.id, false, true, false, true));
        bytes.readerIndex(1);
        PacketData data = new PacketData(10, new Packet(registry(State.PLAY), ADVANCEMENT_PACKET_ID, bytes));
        try {
            tracker.packet(data);
            assertEquals(1, bytes.readerIndex());
            assertEquals(1, bytes.refCnt());
            assertEquals(1, tracker.snapshot().size());
        } finally { data.release(); }
        assertEquals(0, bytes.refCnt());
    }

    @Test void malformedAdvancementLeavesPacketOwnershipWithTheCaller() {
        MilestonePacketTracker tracker = new MilestonePacketTracker(ADVANCEMENT_PACKET_ID);
        ByteBuf bytes = Unpooled.wrappedBuffer(new byte[]{1});
        PacketData data = new PacketData(10, new Packet(registry(State.PLAY), ADVANCEMENT_PACKET_ID, bytes));
        try {
            assertThrows(Exception.class, () -> tracker.packet(data));
            assertEquals(1, bytes.refCnt());
            assertEquals(0, bytes.readerIndex());
            assertTrue(tracker.snapshot().isEmpty());
        } finally { data.release(); }
        assertEquals(0, bytes.refCnt());
    }

    private static void deliver(MilestonePacketTracker tracker, int time, byte[] payload) throws IOException {
        ByteBuf bytes = Unpooled.wrappedBuffer(payload);
        PacketData data = new PacketData(time, new Packet(registry(State.PLAY), ADVANCEMENT_PACKET_ID, bytes));
        try { tracker.packet(data); }
        finally { data.release(); }
        assertEquals(0, bytes.refCnt());
    }

    private static void join(MilestonePacketTracker tracker, int time) throws IOException {
        // The tracker only needs the type; a full game/world bootstrap is deliberately unnecessary here.
        PacketData data = new PacketData(time, new Packet(registry(State.PLAY), PacketType.JoinGame));
        try { tracker.packet(data); }
        finally { data.release(); }
    }

    private static PacketTypeRegistry registry(State state) {
        return PacketTypeRegistry.get(ProtocolVersion.v1_16_1, state);
    }

    /** Minimal vanilla advancement wire payload; the parent is absent from the update on purpose. */
    static byte[] payload(String id, boolean clear, boolean definition, boolean remove, Boolean done) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBoolean(clear);
            varInt(out, definition ? 1 : 0);
            if (definition) {
                string(out, id);
                out.writeBoolean(true);
                string(out, "minecraft:missing_parent");
                out.writeBoolean(false); // No display data or item registry access.
                varInt(out, 1);
                string(out, "entered");
                varInt(out, 1);
                varInt(out, 1);
                string(out, "entered");
            }
            varInt(out, remove ? 1 : 0);
            if (remove) string(out, id);
            varInt(out, done == null ? 0 : 1);
            if (done != null) {
                string(out, id);
                varInt(out, 1);
                string(out, "entered");
                out.writeBoolean(done);
                if (done) out.writeLong(123456789L);
            }
        }
        return bytes.toByteArray();
    }

    static void varInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~127) != 0) {
            out.writeByte((value & 127) | 128);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        varInt(out, bytes.length);
        out.write(bytes);
    }
}
