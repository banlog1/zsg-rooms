package zsgrooms.modid.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
