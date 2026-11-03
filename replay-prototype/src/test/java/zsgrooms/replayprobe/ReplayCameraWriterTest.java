package zsgrooms.replayprobe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ReplayCameraWriterTest {
    @TempDir Path temp;

    @Test void superDetailedSidecarsRoundTripAndBadHeadersDoNotAffectPackets() throws Exception {
        try (URLClassLoader loader = loader()) {
            byte[] baseline = entry(record(loader, "super-baseline", null, null), "recording.tmcpr");
            String[] methods = {"writePresentationCapture", "writeLocalSoundCapture"};
            String[] names = {"presentation", "local-sounds"};
            int[] magics = {0x5A505253, 0x5A534E44};
            for (int i = 0; i < names.length; i++) {
                byte[] data = ByteBuffer.allocate(17).putInt(magics[i]).putInt(1).putInt(127).put((byte) 0).putInt(20).array();
                Path spool = Files.write(temp.resolve(names[i] + ".bin"), data);
                Path replay = record(loader, names[i], spool, "{\"status\":\"complete\"}", methods[i]);
                assertArrayEquals(data, entry(replay, "zsg-rooms/" + names[i] + ".bin"));
                assertArrayEquals(data, Files.readAllBytes(spool));
                assertArrayEquals(baseline, entry(replay, "recording.tmcpr"));
                for (int malformed = 0; malformed < 3; malformed++) {
                    Files.write(spool, malformed == 0 ? new byte[7] : changed(data, malformed == 1 ? 0 : 4, -1));
                    replay = record(loader, names[i] + "-bad-" + malformed, spool, "{\"status\":\"complete\"}", methods[i]);
                    assertNull(entry(replay, "zsg-rooms/" + names[i] + ".bin"));
                    assertTrue(new String(entry(replay, "zsg-rooms/" + names[i] + ".json"), StandardCharsets.UTF_8).contains("\"failed\""));
                    assertArrayEquals(baseline, entry(replay, "recording.tmcpr"));
                }
                replay = record(loader, names[i] + "-metadata", spool, "[]", methods[i]);
                assertNull(entry(replay, "zsg-rooms/" + names[i] + ".bin"));
                assertArrayEquals(baseline, entry(replay, "recording.tmcpr"));
            }
        }
    }

    @Test void exactCameraBinaryAndCoverageRoundTripAtAllRatesWithoutChangingPackets() throws Exception {
        try (URLClassLoader loader = loader()) {
            Path baseline = record(loader, "baseline", null, null);
            byte[] packets = entry(baseline, "recording.tmcpr");
            for (int hz : new int[] { 60, 120, 240 }) {
                byte[] camera = camera(hz);
                Path spool = Files.write(temp.resolve("camera-" + hz + ".bin"), camera);
                String coverage = "{\"schemaVersion\":1,\"requested\":true,\"supported\":true,\"hz\":" + hz
                        + ",\"status\":\"gaps\",\"samples\":4,\"gaps\":1}";
                Path replay = record(loader, "camera-" + hz, spool, coverage);
                assertArrayEquals(camera, entry(replay, "zsg-rooms/camera.bin"));
                assertArrayEquals(coverage.getBytes(StandardCharsets.UTF_8), entry(replay, "zsg-rooms/camera.json"));
                assertArrayEquals(camera, Files.readAllBytes(spool));
                assertArrayEquals(packets, entry(replay, "recording.tmcpr"));
                Map<?, ?> decoded = read(loader, replay);
                assertEquals(Arrays.asList(0L, 10L, 20L), decoded.get("timestamps"));
                assertEquals(20, decoded.get("duration"));
                assertEquals(Arrays.asList("LoginSuccess", "UpdateTime", "UpdateTime"), decoded.get("types"));
                assertNull(entry(replay, "zsg-rooms/detail.bin"));
                assertNull(entry(replay, "zsg-rooms/races.json"));
            }
        }
    }

    @Test void absentSpoolCanDescribeStandardOrUnsupportedWriterWithoutInventingPackets() throws Exception {
        try (URLClassLoader loader = loader()) {
            String standard = "{\"schemaVersion\":1,\"requested\":false,\"supported\":true,\"hz\":0,\"status\":\"standard\"}";
            String unsupported = "{\"schemaVersion\":1,\"requested\":true,\"supported\":false,\"hz\":240,\"status\":\"unavailable\"}";
            for (String coverage : new String[] { standard, unsupported }) {
                Path replay = record(loader, "absent-" + (coverage == standard ? "standard" : "unsupported"), null, coverage);
                assertNull(entry(replay, "zsg-rooms/camera.bin"));
                assertArrayEquals(coverage.getBytes(StandardCharsets.UTF_8), entry(replay, "zsg-rooms/camera.json"));
                assertEquals(Arrays.asList(0L, 10L, 20L), read(loader, replay).get("timestamps"));
            }
        }
    }

    @Test void emptyButWellFormedSpoolIsAllowed() throws Exception {
        try (URLClassLoader loader = loader()) {
            byte[] header = ByteBuffer.allocate(12).putInt(0x5A43414D).putInt(1).putInt(240).array();
            Path spool = Files.write(temp.resolve("empty.bin"), header);
            Path replay = record(loader, "empty", spool, "{\"schemaVersion\":1,\"hz\":240,\"status\":\"complete\"}");
            assertArrayEquals(header, entry(replay, "zsg-rooms/camera.bin"));
        }
    }

    @Test void malformedHeadersRecordsAndPartialTailsAreRemovedWhilePacketsContinue() throws Exception {
        List<byte[]> malformed = new ArrayList<>();
        byte[] valid = camera(240);
        malformed.add(new byte[0]);
        malformed.add(Arrays.copyOf(valid, 11));
        malformed.add(Arrays.copyOf(valid, valid.length - 1));
        malformed.add(Arrays.copyOf(valid, valid.length + 1));
        malformed.add(changed(valid, 0, 0x5A444554));
        malformed.add(changed(valid, 4, 2));
        malformed.add(changed(valid, 8, 0));
        malformed.add(changed(valid, 8, 20));
        malformed.add(changed(valid, 8, 239));
        malformed.add(changed(valid, 8, 241));
        malformed.add(changed(valid, 8, 480));
        malformed.add(changed(valid, 12, -1));
        malformed.add(changed(valid, 12 + 24, 0));
        malformed.add(changed(valid, 12 + 3 * 24, 7));
        malformed.add(changed(valid, 16, -2));
        malformed.add(changed(valid, 20, -2));
        malformed.add(changed(valid, 16 + 24, -1));
        malformed.add(changed(valid, 20 + 24, -1));
        for (float pitch : new float[] { -91, 91, Math.nextDown(-90.0f), Math.nextUp(90.0f) })
            malformed.add(changed(valid, 32 + 24, Float.floatToRawIntBits(pitch)));
        for (int flags : new int[] { -1, 3, 4 }) malformed.add(changed(valid, 24, flags));
        for (float angle : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            malformed.add(changed(valid, 28, Float.floatToRawIntBits(angle)));
            malformed.add(changed(valid, 32, Float.floatToRawIntBits(angle)));
        }
        try (URLClassLoader loader = loader()) {
            byte[] baseline = entry(record(loader, "malformed-baseline", null, null), "recording.tmcpr");
            for (int i = 0; i < malformed.size(); i++) {
                Path spool = Files.write(temp.resolve("malformed-" + i + ".bin"), malformed.get(i));
                Path replay = record(loader, "malformed-" + i, spool, "{\"schemaVersion\":1,\"hz\":240,\"status\":\"complete\"}");
                assertNull(entry(replay, "zsg-rooms/camera.bin"), "Malformed case " + i);
                assertFailed(replay);
                assertArrayEquals(baseline, entry(replay, "recording.tmcpr"));
                assertArrayEquals(malformed.get(i), Files.readAllBytes(spool));
            }
        }
    }

    @Test void missingOrNonRegularSpoolReportsFailureAndMainRecordingFinishes() throws Exception {
        try (URLClassLoader loader = loader()) {
            Path directory = Files.createDirectory(temp.resolve("not-a-spool"));
            for (Path spool : new Path[] { temp.resolve("missing.bin"), directory }) {
                Path replay = record(loader, spool == directory ? "directory" : "missing", spool,
                        "{\"schemaVersion\":1,\"requested\":true,\"hz\":60,\"status\":\"complete\"}");
                assertNull(entry(replay, "zsg-rooms/camera.bin"));
                assertFailed(replay);
                assertNotNull(entry(replay, "metaData.json"));
                assertEquals(Arrays.asList(0L, 10L, 20L), read(loader, replay).get("timestamps"));
            }
        }
    }

    @Test void malformedOrOversizedCoverageIsContainedToTheOptionalTrack() throws Exception {
        List<String> malformed = Arrays.asList("{", "[]", "null", "1", "\"text\"",
                String.join("", Collections.nCopies(16385, "a")),
                "{\"status\":\"" + String.join("", Collections.nCopies(6000, "\u20ac")) + "\"}");
        try (URLClassLoader loader = loader()) {
            Path spool = Files.write(temp.resolve("valid.bin"), camera(60));
            for (int i = 0; i < malformed.size(); i++) {
                Path replay = record(loader, "bad-coverage-" + i, spool, malformed.get(i));
                assertNull(entry(replay, "zsg-rooms/camera.bin"));
                assertFailed(replay);
                assertEquals(Arrays.asList(0L, 10L, 20L), read(loader, replay).get("timestamps"));
            }
        }
    }

    @Test void optionalCameraApiRejectsCallsAfterFinish() throws Exception {
        try (URLClassLoader loader = loader()) {
            Class<?> type = loader.loadClass("zsgrooms.replayprobe.ReplayStudioWriter");
            Path path = temp.resolve("closed.mcpr");
            try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class).newInstance(path, 0L)) {
                type.getMethod("finish", int.class, boolean.class).invoke(writer, 7, true);
                assertInstanceOf(IllegalStateException.class, assertThrows(InvocationTargetException.class,
                        () -> type.getMethod("writeCameraCapture", Path.class, String.class)
                                .invoke(writer, null, "{}")).getCause());
            }
            assertNotNull(entry(path, "recording.tmcpr"));
        }
    }

    private Path record(URLClassLoader loader, String name, Path spool, String coverage) throws Exception {
        return record(loader, name, spool, coverage, "writeCameraCapture");
    }

    private Path record(URLClassLoader loader, String name, Path spool, String coverage, String method) throws Exception {
        Path path = temp.resolve(name + ".mcpr");
        Class<?> type = loader.loadClass("zsgrooms.replayprobe.ReplayStudioWriter");
        int packetId = (Integer) loader.loadClass("zsgrooms.replayprobe.ReplayStudioFixture").getMethod("timePacketId").invoke(null);
        try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class).newInstance(path, 0L)) {
            type.getMethod("write", int.class, int.class, long.class, byte[].class).invoke(writer, 0, packetId, 10L, new byte[16]);
            if (coverage != null) type.getMethod(method, Path.class, String.class).invoke(writer, spool, coverage);
            type.getMethod("write", int.class, int.class, long.class, byte[].class).invoke(writer, 0, packetId, 20L, new byte[16]);
            type.getMethod("finish", int.class, boolean.class).invoke(writer, 7, true);
        }
        return path;
    }

    private static byte[] camera(int hz) {
        return ByteBuffer.allocate(12 + 4 * 24).putInt(0x5A43414D).putInt(1).putInt(hz)
                .putInt(0).putInt(-1).putInt(-1).putInt(0).putFloat(0).putFloat(-0.0f)
                .putInt(8).putInt(0).putInt(1).putInt(1).putInt(0x47C35001).putInt(0xBEAAAAAB)
                .putInt(9).putInt(0).putInt(1).putInt(2).putFloat(0).putFloat(0)
                .putInt(Integer.MAX_VALUE).putInt(1).putInt(2).putInt(1).putFloat(-721.125f).putFloat(89.5f).array();
    }

    private static byte[] changed(byte[] source, int offset, int value) {
        byte[] result = source.clone();
        ByteBuffer.wrap(result).putInt(offset, value);
        return result;
    }

    private static Map<?, ?> read(URLClassLoader loader, Path replay) throws Exception {
        return (Map<?, ?>) loader.loadClass("zsgrooms.replayprobe.ReplayStudioFixture").getMethod("read", Path.class).invoke(null, replay);
    }

    private static void assertFailed(Path replay) throws Exception {
        String coverage = new String(entry(replay, "zsg-rooms/camera.json"), StandardCharsets.UTF_8);
        assertTrue(coverage.contains("\"status\":\"failed\""), coverage);
        assertTrue(coverage.contains("\"failure\":"), coverage);
    }

    private static byte[] entry(Path replay, String name) throws Exception {
        try (ZipFile zip = new ZipFile(replay.toFile())) {
            if (zip.getEntry(name) == null) return null;
            try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                return bytes.toByteArray();
            }
        }
    }

    private static URLClassLoader loader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(Paths.get(System.getProperty("replay.probe.fixture")).toUri().toURL());
        try (Stream<Path> files = Files.list(Paths.get(System.getProperty("replay.probe.libraries")))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".jar")).collect(Collectors.toList()))
                urls.add(file.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent());
    }
}
