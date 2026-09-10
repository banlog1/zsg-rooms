package zsgrooms.replayprobe;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ReplayLibraryProbeTest {
    private static final String FIXTURE = "zsgrooms.replayprobe.ReplayStudioFixture";
    private static final String WRITER = "zsgrooms.replayprobe.ReplayStudioWriter";
    private static final String METADATA = "com.replaymod.replaystudio.replay.ReplayMetaData";
    private static final String MARKER = "zsg-replacement-probe.txt";

    @TempDir
    Path temp;

    @Test
    void libraryIsNotOnTheApplicationClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(METADATA));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(FIXTURE));
    }

    @Test
    void nbtDependenciesAreSelfContainedWithoutMinecraftGuava() throws Exception {
        try (URLClassLoader loader = isolated(libraries())) {
            assertEquals(Boolean.TRUE, loader.loadClass(FIXTURE).getMethod("nbtRoundTrip").invoke(null));
            assertSame(loader, loader.loadClass("com.google.common.base.Preconditions").getClassLoader());
        }
    }

    @Test
    void writesAndReadsMcprWithoutReplayMod() throws Exception {
        try (URLClassLoader loader = isolated(libraries())) {
            Path replay = temp.resolve("fixture.mcpr");
            assertEquals(Boolean.TRUE, invoke(loader, "write", replay));
            assertFixture(read(loader, replay));
            try (JarFile file = new JarFile(replay.toFile())) {
                assertNotNull(file.getEntry("metaData.json"));
                assertNotNull(file.getEntry("recording.tmcpr"));
            }
            assertSame(loader, loader.loadClass(METADATA).getClassLoader());
        }
    }

    @Test
    void liveWriterPreservesRawPayloadsOrderAndMetadata() throws Exception {
        try (URLClassLoader loader = isolated(libraries())) {
            Path path = temp.resolve("live.mcpr");
            Class<?> type = loader.loadClass(WRITER);
            type.getMethod("verifyDependencies").invoke(null);
            int packetId = (Integer) loader.loadClass(FIXTURE).getMethod("timePacketId").invoke(null);
            byte[] first = ByteBuffer.allocate(16).putLong(10L).putLong(20L).array();
            byte[] second = ByteBuffer.allocate(16).putLong(30L).putLong(40L).array();
            try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class)
                    .newInstance(path, 1234L)) {
                java.lang.reflect.Method write = type.getMethod("write", int.class, int.class, long.class, byte[].class);
                write.invoke(writer, 0, packetId, 0L, first);
                write.invoke(writer, 0, packetId, 0L, second);
                write.invoke(writer, 0, packetId, 100L, first);
                type.getMethod("finish", int.class, boolean.class).invoke(writer, 73, true);
            }
            Map<String, Object> result = read(loader, path);
            assertEquals(73, result.get("selfId"));
            assertEquals(100, result.get("duration"));
            assertEquals(Arrays.asList(0L, 0L, 0L, 100L), result.get("timestamps"));
            assertEquals(Arrays.asList("LoginSuccess", "UpdateTime", "UpdateTime", "UpdateTime"), result.get("types"));
            List<?> payloads = (List<?>) result.get("payloads");
            assertEquals(Base64.getEncoder().encodeToString(first), payloads.get(1));
            assertEquals(Base64.getEncoder().encodeToString(second), payloads.get(2));
            assertEquals(Base64.getEncoder().encodeToString(first), payloads.get(3));
            assertEquals(736, result.get("protocol"));
        }
    }

    @Test
    void liveWriterRejectsInvalidEventsAndMarksAnIncompletePrefix() throws Exception {
        try (URLClassLoader loader = isolated(libraries())) {
            Path path = temp.resolve("partial.mcpr");
            Class<?> type = loader.loadClass(WRITER);
            int id = (Integer) loader.loadClass(FIXTURE).getMethod("timePacketId").invoke(null);
            byte[] payload = new byte[16];
            try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class)
                    .newInstance(path, 0L)) {
                java.lang.reflect.Method write = type.getMethod("write", int.class, int.class, long.class, byte[].class);
                write.invoke(writer, 0, id, 10L, payload);
                assertInstanceOf(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                        () -> write.invoke(writer, 0, id, 9L, payload)).getCause());
                assertInstanceOf(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                        () -> write.invoke(writer, 0, id, (long) Integer.MAX_VALUE + 1L, payload)).getCause());
                assertInstanceOf(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                        () -> write.invoke(writer, 1, id, 10L, payload)).getCause());
                type.getMethod("finish", int.class, boolean.class).invoke(writer, -1, false);
                assertInstanceOf(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
                        () -> write.invoke(writer, 0, id, 10L, payload)).getCause());
            }
            assertFalse(Files.exists(path));
            Map<String, Object> result = read(loader, temp.resolve("partial.incomplete.mcpr"));
            assertEquals(10, result.get("duration"));
            assertEquals(Arrays.asList("LoginSuccess", "UpdateTime"), result.get("types"));
        }
    }

    @Test
    void namespacedRaceManifestRoundTripsWithoutChangingReplayPackets() throws Exception {
        try (URLClassLoader loader = isolated(libraries())) {
            Path path = temp.resolve("race.mcpr");
            Class<?> type = loader.loadClass(WRITER);
            String json = "{\"schemaVersion\":1,\"displayName\":\"Player\",\"races\":[]}";
            int id = (Integer) loader.loadClass(FIXTURE).getMethod("timePacketId").invoke(null);
            try (AutoCloseable writer = (AutoCloseable) type.getConstructor(Path.class, long.class)
                    .newInstance(path, 0L)) {
                type.getMethod("write", int.class, int.class, long.class, byte[].class)
                        .invoke(writer, 0, id, 100L, new byte[16]);
                type.getMethod("writeRaceManifest", String.class).invoke(writer, json);
                type.getMethod("finish", int.class, boolean.class).invoke(writer, 7, true);
            }
            try (JarFile file = new JarFile(path.toFile());
                    InputStream input = file.getInputStream(file.getEntry("zsg-rooms/races.json"))) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                assertEquals(json, new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            }
            Map<String, Object> data = read(loader, path);
            assertEquals(100, data.get("duration"));
            assertEquals(7, data.get("selfId"));
            assertEquals(Arrays.asList("LoginSuccess", "UpdateTime"), data.get("types"));
        }
    }

    @Test
    void liveWriterDoesNotOverwriteAnExistingReplay() throws Exception {
        try (URLClassLoader loader = isolated(libraries())) {
            Path path = temp.resolve("existing.mcpr");
            byte[] sentinel = {1, 2, 3};
            Files.write(path, sentinel);
            assertInstanceOf(java.io.IOException.class, assertThrows(InvocationTargetException.class,
                    () -> loader.loadClass(WRITER).getConstructor(Path.class, long.class)
                            .newInstance(path, 0L)).getCause());
            assertArrayEquals(sentinel, Files.readAllBytes(path));
        }
    }

    @Test
    void fixtureContainsOnlyOurJava8Code() throws Exception {
        try (JarFile file = new JarFile(fixture().toFile())) {
            assertNull(file.getEntry("com/replaymod/replaystudio/replay/ReplayMetaData.class"));
            JarEntry entry = file.getJarEntry(FIXTURE.replace('.', '/') + ".class");
            assertNotNull(entry);
            try (DataInputStream input = new DataInputStream(file.getInputStream(entry))) {
                assertEquals(0xCAFEBABE, input.readInt());
                input.readUnsignedShort();
                assertEquals(52, input.readUnsignedShort());
            }
        }
    }

    @Test
    void libraryCanBeReplacedWithoutRebuildingFixture() throws Exception {
        List<Path> jars = libraries();
        Path original = jars.stream().filter(path -> path.getFileName().toString().startsWith("ReplayStudio-"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing ReplayStudio artifact"));
        Path replacement = temp.resolve("ReplayStudio-repacked.jar");
        // This tests a metadata-only modification, not arbitrary source/ABI changes.
        try (JarFile source = new JarFile(original.toFile());
                JarOutputStream output = new JarOutputStream(Files.newOutputStream(replacement))) {
            java.util.Enumeration<JarEntry> entries = source.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                output.putNextEntry(new JarEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream input = source.getInputStream(entry)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                }
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry(MARKER));
            output.write(1);
            output.closeEntry();
        }
        jars.set(jars.indexOf(original), replacement);
        try (URLClassLoader loader = isolated(jars)) {
            assertNotNull(loader.getResource(MARKER));
            assertEquals(replacement.toUri().toURL(), loader.loadClass(METADATA)
                    .getProtectionDomain().getCodeSource().getLocation());
            Path replay = temp.resolve("replacement.mcpr");
            invoke(loader, "write", replay);
            assertFixture(read(loader, replay));
        }
    }

    @Test
    @Tag("coexistence")
    void realReplayModClassesDoNotLeakIntoOurLibrary() throws Exception {
        Path reference = Paths.get(System.getProperty("replay.probe.reference"));
        ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader ambient = new URLClassLoader(new URL[]{reference.toUri().toURL()}, platformParent());
                URLClassLoader own = isolated(libraries())) {
            Thread.currentThread().setContextClassLoader(ambient);
            Class<?> external = ambient.loadClass(METADATA);
            Class<?> internal = own.loadClass(METADATA);
            assertNotSame(external, internal);
            assertSame(ambient, external.getClassLoader());
            assertSame(own, internal.getClassLoader());
            assertEquals(reference.toUri().toURL(), external.getProtectionDomain().getCodeSource().getLocation());
            assertNull(own.getResource("com/replaymod/recording/packet/PacketListener.class"));
            Path replay = temp.resolve("coexistence.mcpr");
            invoke(own, "write", replay);
            assertFixture(read(own, replay));
        } finally {
            Thread.currentThread().setContextClassLoader(originalContext);
        }
    }

    private static URLClassLoader isolated(List<Path> libraries) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(fixture().toUri().toURL());
        for (Path library : libraries) urls.add(library.toUri().toURL());
        // Java 8's extension loader / newer Java's platform loader: never the
        // Minecraft/application loader where ReplayMod's merged classes live.
        return new URLClassLoader(urls.toArray(new URL[0]), platformParent());
    }

    private static ClassLoader platformParent() {
        return ClassLoader.getSystemClassLoader().getParent();
    }

    private static Path fixture() {
        return Paths.get(System.getProperty("replay.probe.fixture"));
    }

    private static List<Path> libraries() throws Exception {
        try (Stream<Path> files = Files.list(Paths.get(System.getProperty("replay.probe.libraries")))) {
            return files.filter(path -> path.toString().endsWith(".jar")).sorted().collect(Collectors.toList());
        }
    }

    private static Object invoke(ClassLoader loader, String method, Path path) throws Exception {
        try {
            return loader.loadClass(FIXTURE).getMethod(method, Path.class).invoke(null, path);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(ClassLoader loader, Path path) throws Exception {
        return (Map<String, Object>) invoke(loader, "read", path);
    }

    private static void assertFixture(Map<String, Object> result) {
        assertEquals(736, result.get("protocol"));
        assertEquals(14, result.get("format"));
        assertEquals("1.16.1", result.get("minecraft"));
        assertEquals(65000, result.get("duration"));
        // ReplayStudio intentionally removes keep-alives from the decoded stream.
        assertEquals(Arrays.asList("LoginSuccess", "UpdateTime", "UpdateTime", "UpdateTime"), result.get("types"));
        assertEquals(Arrays.asList(0L, 0L, 0L, 65000L), result.get("timestamps"));
        List<?> payloads = (List<?>) result.get("payloads");
        assertEquals(Base64.getEncoder().encodeToString(ByteBuffer.allocate(16).putLong(100L).putLong(5000L).array()),
                payloads.get(1));
        assertEquals(Base64.getEncoder().encodeToString(ByteBuffer.allocate(16).putLong(200L).putLong(6000L).array()),
                payloads.get(2));
        assertEquals(Base64.getEncoder().encodeToString(ByteBuffer.allocate(16).putLong(300L).putLong(7000L).array()),
                payloads.get(3));
    }
}
