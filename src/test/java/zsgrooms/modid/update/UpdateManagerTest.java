package zsgrooms.modid.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void selectsCoreJarAndItsChecksumRegardlessOfCompanionAssetOrder() throws Exception {
        for (boolean reversed : new boolean[] {false, true}) {
            JsonObject release = release("v1.2.3");
            String[] names = {"zsg-replay-viewer-0.1.0.jar.sha256", "zsg-rooms-1.2.3.jar.sha256",
                    "zsg-rooms-1.2.3-sources.jar", "zsg-rooms-1.2.3.jar", "zsg-replay-viewer-0.1.0.jar",
                    "zsg-rooms-1.2.2.jar", "zsg-rooms-1.2.3-sources.jar.sha256"};
            for (int i = 0; i < names.length; i++) asset(release, names[reversed ? names.length - 1 - i : i]);
            UpdateRelease result = UpdateManager.parseRelease(release);
            assertEquals("https://example.invalid/zsg-rooms-1.2.3.jar", result.downloadUrl);
            assertEquals("https://example.invalid/zsg-rooms-1.2.3.jar.sha256", result.checksumUrl);
            assertEquals("0.1.0", result.viewer.version);
            assertEquals("https://example.invalid/zsg-replay-viewer-0.1.0.jar", result.viewer.downloadUrl);
            assertEquals("https://example.invalid/zsg-replay-viewer-0.1.0.jar.sha256", result.viewer.checksumUrl);
        }
    }

    @Test
    void neverUsesAnUnrelatedChecksumAndPreservesGithubDigest() throws Exception {
        JsonObject release = release("v1.2.3");
        asset(release, "zsg-replay-viewer-0.1.0.jar.sha256");
        JsonObject jar = asset(release, "zsg-rooms-1.2.3.jar");
        assertNull(UpdateManager.parseRelease(release).checksumUrl);
        jar.addProperty("digest", "sha256:expected-core-digest");
        assertEquals("expected-core-digest", UpdateManager.parseRelease(release).sha256);
    }

    @Test
    void rejectsReleasesWithoutTheirExactCoreArtifact() {
        JsonObject release = release("v1.2.3");
        asset(release, "zsg-replay-viewer-0.1.0.jar");
        asset(release, "zsg-rooms-1.2.2.jar");
        asset(release, "zsg-rooms-1.2.3-sources.jar");
        assertThrows(java.io.IOException.class, () -> UpdateManager.parseRelease(release));
    }

    @Test
    void viewerUsesItsOwnGithubDigestWithoutAnyChecksumFile() throws Exception {
        JsonObject release = release("v1.2.3");
        asset(release, "zsg-rooms-1.2.3.jar");
        asset(release, "zsg-rooms-1.2.3.jar.sha256");
        asset(release, "zsg-replay-viewer-0.2.0-sources.jar");
        asset(release, "zsg-replay-viewer-0.2.0-sources.jar.sha256");
        asset(release, "zsg-replay-viewer-0.2.0.jar").addProperty("digest", "sha256:viewer-digest");
        UpdateArtifact viewer = UpdateManager.parseRelease(release).viewer;
        assertEquals("viewer-digest", viewer.sha256);
        assertNull(viewer.checksumUrl);
    }

    @Test
    void comparesInstalledComponentsIndependentlyWithoutInstallingOrDowngrading() throws Exception {
        JsonObject json = release("v1.2.3");
        asset(json, "zsg-rooms-1.2.3.jar");
        asset(json, "zsg-replay-viewer-0.2.0.jar");
        UpdateRelease release = UpdateManager.parseRelease(json);
        assertEquals(2, release.updates("1.2.2", "0.1.0").size());
        List<UpdateArtifact> viewerOnly = release.updates("1.2.3", "0.1.0");
        assertEquals(1, viewerOnly.size());
        assertEquals("zsg-replay-viewer", viewerOnly.get(0).modId);
        assertEquals("zsg-rooms", release.updates("1.2.2", "0.2.0").get(0).modId);
        assertEquals(1, release.updates("1.2.2", null).size());
        assertTrue(release.updates("1.2.3", null).isEmpty());
        assertTrue(release.updates("1.2.3", "0.2.0").isEmpty());
        assertTrue(release.updates("1.3.0", "0.3.0").isEmpty());
    }

    @Test
    void olderReleasesAndSourceOnlyViewerDoNotOfferViewerUpdates() throws Exception {
        JsonObject json = release("v1.2.3");
        asset(json, "zsg-rooms-1.2.3.jar");
        asset(json, "zsg-replay-viewer-0.2.0-sources.jar");
        UpdateRelease release = UpdateManager.parseRelease(json);
        assertNull(release.viewer);
        assertEquals(1, release.updates("1.2.2", "0.1.0").size());
        assertTrue(release.updates("1.2.3", "0.1.0").isEmpty());
    }

    @Test
    void ambiguousViewerAssetsAreRejected() {
        JsonObject json = release("v1.2.3");
        asset(json, "zsg-rooms-1.2.3.jar");
        asset(json, "zsg-replay-viewer-0.1.0.jar");
        asset(json, "zsg-replay-viewer-0.2.0.jar");
        assertThrows(IOException.class, () -> UpdateManager.parseRelease(json));
    }

    @Test
    void verifiesChecksumAndModIdentityAndVersion() throws Exception {
        UpdateArtifact viewer = new UpdateArtifact("zsg-replay-viewer", "Replay Viewer", "0.2.0",
                "unused", "", null, "zsg-replay-viewer-0.2.0.jar");
        byte[] valid = jar("zsg-replay-viewer", "0.2.0");
        String hash = UpdaterHelper.sha256(valid);
        UpdateManager.verifyDownload(viewer, valid, hash);
        UpdateManager.verifyDownload(viewer, valid, hash.toUpperCase(java.util.Locale.ROOT));
        assertThrows(IOException.class, () -> UpdateManager.verifyDownload(viewer, valid, null));
        assertThrows(IOException.class, () -> UpdateManager.verifyDownload(viewer, valid, "invalid"));
        assertThrows(IOException.class, () -> UpdateManager.verifyDownload(viewer, valid,
                UpdaterHelper.sha256("corrupt".getBytes(StandardCharsets.UTF_8))));
        for (byte[] wrong : new byte[][] {jar("zsg-rooms", "0.2.0"), jar("zsg-replay-viewer", "0.1.0"),
                "not a jar".getBytes(StandardCharsets.UTF_8)}) {
            assertThrows(IOException.class, () -> UpdateManager.verifyDownload(viewer, wrong, UpdaterHelper.sha256(wrong)));
        }
    }

    private static byte[] jar(String id, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"id\":\"" + id + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void batchHelperInstallsBothModsAndClearsMarker() throws Exception {
        Properties properties = batch(2);
        Path marker = marker(properties);
        UpdaterHelper.main(new String[] {marker.toString()});
        assertInstalled(properties, 0);
        assertInstalled(properties, 1);
        assertFalse(Files.exists(marker));
    }

    @Test
    void viewerOnlyBatchLeavesCoreUntouched() throws Exception {
        Path core = this.temporaryDirectory.resolve("core.jar");
        Files.write(core, "core".getBytes(StandardCharsets.UTF_8));
        Properties properties = batch(1);
        Path marker = marker(properties);
        UpdaterHelper.installBatch(properties, marker);
        assertInstalled(properties, 0);
        assertEquals("core", new String(Files.readAllBytes(core), StandardCharsets.UTF_8));
    }

    @Test
    void missingOrCorruptSecondDownloadCannotInstallFirstComponent() throws Exception {
        Properties properties = batch(2);
        Path marker = marker(properties);
        Path second = java.nio.file.Paths.get(properties.getProperty("1.pending"));
        Files.write(second, "corrupt".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> UpdaterHelper.installBatch(properties, marker));
        Files.delete(second);
        assertThrows(IOException.class, () -> UpdaterHelper.installBatch(properties, marker));
        assertEquals("old", new String(Files.readAllBytes(java.nio.file.Paths.get(properties.getProperty("0.target"))),
                StandardCharsets.UTF_8));
        assertTrue(Files.exists(marker));
    }

    @Test
    void resumesPartiallyInstalledBatchAfterAFileLock() throws Exception {
        Properties properties = batch(2);
        Path marker = marker(properties);
        Files.move(java.nio.file.Paths.get(properties.getProperty("0.pending")),
                java.nio.file.Paths.get(properties.getProperty("0.target")), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        UpdaterHelper.installBatch(properties, marker);
        assertInstalled(properties, 0);
        assertInstalled(properties, 1);
        assertFalse(Files.exists(marker));
    }

    @Test
    void readsLegacySingleModPendingManifest() throws Exception {
        Properties properties = batch(1);
        Properties legacy = new Properties();
        legacy.setProperty("target", properties.getProperty("0.target"));
        legacy.setProperty("pending", properties.getProperty("0.pending"));
        Path marker = marker(legacy);
        UpdaterHelper.main(new String[] {marker.toString()});
        assertInstalled(properties, 0);
        assertFalse(Files.exists(marker));
    }

    private Properties batch(int count) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("count", Integer.toString(count));
        for (int index = 0; index < count; index++) {
            Path target = this.temporaryDirectory.resolve(index + ".jar");
            Path pending = this.temporaryDirectory.resolve(index + ".jar.pending");
            byte[] update = ("new" + index).getBytes(StandardCharsets.UTF_8);
            Files.write(target, "old".getBytes(StandardCharsets.UTF_8));
            Files.write(pending, update);
            properties.setProperty(index + ".target", target.toString());
            properties.setProperty(index + ".pending", pending.toString());
            properties.setProperty(index + ".sha256", UpdaterHelper.sha256(update));
        }
        return properties;
    }

    private Path marker(Properties properties) throws IOException {
        Path marker = this.temporaryDirectory.resolve("pending.properties");
        try (java.io.OutputStream output = Files.newOutputStream(marker)) {
            properties.store(output, "test");
        }
        return marker;
    }

    private void assertInstalled(Properties properties, int index) throws IOException {
        assertEquals("new" + index, new String(Files.readAllBytes(java.nio.file.Paths.get(properties.getProperty(index + ".target"))),
                StandardCharsets.UTF_8));
        assertFalse(Files.exists(java.nio.file.Paths.get(properties.getProperty(index + ".pending"))));
    }

    private static JsonObject release(String tag) {
        JsonObject release = new JsonObject();
        release.addProperty("tag_name", tag);
        release.addProperty("html_url", "https://example.invalid/release");
        release.add("assets", new JsonArray());
        return release;
    }

    private static JsonObject asset(JsonObject release, String name) {
        JsonObject asset = new JsonObject();
        asset.addProperty("name", name);
        asset.addProperty("browser_download_url", "https://example.invalid/" + name);
        release.getAsJsonArray("assets").add(asset);
        return asset;
    }

    @Test
    void comparesReleaseVersions() {
        assertTrue(UpdateManager.isNewer("1.1.0", "1.0.9"));
        assertTrue(UpdateManager.isNewer("v1.0.0", "1.0.0-test"));
        assertFalse(UpdateManager.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateManager.isNewer("1.0.0-test", "1.0.0"));
    }

    @Test
    void helperReplacesJarAndClearsMarker() throws Exception {
        Path target = this.temporaryDirectory.resolve("zsg-rooms.jar");
        Path pending = this.temporaryDirectory.resolve("update.jar.pending");
        Path marker = this.temporaryDirectory.resolve("pending.properties");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));
        Files.write(pending, "new".getBytes(StandardCharsets.UTF_8));
        Files.write(marker, "pending".getBytes(StandardCharsets.UTF_8));

        UpdaterHelper.main(new String[]{target.toString(), pending.toString(), marker.toString()});

        assertTrue(new String(Files.readAllBytes(target), StandardCharsets.UTF_8).equals("new"));
        assertFalse(Files.exists(pending));
        assertFalse(Files.exists(marker));
    }
}
