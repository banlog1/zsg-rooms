package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleWebSocketClientTest {
    @Test
    public void websocketHandshakeAndMaskedTextFramesRoundTrip() throws Exception {
        ServerSocket server = new ServerSocket(0);
        CompletableFuture<String> clientMessage = new CompletableFuture<String>();
        Thread serverThread = new Thread(() -> runServer(server, clientMessage));
        serverThread.setDaemon(true);
        serverThread.start();

        CountDownLatch messageReceived = new CountDownLatch(1);
        AtomicReference<String> serverMessage = new AtomicReference<String>();
        SimpleWebSocketClient client = new SimpleWebSocketClient(
                new URI("ws://127.0.0.1:" + server.getLocalPort() + "/room/TEST"),
                "Bearer test-token",
                new SimpleWebSocketClient.Listener() {
                    @Override
                    public void onText(SimpleWebSocketClient source, String message) {
                        serverMessage.set(message);
                        messageReceived.countDown();
                    }

                    @Override
                    public void onClosed(SimpleWebSocketClient source, String reason) {
                    }
                }
        );

        client.connect(3000);
        client.sendText("hello relay");

        assertTrue(messageReceived.await(3, TimeUnit.SECONDS));
        assertEquals("welcome", serverMessage.get());
        assertEquals("hello relay", clientMessage.get(3, TimeUnit.SECONDS));
        client.close();
        server.close();
    }

    private void runServer(ServerSocket server, CompletableFuture<String> clientMessage) {
        try (Socket socket = server.accept()) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            String request = readHeaders(input);
            String key = header(request, "Sec-WebSocket-Key");
            String accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)
                    )
            );
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            output.write(response.getBytes(StandardCharsets.US_ASCII));
            writeServerText(output, "welcome");
            output.flush();
            clientMessage.complete(readClientText(input));
        } catch (Exception exception) {
            clientMessage.completeExceptionally(exception);
        }
    }

    private String readHeaders(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int value = input.read();
            if (value < 0) {
                throw new IllegalStateException("Client closed during handshake");
            }
            bytes.write(value);
            if ((matched == 0 || matched == 2) && value == '\r') {
                matched += 1;
            } else if ((matched == 1 || matched == 3) && value == '\n') {
                matched += 1;
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
    }

    private String header(String request, String name) {
        for (String line : request.split("\r\n")) {
            if (line.toLowerCase().startsWith(name.toLowerCase() + ":")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return "";
    }

    private void writeServerText(OutputStream output, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        output.write(0x81);
        output.write(payload.length);
        output.write(payload);
    }

    private String readClientText(InputStream input) throws Exception {
        int first = input.read();
        int second = input.read();
        if ((first & 0x0F) != 0x1 || (second & 0x80) == 0) {
            throw new IllegalStateException("Expected a masked client text frame");
        }
        int length = second & 0x7F;
        byte[] mask = readExactly(input, 4);
        byte[] payload = readExactly(input, length);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    private byte[] readExactly(InputStream input, int length) throws Exception {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(bytes, offset, length - offset);
            if (count < 0) {
                throw new IllegalStateException("Unexpected end of stream");
            }
            offset += count;
        }
        return bytes;
    }
}
