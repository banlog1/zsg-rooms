package zsgrooms.modid.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RoomConnectionAttemptTest {
    @AfterEach void stop() { RoomWebSocketTransport.stop(); }

    @Test void pendingHandshakeDoesNotBlockClientQueriesAndCanBeCancelled() throws Exception {
        try (Relay relay = new Relay(false, true)) {
            RoomWebSocketTransport.ConnectionAttempt attempt = assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> RoomWebSocketTransport.beginConnection(relay.endpoint(), "TEST1234", "Guest", false));
            assertTrue(relay.request.await(3, TimeUnit.SECONDS));
            assertFalse(attempt.isDone());
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                RoomWebSocketTransport.getStatus(); RoomWebSocketTransport.isActive(); attempt.cancel();
            });
            awaitDone(attempt);
            assertFalse(attempt.isConnected());
            assertFalse(attempt.activate());
            assertFalse(RoomWebSocketTransport.isActive());
        }
    }

    @Test void cancelledAttemptCannotReplaceTheNextConnection() throws Exception {
        try (Relay old = new Relay(false, true); Relay next = new Relay(false, false)) {
            RoomWebSocketTransport.ConnectionAttempt first = RoomWebSocketTransport.beginConnection(old.endpoint(), "TEST1234", "Guest", false);
            assertTrue(old.request.await(3, TimeUnit.SECONDS)); first.cancel();
            RoomWebSocketTransport.ConnectionAttempt second = RoomWebSocketTransport.beginConnection(next.endpoint(), "TEST5678", "Guest", false);
            old.release.countDown();
            awaitDone(first); awaitDone(second);
            assertFalse(first.isConnected()); assertFalse(first.activate()); assertTrue(second.isConnected(), second.error());
            first.cancel();
            assertTrue(second.isConnected(), "Old cancellation disconnected a new attempt");
        }
    }

    @Test void httpFailureIsReportedAndDoesNotLeaveAnActiveTransport() throws Exception {
        try (Relay relay = new Relay(true, false)) {
            RoomWebSocketTransport.ConnectionAttempt attempt = RoomWebSocketTransport.beginConnection(relay.endpoint(), "TEST1234", "Guest", false);
            awaitDone(attempt);
            assertFalse(attempt.isConnected());
            assertTrue(attempt.error().contains("Room host is offline"), attempt.error());
            assertFalse(RoomWebSocketTransport.isActive());
        }
    }

    @Test void invalidAddressDoesNotReplaceAnExistingConnection() throws Exception {
        try (Relay relay = new Relay(false, false)) {
            RoomWebSocketTransport.ConnectionAttempt connected = RoomWebSocketTransport.beginConnection(relay.endpoint(), "TEST1234", "Guest", false);
            awaitDone(connected); assertTrue(connected.isConnected(), connected.error());
            RoomWebSocketTransport.ConnectionAttempt invalid = RoomWebSocketTransport.beginConnection("https://", "TEST1234", "Guest", false);
            assertTrue(invalid.isDone()); assertFalse(invalid.isConnected()); assertTrue(connected.isConnected());
        }
    }

    @Test void validatesAddressesWithoutLosingLocalInsecureScheme() {
        assertEquals("https://example.com", RoomWebSocketTransport.validateRelayUrl(" example.com/ "));
        assertEquals("http://127.0.0.1:1234", RoomWebSocketTransport.validateRelayUrl("http://127.0.0.1:1234"));
        assertEquals("https://example.com", RoomWebSocketTransport.validateRelayUrl("HTTPS://example.com/"));
        for (String address : new String[]{"", "https://", "https://user:pass@example.com", "https://host/?key=private", "https://host/#fragment", "file:///tmp/relay", "http://host:99999", "http://host:0"})
            assertThrows(IllegalArgumentException.class, () -> RoomWebSocketTransport.validateRelayUrl(address), address);
    }

    private static void awaitDone(RoomWebSocketTransport.ConnectionAttempt attempt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!attempt.isDone() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(attempt.isDone(), "Handshake did not finish");
    }

    /** Minimal local protocol peer: no live service, cloud storage or seed-bank calls. */
    private static final class Relay implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        final CountDownLatch request = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicReference<Socket> accepted = new AtomicReference<>();
        final Thread thread;

        Relay(boolean reject, boolean delayed) throws Exception {
            if (!delayed) release.countDown();
            thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    accepted.set(socket);
                    socket.setSoTimeout(5000);
                    String headers = headers(socket.getInputStream());
                    request.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    if (reject) {
                        String body = "Room host is offline";
                        socket.getOutputStream().write(("HTTP/1.1 404 Not Found\r\nContent-Length: " + body.length() + "\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII));
                    } else {
                        String key = "";
                        for (String line : headers.split("\r\n")) if (line.startsWith("Sec-WebSocket-Key:")) key = line.substring(line.indexOf(':') + 1).trim();
                        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                        socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        byte[] welcome = "{\"type\":\"welcome\"}".getBytes(StandardCharsets.UTF_8);
                        socket.getOutputStream().write(0x81); socket.getOutputStream().write(welcome.length); socket.getOutputStream().write(welcome);
                        socket.getOutputStream().flush();
                        while (socket.getInputStream().read() != -1) { }
                    }
                } catch (Exception ignored) { }
            }, "Local room handshake test");
            thread.setDaemon(true); thread.start();
        }
        String endpoint() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception {
            release.countDown(); server.close();
            Socket socket = accepted.get(); if (socket != null) socket.close();
            thread.join(1000);
        }
        private static String headers(InputStream input) throws Exception {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int match = 0;
            while (match < 4) {
                int b = input.read(); if (b < 0) throw new java.io.EOFException(); bytes.write(b);
                if ((match == 0 || match == 2) && b == '\r' || (match == 1 || match == 3) && b == '\n') match++;
                else match = b == '\r' ? 1 : 0;
            }
            return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
        }
    }
}
