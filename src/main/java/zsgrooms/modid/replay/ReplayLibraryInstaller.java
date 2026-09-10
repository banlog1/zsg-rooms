package zsgrooms.modid.replay;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Background-only installer. Downloads are verified before becoming loadable JARs. */
final class ReplayLibraryInstaller {
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final String RESOURCE_ROOT = "/assets/zsg-rooms/replay/";

    private ReplayLibraryInstaller() {
    }

    static List<Path> prepare(Path directory, Consumer<String> status) throws IOException {
        Manifest manifest;
        try (InputStream input = ReplayLibraryInstaller.class.getResourceAsStream(RESOURCE_ROOT + "libraries.json")) {
            if (input == null) throw new IOException("Replay manifest missing");
            manifest = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Manifest.class);
        }
        return prepare(directory, manifest, ReplayLibraryInstaller::open, status);
    }

    static List<Path> prepare(Path directory, Manifest manifest, Source source, Consumer<String> status) throws IOException {
        if (manifest == null || manifest.artifacts == null || manifest.artifacts.isEmpty()) {
            throw new IOException("Replay manifest empty");
        }
        Set<String> names = new HashSet<>();
        for (Artifact artifact : manifest.artifacts) {
            if (artifact == null || artifact.file == null || !artifact.file.matches("[A-Za-z0-9._-]+\\.jar")
                    || !names.add(artifact.file) || artifact.sha256 == null || !artifact.sha256.matches("[a-f0-9]{64}")
                    || artifact.url == null || !(artifact.url.startsWith("https://")
                    || artifact.url.equals("resource:" + RESOURCE_ROOT + "zsg-replay-writer.jar"))) {
                throw new IOException("Invalid replay manifest entry");
            }
        }
        Files.createDirectories(directory);
        List<Path> paths = new ArrayList<>();
        int index = 0;
        for (Artifact artifact : manifest.artifacts) {
            index++;
            Path target = directory.resolve(artifact.file);
            if (!Files.isRegularFile(target) || !artifact.sha256.equals(hash(target))) {
                status.accept("Installing replay libraries " + index + "/" + manifest.artifacts.size() + "...");
                install(target, artifact, source);
            }
            paths.add(target);
        }
        return paths;
    }

    private static void install(Path target, Artifact artifact, Source source) throws IOException {
        Path partial = Files.createTempFile(target.getParent(), "replay-download-", ".part");
        try {
            MessageDigest digest = digest();
            long size = 0;
            try (InputStream input = source.open(artifact.url); OutputStream output = Files.newOutputStream(partial)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_FILE_BYTES) throw new IOException("Replay library exceeds size limit");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (!artifact.sha256.equals(hex(digest.digest()))) throw new IOException("Replay library checksum mismatch");
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static InputStream open(String location) throws IOException {
        if (location.startsWith("resource:")) {
            InputStream input = ReplayLibraryInstaller.class.getResourceAsStream(location.substring(9));
            if (input == null) throw new IOException("Replay writer missing");
            return input;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(location).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "ZSG-Rooms-Replay-Setup");
        // Pinned artifacts are fetched directly; do not follow arbitrary redirect destinations.
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("Replay download failed");
            if (connection.getContentLengthLong() > MAX_FILE_BYTES) throw new IOException("Replay library exceeds size limit");
            return new java.io.FilterInputStream(connection.getInputStream()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        connection.disconnect();
                    }
                }
            };
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
    }

    static String hash(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            value.append(Character.forDigit((b & 255) >>> 4, 16));
            value.append(Character.forDigit(b & 15, 16));
        }
        return value.toString();
    }

    interface Source {
        InputStream open(String location) throws IOException;
    }

    static final class Manifest {
        List<Artifact> artifacts;
    }

    static final class Artifact {
        String file;
        String sha256;
        String url;
    }
}
