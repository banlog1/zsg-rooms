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
import java.util.concurrent.atomic.AtomicInteger;

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
        body.add("structure", new JsonParser().parse("[32,192]"));
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
    void aaBankCannotAcceptAnOrdinaryTempleResponse() throws Exception {
        JsonObject body = response();
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.AA_THUNDERLESS, ID));
        body.addProperty("type", "aa_temple");
        assertEquals(SEED, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.AA_THUNDERLESS, ID).seed);
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
    }

    @Test
    void ruinedPortalResponsesMustMatchTheirOwnProfile() throws Exception {
        JsonObject body = response();
        body.addProperty("type", "ruined_portal");
        assertEquals(SEED, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.RUINED_PORTAL, ID).seed);
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(response().toString(), SeedBankProfile.RUINED_PORTAL, ID));
    }

    @Test
    void buriedTreasureResponsesMustMatchTheirOwnProfile() throws Exception {
        JsonObject body = response();
        body.addProperty("type", "buried_treasure");
        assertEquals(SEED, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.BURIED_TREASURE, ID).seed);
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(response().toString(), SeedBankProfile.BURIED_TREASURE, ID));
    }

    @Test
    void requiresMatchingProfileTypeRequestAndExactStringSeed() throws Exception {
        assertEquals(SEED, SeedBankClient.parseResponse(response().toString(), SeedBankProfile.TEMPLE, ID).seed);
        for (String seed : Arrays.asList("-9223372036854775808", "9007199254740993")) {
            JsonObject body = response(); body.addProperty("seed", seed);
            assertEquals(seed, SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID).seed);
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

    @Test void exactStructureCoordinatesAreRequiredAndTravelWithTheSeed() throws Exception {
        SeedBankEntry entry = SeedBankClient.parseResponse(response().toString(), SeedBankProfile.TEMPLE, ID);
        assertEquals(32, entry.structureX);
        assertEquals(192, entry.structureZ);
        String specification = entry.roomSeed(SeedBankProfile.TEMPLE);
        assertEquals(SEED, ZsgSeedBridge.extractMinecraftSeed(specification));
        assertEquals(new net.minecraft.util.math.BlockPos(32, 0, 192),
                SeedStructureTarget.parse(specification, Long.MAX_VALUE, SeedBankProfile.TEMPLE.specification));
        for (String coordinates : Arrays.asList("null", "[]", "[32]", "[32,192,0]", "[1.5,2]", "[30000001,0]", "[\"32\",192]", "[4294967296,0]")) {
            JsonObject body = response();
            body.add("structure", new JsonParser().parse(coordinates));
            assertThrows(IOException.class, () -> SeedBankClient.parseResponse(body.toString(), SeedBankProfile.TEMPLE, ID));
        }
        JsonObject missing = response(); missing.remove("structure");
        assertThrows(IOException.class, () -> SeedBankClient.parseResponse(missing.toString(), SeedBankProfile.TEMPLE, ID));
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
    void onlyAaRetriesExhaustedHistoryAndOnlyOnce() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger forcedStatus = new AtomicInteger();
        server.createContext("/v1/seed", exchange -> {
            calls.incrementAndGet();
            JsonObject request = new JsonParser().parse(new java.io.InputStreamReader(
                    exchange.getRequestBody(), StandardCharsets.UTF_8)).getAsJsonObject();
            int status = forcedStatus.get() != 0 ? forcedStatus.get()
                    : request.getAsJsonArray("excludeFamilies").size() > 0 ? 409 : 200;
            JsonObject body = response();
            body.addProperty("requestId", request.get("requestId").getAsString());
            body.addProperty("type", request.get("type").getAsString());
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            java.util.List<String> recent = Collections.singletonList(Long.toString(Long.MAX_VALUE & 281474976710655L));
            assertEquals(SEED, SeedBankClient.fetchWithRecentFamilies(endpoint, SeedBankProfile.AA_THUNDERLESS, recent).seed);
            assertEquals(2, calls.get());
            for (SeedBankProfile profile : SeedBankProfile.values()) {
                if (profile == SeedBankProfile.AA_THUNDERLESS) continue;
                calls.set(0);
                assertThrows(IOException.class, () -> SeedBankClient.fetchWithRecentFamilies(endpoint, profile, recent));
                assertEquals(1, calls.get());
            }
            for (int status : Arrays.asList(429, 503)) {
                forcedStatus.set(status);
                calls.set(0);
                assertThrows(IOException.class, () -> SeedBankClient.fetchWithRecentFamilies(endpoint, SeedBankProfile.AA_THUNDERLESS, recent));
                assertEquals(1, calls.get());
            }
            forcedStatus.set(409);
            calls.set(0);
            assertThrows(IOException.class, () -> SeedBankClient.fetchWithRecentFamilies(endpoint, SeedBankProfile.AA_THUNDERLESS, recent));
            assertEquals(2, calls.get());
            calls.set(0);
            assertThrows(IOException.class, () -> SeedBankClient.fetchWithRecentFamilies(endpoint, SeedBankProfile.AA_THUNDERLESS, Collections.emptyList()));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
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
            assertEquals(SEED, SeedBankClient.fetch(endpoint, SeedBankProfile.TEMPLE, Collections.emptyList()).seed);
            assertThrows(IOException.class, () -> SeedBankClient.fetch(endpoint, SeedBankProfile.TEMPLE,
                    Collections.singletonList(Long.toString(Long.MAX_VALUE & 281474976710655L))));
            IOException error = assertThrows(IOException.class, () -> SeedBankClient.fetch(endpoint + "/failure", SeedBankProfile.TEMPLE, Collections.emptyList()));
            assertFalse(error.getMessage().contains(SEED));
        } finally { server.stop(0); }
    }
}
