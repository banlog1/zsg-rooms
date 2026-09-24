package zsgrooms.modid.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import zsgrooms.modid.ZsgRooms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UpdateManager {
    public static final String DEFAULT_RELEASE_API = "https://api.github.com/repos/banlog1/zsg-rooms/releases/latest";
    private static final Path API_CONFIG = Paths.get("config", "zsg-rooms-update-url.txt");
    private static final Path UPDATE_DIR = Paths.get("config", "zsg-rooms", "update");
    private static final Path PENDING_CONFIG = UPDATE_DIR.resolve("pending.properties");
    private static final Path HELPER_JAR = UPDATE_DIR.resolve("updater-helper.jar");
    private static final Pattern VIEWER_JAR = Pattern.compile("zsg-replay-viewer-([0-9]+\\.[0-9]+\\.[0-9]+)\\.jar");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZSG Rooms Update");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean checked;
    private static volatile boolean checking;
    private static volatile UpdateRelease availableRelease;
    private static volatile String status = "";

    private UpdateManager() {
    }

    public static void checkForUpdates(Consumer<UpdateRelease> callback) {
        if (!UpdatePreferences.areChecksEnabled()) {
            return;
        }
        UpdateRelease cached = availableRelease;
        if (cached != null) {
            if (!UpdatePreferences.isSkipped(cached.version)) {
                callback.accept(cached);
            }
            return;
        }
        synchronized (UpdateManager.class) {
            if (checked || checking) {
                return;
            }
            checking = true;
        }
        EXECUTOR.execute(() -> {
            try {
                UpdateRelease release = fetchLatestRelease();
                if (release != null && !updates(release).isEmpty()
                        && !UpdatePreferences.isSkipped(release.version)) {
                    availableRelease = release;
                    callback.accept(release);
                }
            } catch (Exception exception) {
                status = "Update check failed: " + usefulMessage(exception);
                ZsgRooms.LOGGER.warn("[ZSG-Rooms] " + status);
            } finally {
                checking = false;
                checked = true;
            }
        });
    }

    public static void download(UpdateRelease release, Consumer<String> success, Consumer<String> failure) {
        if (release == null) {
            failure.accept("No update selected");
            return;
        }
        status = "Downloading " + release.version + "...";
        EXECUTOR.execute(() -> {
            List<Path> staged = new ArrayList<>();
            Path directory = null;
            boolean committed = false;
            try {
                if (Files.exists(PENDING_CONFIG)) {
                    throw new IOException("An update is already staged. Restart Minecraft first.");
                }
                List<UpdateArtifact> updates = updates(release);
                if (updates.isEmpty()) throw new IOException("Installed mods are already up to date");
                // Resolve every installed jar before downloading or staging either component.
                List<Path> targets = new ArrayList<>();
                for (UpdateArtifact artifact : updates) targets.add(installedJar(artifact.modId));
                Files.createDirectories(UPDATE_DIR);
                directory = Files.createTempDirectory(UPDATE_DIR, "batch-");
                Properties properties = new Properties();
                properties.setProperty("count", Integer.toString(updates.size()));
                for (int index = 0; index < updates.size(); index++) {
                    UpdateArtifact artifact = updates.get(index);
                    status = "Downloading " + artifact.label + " " + artifact.version + "...";
                    byte[] jar = requestBytes(artifact.downloadUrl, 32 * 1024 * 1024);
                    String expectedHash = artifact.sha256;
                    if ((expectedHash == null || expectedHash.isEmpty()) && artifact.checksumUrl != null) {
                        expectedHash = firstToken(new String(requestBytes(artifact.checksumUrl, 4096), StandardCharsets.UTF_8));
                    }
                    verifyDownload(artifact, jar, expectedHash);
                    Path pending = directory.resolve(artifact.fileName + ".pending");
                    staged.add(pending);
                    Files.write(pending, jar);
                    String prefix = index + ".";
                    properties.setProperty(prefix + "target", targets.get(index).toAbsolutePath().toString());
                    properties.setProperty(prefix + "pending", pending.toAbsolutePath().toString());
                    properties.setProperty(prefix + "sha256", expectedHash.toLowerCase(Locale.ROOT));
                }
                Path manifest = directory.resolve("pending.properties");
                staged.add(manifest);
                try (java.io.OutputStream output = Files.newOutputStream(manifest)) {
                    properties.store(output, "ZSG Rooms and Replay Viewer pending updates");
                }
                Files.move(manifest, PENDING_CONFIG, StandardCopyOption.ATOMIC_MOVE);
                committed = true;
                status = "Update ready. Restart Minecraft to install.";
                success.accept(status);
            } catch (Exception exception) {
                status = "Update failed: " + usefulMessage(exception);
                failure.accept(status);
            } finally {
                if (!committed && directory != null) {
                    for (Path path : staged) {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    }
                    try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
                }
            }
        });
    }

    public static void installOnExit() {
        try {
            if (!Files.isRegularFile(PENDING_CONFIG)) return;
            Files.createDirectories(UPDATE_DIR);
            Files.copy(currentJar(), HELPER_JAR, StandardCopyOption.REPLACE_EXISTING);
            String javaExecutable = javaExecutable();
            new ProcessBuilder(javaExecutable, "-cp", HELPER_JAR.toAbsolutePath().toString(),
                    UpdaterHelper.class.getName(), PENDING_CONFIG.toAbsolutePath().toString())
                    .start();
        } catch (Exception exception) {
            ZsgRooms.LOGGER.warn("[ZSG-Rooms] Could not start update installer: " + usefulMessage(exception));
        }
    }

    public static String getStatus() {
        return status;
    }

    public static List<UpdateArtifact> updates(UpdateRelease release) {
        String viewerVersion = FabricLoader.getInstance().getModContainer("zsg-replay-viewer")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(null);
        return release.updates(currentVersion(), viewerVersion);
    }

    private static UpdateRelease fetchLatestRelease() throws Exception {
        JsonObject root = new JsonParser().parse(new String(requestBytes(configuredApi(), 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
        return parseRelease(root);
    }

    static UpdateRelease parseRelease(JsonObject root) throws IOException {
        String version = cleanVersion(string(root, "tag_name"));
        String releaseUrl = string(root, "html_url");
        JsonArray assets = root.getAsJsonArray("assets");
        JsonObject jarAsset = null;
        JsonObject viewerAsset = null;
        String viewerVersion = null;
        String checksumUrl = null;
        String jarName = "zsg-rooms-" + version + ".jar";
        if (assets != null) {
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = string(asset, "name");
                Matcher viewerMatch = VIEWER_JAR.matcher(name);
                if (viewerMatch.matches()) {
                    if (viewerAsset != null) throw new IOException("Release has multiple Replay Viewer JARs");
                    viewerAsset = asset;
                    viewerVersion = viewerMatch.group(1);
                }
                if (name.equals(jarName + ".sha256")) {
                    checksumUrl = string(asset, "browser_download_url");
                } else if (name.equals(jarName)) {
                    jarAsset = asset;
                }
            }
        }
        if (version.isEmpty() || jarAsset == null) {
            throw new IOException("Latest release has no ZSG Rooms JAR");
        }
        UpdateArtifact viewer = null;
        if (viewerAsset != null) {
            String viewerName = string(viewerAsset, "name");
            String viewerChecksum = null;
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                if ((viewerName + ".sha256").equals(string(asset, "name"))) {
                    viewerChecksum = string(asset, "browser_download_url");
                }
            }
            viewer = new UpdateArtifact("zsg-replay-viewer", "Replay Viewer", viewerVersion,
                    string(viewerAsset, "browser_download_url"), digest(viewerAsset), viewerChecksum, viewerName);
        }
        return new UpdateRelease(version, releaseUrl, string(jarAsset, "browser_download_url"), digest(jarAsset),
                checksumUrl, string(jarAsset, "name"), viewer);
    }

    private static String digest(JsonObject asset) {
        String digest = string(asset, "digest");
        if (digest.startsWith("sha256:")) {
            digest = digest.substring("sha256:".length());
        } else {
            digest = "";
        }
        return digest;
    }

    private static byte[] requestBytes(String address, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "zsg-rooms/" + currentVersion());
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Update server returned HTTP " + code);
        }
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw new IOException("Update response is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static Path currentJar() throws Exception {
        URI location = UpdateManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path path = Paths.get(location).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
            throw new IOException("Updates can only be installed from a packaged mod JAR");
        }
        return path;
    }

    private static Path installedJar(String modId) throws Exception {
        if ("zsg-rooms".equals(modId)) return currentJar();
        ModOrigin origin = FabricLoader.getInstance().getModContainer(modId)
                .orElseThrow(() -> new IOException("Replay Viewer is not installed")).getOrigin();
        if (origin.getKind() != ModOrigin.Kind.PATH || origin.getPaths().size() != 1) {
            throw new IOException("Replay Viewer must be installed as a separate mod JAR");
        }
        Path path = origin.getPaths().get(0).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
            throw new IOException("Replay Viewer updates require a packaged mod JAR");
        }
        return path;
    }

    static void verifyDownload(UpdateArtifact artifact, byte[] jar, String expectedHash) throws Exception {
        if (expectedHash == null || !expectedHash.matches("(?i)[a-f0-9]{64}")) {
            throw new IOException(artifact.label + " release has no valid SHA-256 digest");
        }
        if (!UpdaterHelper.sha256(jar).equalsIgnoreCase(expectedHash)) {
            throw new IOException(artifact.label + " JAR failed SHA-256 verification");
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"fabric.mod.json".equals(entry.getName())) continue;
                ByteArrayOutputStream metadata = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    if (metadata.size() + read > 65536) throw new IOException("Mod metadata is too large");
                    metadata.write(buffer, 0, read);
                }
                JsonObject root = new JsonParser().parse(new String(metadata.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!artifact.modId.equals(string(root, "id")) || !artifact.version.equals(string(root, "version"))) {
                    throw new IOException(artifact.label + " JAR has unexpected mod metadata");
                }
                return;
            }
        }
        throw new IOException(artifact.label + " JAR has no mod metadata");
    }

    private static String configuredApi() throws IOException {
        String property = System.getProperty("zsgrooms.updateApi");
        if (property != null && !property.trim().isEmpty()) return property.trim();
        String environment = System.getenv("ZSG_ROOMS_UPDATE_API");
        if (environment != null && !environment.trim().isEmpty()) return environment.trim();
        if (Files.isRegularFile(API_CONFIG)) {
            String configured = new String(Files.readAllBytes(API_CONFIG), StandardCharsets.UTF_8).trim();
            if (!configured.isEmpty()) return configured;
        }
        return DEFAULT_RELEASE_API;
    }

    private static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(ZsgRooms.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    static boolean isNewer(String candidate, String current) {
        String[] left = cleanVersion(candidate).split("[-+]", 2)[0].split("\\.");
        String[] right = cleanVersion(current).split("[-+]", 2)[0].split("\\.");
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int leftPart = numberPart(left, index);
            int rightPart = numberPart(right, index);
            if (leftPart != rightPart) return leftPart > rightPart;
        }
        return current.contains("-") && !candidate.contains("-");
    }

    private static int numberPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "javaw.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String cleanVersion(String version) {
        String clean = version == null ? "" : version.trim();
        return clean.startsWith("v") || clean.startsWith("V") ? clean.substring(1) : clean;
    }

    private static String firstToken(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.split("\\s+", 2)[0];
    }

    private static String usefulMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
