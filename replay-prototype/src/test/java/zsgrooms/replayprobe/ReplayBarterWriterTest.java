package zsgrooms.replayprobe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ReplayBarterWriterTest {
    @TempDir Path directory;

    @Test void optionalEntryPreservesExactJsonAndDoesNotAppendToManifestOrPackets() throws Exception {
        try (URLClassLoader loader = loader()) {
            Path path = directory.resolve("barters.mcpr");
            Class<?> type = loader.loadClass("zsgrooms.replayprobe.ReplayStudioWriter");
            int packetId = (Integer) loader.loadClass("zsgrooms.replayprobe.ReplayStudioFixture").getMethod("timePacketId").invoke(null);
            String json = "{\"version\":1,\"protocol\":736,\"truncated\":false,\"events\":[]}";
            try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class).newInstance(path, 0L)) {
                type.getMethod("write", int.class, int.class, long.class, byte[].class).invoke(writer, 0, packetId, 10L, new byte[16]);
                type.getMethod("writeRaceManifest", String.class).invoke(writer, "{}");
                type.getMethod("writeBarters", String.class).invoke(writer, json);
                type.getMethod("finish", int.class, boolean.class).invoke(writer, 7, true);
                assertInstanceOf(IllegalStateException.class, assertThrows(InvocationTargetException.class,
                        () -> type.getMethod("writeBarters", String.class).invoke(writer, json)).getCause());
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                byte[] expected = json.getBytes(StandardCharsets.UTF_8);
                byte[] actual = new byte[expected.length];
                try (InputStream input = zip.getInputStream(zip.getEntry("zsg-rooms/barters.json"))) {
                    new DataInputStream(input).readFully(actual);
                    assertEquals(-1, input.read());
                }
                assertArrayEquals(expected, actual);
                assertEquals(2, zip.getEntry("zsg-rooms/races.json").getSize());
                assertNotNull(zip.getEntry("recording.tmcpr"));
            }
        }
    }

    @Test void oversizedOptionalPayloadCanBeRejectedAndPacketRecordingStillFinishes() throws Exception {
        try (URLClassLoader loader = loader()) {
            Path path = directory.resolve("rejected.mcpr");
            Class<?> type = loader.loadClass("zsgrooms.replayprobe.ReplayStudioWriter");
            try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class).newInstance(path, 0L)) {
                String oversized = String.join("", Collections.nCopies(4 * 1024 * 1024 + 1, "a"));
                assertInstanceOf(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                        () -> type.getMethod("writeBarters", String.class).invoke(writer, oversized)).getCause());
                type.getMethod("finish", int.class, boolean.class).invoke(writer, 7, true);
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                assertNull(zip.getEntry("zsg-rooms/barters.json"));
                assertNotNull(zip.getEntry("recording.tmcpr"));
                assertNotNull(zip.getEntry("metaData.json"));
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
