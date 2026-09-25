package zsgrooms.modid.seedbank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zsgrooms.modid.ZsgSeedBridge;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SeedBankClientTest {
    private static final String ID = "test-request";
    private static final String SEED = "9223372036854775807";

    @Test
    void endpointDefaultsToProductionButPreservesExplicitOverrides(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("seedbank.txt");
        assertEquals(SeedBankClient.DEFAULT_ENDPOINT, SeedBankClient.loadEndpoint(config));
        assertFalse(Files.exists(config));
        Files.write(config, " \r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(SeedBankClient.DEFAULT_ENDPOINT, SeedBankClient.loadEndpoint(config));
        for (String endpoint : Arrays.asList("http://127.0.0.1:8791", "https://example.com")) {
            Files.write(config, (" " + endpoint + "\r\n").getBytes(StandardCharsets.UTF_8));
            assertEquals(endpoint, SeedBankClient.loadEndpoint(config));
        }
        assertEquals(SeedBankClient.DEFAULT_ENDPOINT + "/v1/seed",
                SeedBankClient.requestUri(SeedBankClient.DEFAULT_ENDPOINT).toString());
    }

    private JsonObject response() {
        JsonObject body = new JsonObject();
        body.addProperty("schemaVersion", 1);
        body.addProperty("profile", SeedBankProfile.MODEL_PROFILE);
        body.addProperty("type", "temple");
        body.addProperty("requestId", ID);
        body.addProperty("revision", String.join("", Collections.nCopies(64, "a")));
        body.addProperty("seed", SEED);
        return body;
    }

    @Test
    void bankChoicesRoundTripWithoutBecomingFsgOrChangingTheirStructure() {
        for (SeedBankProfile bank : SeedBankProfile.values()) {
            assertEquals(bank.specification, ZsgSeedBridge.normalizeSeedSpecification(bank.label));
            assertEquals(bank.label, ZsgSeedBridge.seedTypeLabel(bank.specification));
            assertFalse(ZsgSeedBridge.isFsgFilterSeedType(bank.specification));
            String seed = ZsgSeedBridge.buildSeedForStructure(SEED, bank.specification, 4);
            assertEquals(SEED, ZsgSeedBridge.extractMinecraftSeed(seed));
            assertEquals(bank.specification, ZsgSeedBridge.seedSpecificationFromSeed(seed));
            assertTrue(ZsgSeedBridge.extractMinecraftSeed(ZsgSeedBridge.fetchSeedForRoom("test", bank.specification)).startsWith("pending-"));
        }
        assertTrue(ZsgSeedBridge.isFsgFilterSeedType("zsgtemple"));
        assertNull(SeedBankProfile.find("rooms-temple-v4"));
    }

    @Test
    void ruinedPortalResponsesMustMatchTheirOwnProfile() throws Exception {
        JsonObject body = response();
        body.addProperty("type", "ruined_portal");
        assertEquals(SEED, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.RUINED_PORTAL, ID));
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(response().toString(), SeedBankProfile.RUINED_PORTAL, ID));
    }

    @Test
    void buriedTreasureResponsesMustMatchTheirOwnProfile() throws Exception {
        JsonObject body = response();
        body.addProperty("type", "buried_treasure");
        assertEquals(SEED, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.BURIED_TREASURE, ID));
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(response().toString(), SeedBankProfile.BURIED_TREASURE, ID));
    }

    @Test
    void requiresMatchingProfileTypeRequestAndExactStringSeed() throws Exception {
        assertEquals(SEED, SeedBankClient.parseResponse(response().toString(), SeedBankProfile.TEMPLE, ID));
        for (String seed : Arrays.asList("-9223372036854775808", "9007199254740993")) {
            JsonObject body = response(); body.addProperty("seed", seed);
            assertEquals(seed, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        }
        for (String field : Arrays.asList("profile", "type", "requestId", "revision", "seed")) {
            JsonObject body = response(); body.addProperty(field, "invalid");
            IOException error = assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
            assertFalse(error.getMessage().contains(SEED));
        }
        JsonObject numeric = response(); numeric.addProperty("seed", Long.MAX_VALUE);
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(numeric.toString(), SeedBankProfile.TEMPLE, ID));
        JsonObject wrongVersion = response(); wrongVersion.addProperty("schemaVersion", 1.5);
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(wrongVersion.toString(), SeedBankProfile.TEMPLE, ID));
        for (String bad : Arrays.asList("0", "01", "+123", "9223372036854775808")) {
            JsonObject body = response(); body.addProperty("seed", bad);
            assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        }
    }

    @Test
    void transportRequiresTlsExceptForLoopbackAndRejectsSecretsInUrls() throws Exception {
        assertEquals("https://example.com/v1/seed", SeedBankClient.requestUri("https://example.com/").toString());
        assertEquals("http://127.0.0.1:8791/v1/seed", SeedBankClient.requestUri("http://127.0.0.1:8791").toString());
        for (String endpoint : Arrays.asList("", "http://example.com", "https://user:password@example.com", "https://example.com?secret=x", "https://example.com#fragment", "file:///tmp", "https://example.com:70000")) {
            assertThrows(IOException.class, () -> SeedBankClient.requestUri(endpoint));
        }
    }

    @Test
    void localHttpRoundTripPreservesSeedAndFailsClosedOnServerErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/seed", exchange -> {
            JsonObject request = new JsonParser().parse(new java.io.InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject body = response(); body.addProperty("requestId", request.get("requestId").getAsString());
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/failure/v1/seed", exchange -> {
            byte[] bytes = SEED.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, bytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals(SEED, SeedBankClient.fetch(endpoint, SeedBankProfile.TEMPLE, Collections.emptyList()));
            assertThrows(IOException.class, () -> SeedBankClient.fetch(endpoint, SeedBankProfile.TEMPLE,
                    Collections.singletonList(Long.toString(Long.MAX_VALUE & 281474976710655L))));
            IOException error = assertThrows(IOException.class, () -> SeedBankClient.fetch(endpoint + "/failure", SeedBankProfile.TEMPLE, Collections.emptyList()));
            assertFalse(error.getMessage().contains(SEED));
        } finally { server.stop(0); }
    }
}
