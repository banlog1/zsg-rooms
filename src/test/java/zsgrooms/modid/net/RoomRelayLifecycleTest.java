package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RoomRelayLifecycleTest {
    @Test void normalStopSendsClose1000ForActivatedHostAndGuest() throws Exception {
        for (boolean host : new boolean[]{true, false}) {
            try (Session session = new Session(host)) {
                assertTrue(session.attempt.activate());
                RoomWebSocketTransport.stop();
                session.assertNormalClose();
                assertFalse(RoomWebSocketTransport.isActive());
                assertNull(field(session.relay, "reconnectThread"));
            }
        }
    }

    @Test void normalStopAlsoClosesLegacyEstablishedSessionsGracefully() throws Exception {
        try (Session session = new Session(false)) {
            // Legacy open() enables reconnect without a ConnectionAttempt handoff.
            setField(session.relay, "reconnectEnabled", true);
            RoomWebSocketTransport.stop();
            session.assertNormalClose();
        }
    }

    @Test void cancellationBeforeActivationDoesNotWaitForTheSocketWriter() throws Exception {
        try (Session session = new Session(false)) {
            CountDownLatch writerHeld = new CountDownLatch(1);
            CountDownLatch releaseWriter = new CountDownLatch(1);
            Thread writer = new Thread(() -> {
                synchronized (session.socket) {
                    writerHeld.countDown();
                    try { releaseWriter.await(); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
            }, "Pending relay writer test");
            writer.setDaemon(true);
            writer.start();
            try {
                assertTrue(writerHeld.await(3, TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1), session.attempt::cancel);
                assertTrue(session.rawSocket.isClosed());
                assertEquals(0, session.frames.size(), "Pending cancellation sent a close frame");
                assertFalse(session.attempt.activate());
                assertFalse(RoomWebSocketTransport.isActive());
            } finally {
                releaseWriter.countDown();
                writer.join(3000);
            }
        }
    }

    @Test void activationRejectsASocketThatClosedBeforeTheHandoff() throws Exception {
        try (Session session = new Session(false)) {
            session.socket.abort();
            assertFalse(session.attempt.activate());
            assertEquals(false, field(session.relay, "reconnectEnabled"));
            assertNull(field(session.relay, "reconnectThread"));
        }
    }

    @Test void disconnectBetweenOpenCheckAndActivationAlwaysStartsReconnect() throws Exception {
        try (Session session = new Session(false)) {
            CountDownLatch callbackDone = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<Thread> callbackThread = new AtomicReference<>();
            session.socket.onOpenCheck = () -> {
                try {
                    setField(session.socket, SimpleWebSocketClient.class, "running", false);
                    Thread callback = new Thread(() -> {
                        try {
                            ((SimpleWebSocketClient.Listener) session.relay).onClosed(session.socket, "Forced disconnect");
                        } catch (Throwable error) {
                            failure.set(error);
                        } finally {
                            callbackDone.countDown();
                        }
                    }, "Relay activation close test");
                    callback.setDaemon(true);
                    callbackThread.set(callback);
                    callback.start();
                    // Old code let onClosed finish here before reconnect was enabled. The fixed
                    // code holds the connection monitor, so onClosed must wait for activation.
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                    while (callbackDone.getCount() != 0 && callback.getState() != Thread.State.BLOCKED
                            && System.nanoTime() < deadline) {
                        Thread.sleep(1);
                    }
                    assertTrue(callbackDone.getCount() == 0 || callback.getState() == Thread.State.BLOCKED);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            };
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertTrue(session.attempt.activate()));
                assertTrue(callbackDone.await(3, TimeUnit.SECONDS), "Close callback deadlocked during activation");
                assertNull(failure.get());
                assertNotNull(field(session.relay, "reconnectThread"), "Activation lost the disconnect notification");
                assertTrue(RoomWebSocketTransport.hasRelayRoom("TEST1234"));
                assertFalse(RoomWebSocketTransport.isActive());
            } finally {
                Thread callback = callbackThread.get();
                if (callback != null) callback.join(3000);
            }
        }
    }

    @Test void socketCloseRunsOutsideTheConnectionMonitor() throws Exception {
        try (Session session = new Session(false)) {
            assertTrue(session.attempt.activate());
            session.socket.onClose = () -> assertFalse(Thread.holdsLock(session.relay));
            RoomWebSocketTransport.stop();
            session.assertNormalClose();
        }
    }

    /** In-memory socket output exercises real close frames without contacting any relay. */
    private static final class Session implements AutoCloseable {
        final Object relay;
        final ProbeSocket socket;
        final Socket rawSocket = new Socket();
        final ByteArrayOutputStream frames = new ByteArrayOutputStream();
        final RoomWebSocketTransport.ConnectionAttempt attempt;
        final Path config;
        final byte[] previousConfig;
        final String previousUrl;

        Session(boolean host) throws Exception {
            RoomWebSocketTransport.stop();
            config = (Path) staticField("RELAY_CONFIG").get(null);
            previousConfig = Files.exists(config) ? Files.readAllBytes(config) : null;
            previousUrl = RoomWebSocketTransport.getRelayUrl();
            Class<?> relayType = Class.forName(RoomWebSocketTransport.class.getName() + "$RelayConnection");
            Constructor<?> constructor = relayType.getDeclaredConstructor(String.class, String.class, String.class, boolean.class);
            constructor.setAccessible(true);
            relay = constructor.newInstance("http://127.0.0.1:1", "TEST1234", host ? "Host" : "Guest", host);
            socket = new ProbeSocket((SimpleWebSocketClient.Listener) relay);
            setField(socket, SimpleWebSocketClient.class, "socket", rawSocket);
            setField(socket, SimpleWebSocketClient.class, "output", frames);
            setField(socket, SimpleWebSocketClient.class, "running", true);
            setField(relay, "socket", socket);
            setField(relay, "welcomed", true);
            staticField("connection").set(null, relay);
            Constructor<?> attemptConstructor = RoomWebSocketTransport.ConnectionAttempt.class.getDeclaredConstructor(relayType, String.class);
            attemptConstructor.setAccessible(true);
            attempt = (RoomWebSocketTransport.ConnectionAttempt) attemptConstructor.newInstance(relay, previousUrl);
            setField(attempt, "connected", true);
            setField(attempt, "done", true);
        }

        void assertNormalClose() {
            byte[] frame = frames.toByteArray();
            assertEquals(8, frame.length);
            assertEquals(0x88, frame[0] & 0xFF);
            assertEquals(0x82, frame[1] & 0xFF);
            int code = (((frame[6] ^ frame[2]) & 0xFF) << 8) | ((frame[7] ^ frame[3]) & 0xFF);
            assertEquals(1000, code);
            assertTrue(rawSocket.isClosed());
        }

        @Override public void close() throws Exception {
            Thread reconnect = (Thread) field(relay, "reconnectThread");
            try {
                RoomWebSocketTransport.stop();
                socket.abort();
                if (reconnect != null) {
                    reconnect.join(3000);
                    assertFalse(reconnect.isAlive(), "Reconnect worker survived shutdown");
                }
            } finally {
                staticField("relayUrl").set(null, previousUrl);
                if (previousConfig == null) Files.deleteIfExists(config);
                else Files.write(config, previousConfig);
            }
        }
    }

    private static final class ProbeSocket extends SimpleWebSocketClient {
        Runnable onOpenCheck;
        Runnable onClose;

        ProbeSocket(Listener listener) {
            super(URI.create("ws://127.0.0.1:1/room/TEST1234"), "", listener);
        }

        @Override public boolean isOpen() {
            boolean open = super.isOpen();
            Runnable callback = onOpenCheck;
            onOpenCheck = null;
            if (callback != null) callback.run();
            return open;
        }

        @Override public void close() {
            if (onClose != null) onClose.run();
            super.close();
        }
    }

    private static Field staticField(String name) throws Exception {
        Field field = RoomWebSocketTransport.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        setField(owner, owner.getClass(), name, value);
    }

    private static void setField(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
