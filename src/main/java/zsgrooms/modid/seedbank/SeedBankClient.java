package zsgrooms.modid.seedbank;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SeedBankClient {
    public static final String DEFAULT_ENDPOINT = "https://zsg-rooms-seeds.banlogzzz.workers.dev";
    private static final Path CONFIG = Paths.get("config", "zsg-rooms-seedbank.txt");
    private static final ExecutorService REQUESTS = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ZSG Room Seed Bank");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<SeedBankProfile, ArrayDeque<String>> RECENT = new EnumMap<>(SeedBankProfile.class);

    private SeedBankClient() { }

    public static String getEndpoint() {
        return loadEndpoint(CONFIG);
    }

    static String loadEndpoint(Path config) {
        try {
            String endpoint = Files.isRegularFile(config)
                    ? new String(Files.readAllBytes(config), StandardCharsets.UTF_8).trim() : "";
            return endpoint.isEmpty() ? DEFAULT_ENDPOINT : endpoint;
        } catch (IOException error) { return ""; }
    }

    public static void saveEndpoint(String value) throws IOException {
        String endpoint = value.trim();
        if (!endpoint.isEmpty()) requestUri(endpoint);
        Files.createDirectories(CONFIG.getParent());
        Files.write(CONFIG, endpoint.getBytes(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> request(SeedBankProfile profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> recent;
                synchronized (RECENT) { recent = new ArrayList<>(RECENT.computeIfAbsent(profile, key -> new ArrayDeque<>())); }
                String seed = fetch(getEndpoint(), profile, recent);
                String family = Long.toString(Long.parseLong(seed) & 281474976710655L);
                synchronized (RECENT) {
                    ArrayDeque<String> history = RECENT.get(profile);
                    history.remove(family);
                    history.addLast(family);
                    while (history.size() > 16) history.removeFirst();
                }
                return seed;
            } catch (IOException error) { throw new CompletionException(error); }
        }, REQUESTS);
    }

    static URI requestUri(String endpoint) throws IOException {
        try {
            URI base = new URI(endpoint.trim());
            String host = base.getHost();
            boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            if (host == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || base.getPort() == 0 || base.getPort() > 65535
                    || !("https".equalsIgnoreCase(base.getScheme()) || (local && "http".equalsIgnoreCase(base.getScheme())))) {
                throw new IOException();
            }
            String text = base.toASCIIString();
            while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
            return new URI(text + "/v1/seed");
        } catch (Exception error) { throw new IOException("Set a valid HTTPS seed-bank URL in Room Settings; local HTTP is allowed for testing."); }
    }

    static String fetch(String endpoint, SeedBankProfile profile, List<String> excluded) throws IOException {
        if (excluded.size() > 16) throw new IOException("Too many recent seed families.");
        String requestId = UUID.randomUUID().toString();
        JsonObject request = new JsonObject();
        request.addProperty("profile", SeedBankProfile.MODEL_PROFILE);
        request.addProperty("type", profile.type);
        request.addProperty("requestId", requestId);
        JsonArray families = new JsonArray();
        for (String family : excluded) families.add(family);
        request.add("excludeFamilies", families);
        byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) requestUri(endpoint).toURL().openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            int status = connection.getResponseCode();
            if (status == 429) throw new IOException("Seed bank rate limit reached; try again shortly.");
            if (status == 409) throw new IOException("No fresh seed candidate available; try again or choose another bank.");
            if (status != 200) throw new IOException("Seed bank unavailable or empty. No alternate filter was used.");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > 8192) throw new IOException("Seed bank response is too large.");
                    body.write(buffer, 0, count);
                }
            }
            String seed = parseResponse(new String(body.toByteArray(), StandardCharsets.UTF_8), profile, requestId);
            if (excluded.contains(Long.toString(Long.parseLong(seed) & 281474976710655L))) {
                throw new IOException("Seed bank returned a recently prepared family.");
            }
            return seed;
        } catch (IOException error) {
            // Do not expose network exception details, which may include response data or private endpoints.
            String message = error.getMessage();
            if (message != null && (message.startsWith("Seed bank ") || message.startsWith("No fresh seed"))) throw error;
            throw new IOException("Seed bank request failed; check the service connection.");
        } finally { connection.disconnect(); }
    }

    static String parseResponse(String response, SeedBankProfile profile, String requestId) throws IOException {
        try {
            JsonObject body = new JsonParser().parse(response).getAsJsonObject();
            JsonElement seedValue = body.get("seed");
            if (!body.getAsJsonPrimitive("schemaVersion").isNumber() || !"1".equals(body.get("schemaVersion").getAsString())
                    || !SeedBankProfile.MODEL_PROFILE.equals(stringField(body, "profile"))
                    || !profile.type.equals(stringField(body, "type"))
                    || !requestId.equals(stringField(body, "requestId"))
                    || !stringField(body, "revision").matches("[a-f0-9]{64}")
                    || !seedValue.isJsonPrimitive() || !seedValue.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
            String seed = seedValue.getAsString();
            long value = Long.parseLong(seed);
            if (value == 0L || !Long.toString(value).equals(seed)) throw new IllegalArgumentException();
            return seed;
        } catch (RuntimeException error) { throw new IOException("Seed bank returned an invalid or mismatched record."); }
    }

    private static String stringField(JsonObject body, String name) {
        if (!body.getAsJsonPrimitive(name).isString()) throw new IllegalArgumentException();
        return body.get(name).getAsString();
    }
}
