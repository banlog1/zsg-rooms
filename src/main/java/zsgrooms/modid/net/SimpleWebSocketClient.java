package zsgrooms.modid.net;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleWebSocketClient {
    public interface Listener {
        void onText(String message);

        void onClosed(String reason);
    }

    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;

    private final URI uri;
    private final String authorization;
    private final Listener listener;
    private final SecureRandom random;
    private final AtomicBoolean closeNotified;
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private volatile boolean running;
    private Thread heartbeatThread;

    public SimpleWebSocketClient(URI uri, String authorization, Listener listener) {
        this.uri = uri;
        this.authorization = authorization;
        this.listener = listener;
        this.random = new SecureRandom();
        this.closeNotified = new AtomicBoolean(false);
    }

    public void connect(int timeoutMillis) throws IOException {
        String scheme = this.uri.getScheme() == null ? "" : this.uri.getScheme().toLowerCase(Locale.ROOT);
        boolean secure = "wss".equals(scheme);
        if (!secure && !"ws".equals(scheme)) {
            throw new IOException("Relay URL must use https/wss or http/ws");
        }
        String host = this.uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IOException("Relay URL has no host");
        }
        int port = this.uri.getPort() > 0 ? this.uri.getPort() : (secure ? 443 : 80);

        Socket tcpSocket = new Socket();
        tcpSocket.connect(new InetSocketAddress(host, port), timeoutMillis);
        tcpSocket.setTcpNoDelay(true);
        tcpSocket.setSoTimeout(timeoutMillis);

        if (secure) {
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(tcpSocket, host, port, true);
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
            sslSocket.startHandshake();
            this.socket = sslSocket;
        } else {
            this.socket = tcpSocket;
        }

        this.input = this.socket.getInputStream();
        this.output = this.socket.getOutputStream();
        performUpgrade(host, port, secure);
        this.socket.setSoTimeout(0);
        this.running = true;

        Thread reader = new Thread(this::readLoop, "ZSG Room WebSocket");
        reader.setDaemon(true);
        reader.start();
        startHeartbeat();
    }

    public boolean isOpen() {
        return this.running && this.socket != null && !this.socket.isClosed();
    }

    public synchronized void sendText(String message) throws IOException {
        if (!isOpen()) {
            throw new IOException("WebSocket is not connected");
        }
        writeFrame(0x1, message.getBytes(StandardCharsets.UTF_8));
    }

    public void close() {
        boolean wasRunning = this.running;
        this.running = false;
        if (wasRunning) {
            try {
                synchronized (this) {
                    writeFrame(0x8, new byte[]{0x03, (byte) 0xE8});
                }
            } catch (IOException ignored) {
            }
        }
        closeSocket();
        notifyClosed("Connection closed");
    }

    private void performUpgrade(String host, int port, boolean secure) throws IOException {
        byte[] nonce = new byte[16];
        this.random.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String path = this.uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (this.uri.getRawQuery() != null && !this.uri.getRawQuery().isEmpty()) {
            path += "?" + this.uri.getRawQuery();
        }

        String hostHeader = host;
        if ((secure && port != 443) || (!secure && port != 80)) {
            hostHeader += ":" + port;
        }
        StringBuilder request = new StringBuilder();
        request.append("GET ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(hostHeader).append("\r\n");
        request.append("Upgrade: websocket\r\n");
        request.append("Connection: Upgrade\r\n");
        request.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        request.append("Sec-WebSocket-Version: 13\r\n");
        request.append("User-Agent: zsg-rooms/1.0\r\n");
        if (this.authorization != null && !this.authorization.isEmpty()) {
            request.append("Authorization: ").append(this.authorization).append("\r\n");
        }
        request.append("\r\n");
        this.output.write(request.toString().getBytes(StandardCharsets.US_ASCII));
        this.output.flush();

        String response = readHttpHeaders();
        String[] lines = response.split("\r\n");
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
            }
        }
        if (lines.length == 0 || !lines[0].contains(" 101 ")) {
            String statusLine = lines.length == 0 ? "no response" : lines[0];
            String detail = readErrorBody(headers);
            throw new IOException("Relay rejected connection: " + statusLine + (detail.isEmpty() ? "" : " - " + detail));
        }
        String expectedAccept = websocketAccept(key);
        if (!expectedAccept.equals(headers.get("sec-websocket-accept"))) {
            throw new IOException("Relay returned an invalid WebSocket handshake");
        }
    }

    private String readErrorBody(Map<String, String> headers) {
        String rawLength = headers.get("content-length");
        if (rawLength == null) {
            return "";
        }
        try {
            int length = Math.min(4096, Math.max(0, Integer.parseInt(rawLength)));
            return new String(readExactly(length), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readHttpHeaders() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < 16384) {
            int value = this.input.read();
            if (value < 0) {
                throw new EOFException("Relay closed during WebSocket handshake");
            }
            bytes.write(value);
            if ((matched == 0 || matched == 2) && value == '\r') {
                matched += 1;
            } else if ((matched == 1 || matched == 3) && value == '\n') {
                matched += 1;
                if (matched == 4) {
                    return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
                }
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        throw new IOException("Relay handshake headers are too large");
    }

    private void readLoop() {
        String closeReason = "Relay disconnected";
        ByteArrayOutputStream fragmented = null;
        int fragmentedOpcode = 0;
        try {
            while (this.running) {
                Frame frame = readFrame();
                if (frame.opcode == 0x8) {
                    closeReason = decodeCloseReason(frame.payload);
                    break;
                }
                if (frame.opcode == 0x9) {
                    synchronized (this) {
                        writeFrame(0xA, frame.payload);
                    }
                    continue;
                }
                if (frame.opcode == 0xA) {
                    continue;
                }
                if (frame.opcode == 0x1 && frame.finished) {
                    this.listener.onText(new String(frame.payload, StandardCharsets.UTF_8));
                } else if (frame.opcode == 0x1) {
                    fragmented = new ByteArrayOutputStream();
                    fragmented.write(frame.payload);
                    fragmentedOpcode = frame.opcode;
                } else if (frame.opcode == 0x0 && fragmented != null) {
                    fragmented.write(frame.payload);
                    if (fragmented.size() > MAX_MESSAGE_BYTES) {
                        throw new IOException("Relay message is too large");
                    }
                    if (frame.finished) {
                        if (fragmentedOpcode == 0x1) {
                            this.listener.onText(new String(fragmented.toByteArray(), StandardCharsets.UTF_8));
                        }
                        fragmented = null;
                        fragmentedOpcode = 0;
                    }
                }
            }
        } catch (IOException exception) {
            closeReason = exception.getMessage() == null ? "Relay connection failed" : exception.getMessage();
        } finally {
            this.running = false;
            closeSocket();
            notifyClosed(closeReason);
        }
    }

    private void startHeartbeat() {
        this.heartbeatThread = new Thread(() -> {
            while (this.running) {
                try {
                    Thread.sleep(15000L);
                    if (this.running) {
                        synchronized (this) {
                            writeFrame(0x9, new byte[]{'z', 's', 'g'});
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException exception) {
                    this.running = false;
                    closeSocket();
                    notifyClosed(exception.getMessage() == null ? "Relay heartbeat failed" : exception.getMessage());
                    return;
                }
            }
        }, "ZSG Room WebSocket Heartbeat");
        this.heartbeatThread.setDaemon(true);
        this.heartbeatThread.start();
    }

    private Frame readFrame() throws IOException {
        int first = readByte();
        int second = readByte();
        boolean finished = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte() << 8) | readByte();
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
        }
        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("Relay frame is too large");
        }
        byte[] mask = masked ? readExactly(4) : null;
        byte[] payload = readExactly((int) length);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }
        return new Frame(finished, opcode, payload);
    }

    private synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
        if (this.output == null) {
            throw new IOException("WebSocket has no output stream");
        }
        this.output.write(0x80 | opcode);
        int length = payload.length;
        if (length <= 125) {
            this.output.write(0x80 | length);
        } else if (length <= 65535) {
            this.output.write(0x80 | 126);
            this.output.write((length >>> 8) & 0xFF);
            this.output.write(length & 0xFF);
        } else {
            this.output.write(0x80 | 127);
            long longLength = length;
            for (int shift = 56; shift >= 0; shift -= 8) {
                this.output.write((int) ((longLength >>> shift) & 0xFF));
            }
        }
        byte[] mask = new byte[4];
        this.random.nextBytes(mask);
        this.output.write(mask);
        for (int i = 0; i < payload.length; i++) {
            this.output.write(payload[i] ^ mask[i % 4]);
        }
        this.output.flush();
    }

    private int readByte() throws IOException {
        int value = this.input.read();
        if (value < 0) {
            throw new EOFException("Relay closed the connection");
        }
        return value;
    }

    private byte[] readExactly(int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = this.input.read(bytes, offset, length - offset);
            if (count < 0) {
                throw new EOFException("Relay closed during a message");
            }
            offset += count;
        }
        return bytes;
    }

    private String decodeCloseReason(byte[] payload) {
        if (payload.length <= 2) {
            return "Relay closed the connection";
        }
        return new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
    }

    private void closeSocket() {
        if (this.heartbeatThread != null && this.heartbeatThread != Thread.currentThread()) {
            this.heartbeatThread.interrupt();
        }
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void notifyClosed(String reason) {
        if (this.closeNotified.compareAndSet(false, true)) {
            this.listener.onClosed(reason);
        }
    }

    private String websocketAccept(String key) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest((key + ACCEPT_GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-1 is unavailable", exception);
        }
    }

    private static class Frame {
        private final boolean finished;
        private final int opcode;
        private final byte[] payload;

        private Frame(boolean finished, int opcode, byte[] payload) {
            this.finished = finished;
            this.opcode = opcode;
            this.payload = payload;
        }
    }
}
