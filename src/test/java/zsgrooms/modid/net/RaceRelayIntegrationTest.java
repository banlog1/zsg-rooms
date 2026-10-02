package zsgrooms.modid.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zsgrooms.modid.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.URI;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real mod host transport and relay, with a wire-level guest; no Minecraft worlds required. */
class RaceRelayIntegrationTest {
    private String endpoint, roomName;
    private InGame game;
    private Guest guest;
    private boolean started;

    @BeforeEach void setup() throws Exception {
        endpoint = System.getProperty("zsgrooms.testRelay");
        assumeTrue(endpoint != null, "Run raceRelayTest with a local relay");
        assertEquals("127.0.0.1", URI.create(endpoint).getHost(), "Tests must never use the production relay");
        roomName = "RACE-" + UUID.randomUUID();
        Room room = new Room(roomName, "12345|structure:manual", new Player("Host", true, true), 4);
        room.addPlayer(new Player("Guest", true, false));
        game = new InGame(room.seed, roomName, InGame.SeedType.FIXED, false);
        game.setFinishGoal(3);
        game.setFinisherLimit(2);
        TournamentSettings settings = new TournamentSettings();
        settings.enabled = true;
        settings.bracket = false;
        settings.rounds = 2;
        settings.order = Arrays.asList("Host", "Guest");
        game.setTournamentSettings(settings);
        assertTrue(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()));
        game = ZsgRooms.getGame(roomName);
        game.targetStructure = "manual:12345";
        game.setTournament(new Tournament(settings));
        game.startGame();
        RoomSequenceHost.assignSequence(ZsgRooms.getRoom(roomName), game,
                Arrays.asList(room.seed, "67890|structure:manual", "98765|structure:manual"));
        started = true;
        assertTrue(RoomWebSocketTransport.host(endpoint, roomName, "Host"), RoomWebSocketTransport.getStatus());
        guest = new Guest();
        guest.connect();
        guest.snapshot(s -> s.sequence != null);
    }

    @AfterEach void cleanup() {
        if (guest != null) guest.close();
        if (started) RoomWebSocketTransport.stop();
    }

    @Test void hostReconnectPreservesPrivateSeedsAndQueuedReadiness() throws Exception {
        Object previousConnection = hostConnection();
        drop(hostSocket());
        guest.message(m -> "relay_status".equals(m.get("type")) && m.get("value").contains("60 seconds"));
        assertTrue(RoomWebSocketTransport.sendAction("world_ready", roomName, "Host", game.seed));
        // An open guest socket can send while the host is offline; the relay drops that message.
        guest.send("world_ready", game.seed);
        guest.message(m -> "relay_status".equals(m.get("type")) && m.get("value").contains("reconnected"));
        guest.snapshot(s -> s.readyPlayers.contains("Host"));
        assertSame(previousConnection, hostConnection());
        assertSame(game, ZsgRooms.getGame(roomName));
        assertEquals("98765|structure:manual", game.getSequence().seed(2));
        // Retry the lost ready notification, as a waiting client must do after interruption.
        guest.send("world_ready", game.seed);
        RoomSnapshot released = guest.snapshot(s -> s.synchronizedStartReleased);
        assertTrue(released.sequence.active("Guest"));
        assertNull(released.sequence.seed(1), "Future seeds leaked to guest");
        assertTrue(game.hostRaceElapsed() >= 0);
    }

    @Test void guestReconnectDuringTransitionKeepsStageAndDuplicateReportsAreIdempotent() throws Exception {
        ready();
        String report = RaceSequence.report(game.getRaceId(), 0, 10_000_000_000L, "finish");
        guest.send("complete_run", report);
        guest.snapshot(s -> s.sequence.runner("Guest").stage == 1);
        drop(guest.socket);
        Thread.sleep(300);
        guest.connect();
        RoomSnapshot restored = guest.snapshot(s -> s.sequence.runner("Guest").stage == 1);
        assertFalse(restored.tournament.withdrawn.contains("Guest"));
        assertEquals("67890|structure:manual", restored.sequence.seed(1));
        guest.send("complete_run", report);
        guest.snapshot(s -> s.sequence.runner("Guest").stage == 1);
        assertEquals(1, game.getSequence().runner("Guest").stage);
        assertTrue(game.getSequence().active("Guest"));
        guest.send("forfeit", RaceSequence.report(game.getRaceId(), 1, 20_000_000_000L, "skip"));
        guest.snapshot(s -> s.sequence.runner("Guest").stage == 2);
        guest.send("forfeit", RaceSequence.report(game.getRaceId(), 1, 20_000_000_000L, "skip"));
        guest.snapshot(s -> s.sequence.runner("Guest").stage == 2);
        assertEquals(1, game.getSequence().runner("Guest").skipped);
        assertEquals(RaceSequence.SKIP_NANOS, game.getSequence().runner("Guest").penaltyNanos());
    }

    @Test void hostReconnectAtFinishFlushesQueuedReportAndScoresExactlyOnce() throws Exception {
        ready();
        for (int stage = 0; stage < 2; stage++) {
            hostReport(stage, stage + 1);
            guest.send("complete_run", RaceSequence.report(game.getRaceId(), stage, (stage + 2) * 1_000_000_000L, "finish"));
            final int reached = stage + 1;
            guest.snapshot(s -> s.sequence.runner("Guest").stage == reached);
        }
        drop(hostSocket());
        guest.message(m -> "relay_status".equals(m.get("type")) && m.get("value").contains("60 seconds"));
        hostReport(2, 3);
        String report = RaceSequence.report(game.getRaceId(), 2, 4_000_000_000L, "finish");
        guest.send("complete_run", report);
        guest.message(m -> "relay_status".equals(m.get("type")) && m.get("value").contains("reconnected"));
        guest.snapshot(s -> s.sequence.runner("Host").finished);
        guest.send("complete_run", report);
        RoomSnapshot completed = guest.snapshot(s -> !s.inGame && s.tournament.completedRounds == 1);
        assertEquals(Integer.valueOf(10), completed.tournament.scores.get("Host"));
        assertEquals(Integer.valueOf(6), completed.tournament.scores.get("Guest"));
        guest.send("complete_run", report);
        completed = guest.snapshot(s -> s.tournament.completedRounds == 1);
        assertEquals(Integer.valueOf(6), completed.tournament.scores.get("Guest"));
        assertFalse(game.getTournament().record(game.getSequence()));
    }

    private void ready() throws Exception {
        assertTrue(RoomWebSocketTransport.sendAction("world_ready", roomName, "Host", game.seed));
        guest.send("world_ready", game.seed);
        guest.snapshot(s -> s.synchronizedStartReleased);
    }

    private void hostReport(int stage, long seconds) {
        assertTrue(RoomWebSocketTransport.sendAction("complete_run", roomName, "Host",
                RaceSequence.report(game.getRaceId(), stage, seconds * 1_000_000_000L, "finish")));
    }

    private static Object field(Object object, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object hostConnection() throws Exception { return field(null, RoomWebSocketTransport.class, "connection"); }
    private static SimpleWebSocketClient hostSocket() throws Exception {
        Object connection = hostConnection();
        return (SimpleWebSocketClient) field(connection, connection.getClass(), "socket");
    }
    private static void drop(SimpleWebSocketClient socket) throws Exception {
        Socket tcp = (Socket) field(socket, SimpleWebSocketClient.class, "socket");
        tcp.setSoLinger(true, 0);
        tcp.close();
    }

    private final class Guest implements SimpleWebSocketClient.Listener {
        private final LinkedBlockingQueue<Map<String, String>> messages = new LinkedBlockingQueue<>();
        private SimpleWebSocketClient socket;
        void connect() throws Exception {
            messages.clear();
            socket = new SimpleWebSocketClient(URI.create(endpoint.replace("http://", "ws://")
                    + "/room/" + roomName + "?role=guest&player=Guest"), "", this);
            socket.connect(5000);
            message(m -> "welcome".equals(m.get("type")));
        }
        void send(String type, String value) throws Exception { socket.sendText(RoomProtocol.encode(type, roomName, "Spoofed", value)); }
        @Override public void onText(SimpleWebSocketClient source, String value) { messages.add(RoomProtocol.decode(value)); }
        @Override public void onClosed(SimpleWebSocketClient source, String reason) { }
        Map<String, String> message(Predicate<Map<String, String>> match) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < deadline) {
                Map<String, String> message = messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (message != null && match.test(message)) return message;
            }
            throw new AssertionError("Timed out waiting for relay message: " + RoomWebSocketTransport.getStatus());
        }
        RoomSnapshot snapshot(Predicate<RoomSnapshot> match) throws Exception {
            return RoomSnapshot.fromJson(message(m -> "snapshot".equals(m.get("type"))
                    && RoomSnapshot.fromJson(m.get("value")) != null && match.test(RoomSnapshot.fromJson(m.get("value")))).get("value"));
        }
        void close() { if (socket != null) socket.close(); }
    }
}
