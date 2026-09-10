package zsgrooms.modid.replay;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ReplayLibraryInstallerTest {
    @TempDir Path temp;

    @Test
    void firstSetupCreatesInstanceFolderAndCachedSetupWorksOffline() throws Exception {
        byte[] bytes = "verified library".getBytes(StandardCharsets.UTF_8);
        ReplayLibraryInstaller.Manifest manifest = manifest(bytes);
        Path folder = temp.resolve("minecraft with spaces/zsgrooms/replay-libraries");
        AtomicInteger downloads = new AtomicInteger();
        List<Path> paths = ReplayLibraryInstaller.prepare(folder, manifest, location -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(bytes);
        }, status -> assertTrue(status.startsWith("Installing")));
        assertEquals(Arrays.asList(folder.resolve("test.jar")), paths);
        assertArrayEquals(bytes, Files.readAllBytes(paths.get(0)));
        ReplayLibraryInstaller.prepare(folder, manifest, location -> {
            throw new IOException("Offline");
        }, status -> fail("No download required"));
        assertEquals(1, downloads.get());
    }

    @Test
    void checksumFailurePreservesOldFileAndRetryRepairsIt() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3};
        ReplayLibraryInstaller.Manifest manifest = manifest(bytes);
        Path folder = Files.createDirectory(temp.resolve("libraries"));
        Path target = folder.resolve("test.jar");
        Files.write(target, new byte[] {4});
        assertThrows(IOException.class, () -> ReplayLibraryInstaller.prepare(folder, manifest,
                location -> new ByteArrayInputStream(new byte[] {5}), status -> {}));
        assertArrayEquals(new byte[] {4}, Files.readAllBytes(target));
        assertNoPartialFiles(folder);
        ReplayLibraryInstaller.prepare(folder, manifest, location -> new ByteArrayInputStream(bytes), status -> {});
        assertArrayEquals(bytes, Files.readAllBytes(target));
    }

    @Test
    void interruptedDownloadCannotLeaveALoadablePartialJar() throws Exception {
        ReplayLibraryInstaller.Manifest manifest = manifest(new byte[] {1});
        Path folder = temp.resolve("libraries");
        assertThrows(IOException.class, () -> ReplayLibraryInstaller.prepare(folder, manifest,
                location -> new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("Interrupted");
                    }
                }, status -> {}));
        assertFalse(Files.exists(folder.resolve("test.jar")));
        assertNoPartialFiles(folder);
    }

    @Test
    void unrelatedJarsAreNeitherDeletedNorLoaded() throws Exception {
        ReplayLibraryInstaller.Manifest manifest = manifest(new byte[] {1});
        Path folder = Files.createDirectory(temp.resolve("libraries"));
        Path unrelated = Files.write(folder.resolve("old.jar"), new byte[] {2});
        List<Path> paths = ReplayLibraryInstaller.prepare(folder, manifest,
                location -> new ByteArrayInputStream(new byte[] {1}), status -> {});
        assertFalse(paths.contains(unrelated));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(unrelated));
    }

    @Test
    void invalidManifestPathsAndInsecureUrlsAreRejectedBeforeDownloading() throws Exception {
        ReplayLibraryInstaller.Manifest manifest = manifest(new byte[] {1});
        manifest.artifacts.get(0).file = "../outside.jar";
        assertThrows(IOException.class, () -> ReplayLibraryInstaller.prepare(temp, manifest,
                location -> { throw new AssertionError("Must not download"); }, status -> {}));
        manifest.artifacts.get(0).file = "test.jar";
        manifest.artifacts.get(0).url = "http://example.com/test.jar";
        assertThrows(IOException.class, () -> ReplayLibraryInstaller.prepare(temp, manifest,
                location -> { throw new AssertionError("Must not download"); }, status -> {}));
    }

    @Test
    void bundledAdapterMatchesManifestAndContainsNoExternalLibraries() throws Exception {
        ReplayLibraryInstaller.Manifest manifest;
        try (InputStream input = getClass().getResourceAsStream("/assets/zsg-rooms/replay/libraries.json")) {
            assertNotNull(input);
            manifest = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), ReplayLibraryInstaller.Manifest.class);
        }
        assertEquals(9, manifest.artifacts.size());
        ReplayLibraryInstaller.Artifact writer = manifest.artifacts.stream()
                .filter(artifact -> artifact.file.equals("zsg-replay-writer.jar")).findFirst().get();
        Path jar = temp.resolve("writer.jar");
        try (InputStream input = getClass().getResourceAsStream(writer.url.substring(9))) {
            assertNotNull(input);
            Files.copy(input, jar);
        }
        assertEquals(writer.sha256, ReplayLibraryInstaller.hash(jar));
        int classes = 0;
        try (JarInputStream input = new JarInputStream(Files.newInputStream(jar))) {
            java.util.jar.JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    assertEquals("zsgrooms/replayprobe/ReplayStudioWriter.class", entry.getName());
                    classes++;
                }
            }
        }
        assertEquals(1, classes);
    }

    private ReplayLibraryInstaller.Manifest manifest(byte[] bytes) throws Exception {
        Path sample = Files.write(temp.resolve("sample.bin"), bytes);
        ReplayLibraryInstaller.Artifact artifact = new ReplayLibraryInstaller.Artifact();
        artifact.file = "test.jar";
        artifact.sha256 = ReplayLibraryInstaller.hash(sample);
        artifact.url = "https://example.com/test.jar";
        ReplayLibraryInstaller.Manifest manifest = new ReplayLibraryInstaller.Manifest();
        manifest.artifacts = Arrays.asList(artifact);
        return manifest;
    }

    private void assertNoPartialFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".part")));
        }
    }
}
