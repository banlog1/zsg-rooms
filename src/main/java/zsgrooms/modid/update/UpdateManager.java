package zsgrooms.modid.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import zsgrooms.modid.ZsgRooms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class UpdateManager {
    public static final String DEFAULT_RELEASE_API = "https://api.github.com/repos/banlog1/zsg-rooms/releases/latest";
    private static final Path API_CONFIG = Paths.get("config", "zsg-rooms-update-url.txt");
    private static final Path UPDATE_DIR = Paths.get("config", "zsg-rooms", "update");
    private static final Path PENDING_CONFIG = UPDATE_DIR.resolve("pending.properties");
    private static final Path HELPER_JAR = UPDATE_DIR.resolve("updater-helper.jar");
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
                if (release != null && isNewer(release.version, currentVersion())
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
            try {
                byte[] jar = requestBytes(release.downloadUrl, 32 * 1024 * 1024);
                String expectedHash = release.sha256;
                if ((expectedHash == null || expectedHash.isEmpty()) && release.checksumUrl != null) {
                    expectedHash = firstToken(new String(requestBytes(release.checksumUrl, 4096), StandardCharsets.UTF_8));
                }
                if (expectedHash == null || expectedHash.isEmpty()) {
                    throw new IOException("Release has no SHA-256 digest");
                }
                String actualHash = sha256(jar);
                if (!actualHash.equalsIgnoreCase(expectedHash)) {
                    throw new IOException("Downloaded JAR failed SHA-256 verification");
                }

                Path target = currentJar();
                Files.createDirectories(UPDATE_DIR);
                Path pending = UPDATE_DIR.resolve("zsg-rooms-" + safeVersion(release.version) + ".jar.pending");
                Files.write(pending, jar);
                writePending(target, pending);
                status = "Update ready. Restart Minecraft to install.";
                success.accept(status);
            } catch (Exception exception) {
                status = "Update failed: " + usefulMessage(exception);
                failure.accept(status);
            }
        });
    }

    public static void installOnExit() {
        try {
            Properties pending = readPending();
            if (pending == null) {
                return;
            }
            Path target = Paths.get(pending.getProperty("target"));
            Path update = Paths.get(pending.getProperty("pending"));
            if (!Files.isRegularFile(update)) {
                Files.deleteIfExists(PENDING_CONFIG);
                return;
            }
            if (!Files.isRegularFile(target)) {
                return;
            }
            Files.createDirectories(UPDATE_DIR);
            Files.copy(target, HELPER_JAR, StandardCopyOption.REPLACE_EXISTING);
            String javaExecutable = javaExecutable();
            new ProcessBuilder(javaExecutable, "-cp", HELPER_JAR.toAbsolutePath().toString(),
                    UpdaterHelper.class.getName(), target.toAbsolutePath().toString(), update.toAbsolutePath().toString(),
                    PENDING_CONFIG.toAbsolutePath().toString())
                    .start();
        } catch (Exception exception) {
            ZsgRooms.LOGGER.warn("[ZSG-Rooms] Could not start update installer: " + usefulMessage(exception));
        }
    }

    public static String getStatus() {
        return status;
    }

    private static UpdateRelease fetchLatestRelease() throws Exception {
        JsonObject root = new JsonParser().parse(new String(requestBytes(configuredApi(), 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
        String version = cleanVersion(string(root, "tag_name"));
        String releaseUrl = string(root, "html_url");
        JsonArray assets = root.getAsJsonArray("assets");
        JsonObject jarAsset = null;
        String checksumUrl = null;
        if (assets != null) {
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = string(asset, "name");
                if (name.endsWith(".sha256")) {
                    checksumUrl = string(asset, "browser_download_url");
                } else if (name.startsWith("zsg-rooms-") && name.endsWith(".jar") && !name.contains("sources")) {
                    jarAsset = asset;
                }
            }
        }
        if (version.isEmpty() || jarAsset == null) {
            throw new IOException("Latest release has no ZSG Rooms JAR");
        }
        String digest = string(jarAsset, "digest");
        if (digest.startsWith("sha256:")) {
            digest = digest.substring("sha256:".length());
        } else {
            digest = "";
        }
        return new UpdateRelease(version, releaseUrl, string(jarAsset, "browser_download_url"), digest,
                checksumUrl, string(jarAsset, "name"));
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

    private static void writePending(Path target, Path pending) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("target", target.toAbsolutePath().toString());
        properties.setProperty("pending", pending.toAbsolutePath().toString());
        try (java.io.OutputStream output = Files.newOutputStream(PENDING_CONFIG)) {
            properties.store(output, "ZSG Rooms pending update");
        }
    }

    private static Properties readPending() throws IOException {
        if (!Files.isRegularFile(PENDING_CONFIG)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(PENDING_CONFIG)) {
            properties.load(input);
        }
        return properties;
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

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder value = new StringBuilder();
        for (byte part : digest) value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        return value.toString();
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

    private static String safeVersion(String version) {
        return cleanVersion(version).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String firstToken(String value) {
        String trimmed = value == null ? "" : value.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String usefulMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
