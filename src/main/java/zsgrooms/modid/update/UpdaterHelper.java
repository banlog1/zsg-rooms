package zsgrooms.modid.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;

public final class UpdaterHelper {
    private UpdaterHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            Path marker = Paths.get(args[0]);
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(marker)) {
                properties.load(input);
            }
            if (!properties.containsKey("count")) {
                main(new String[] {properties.getProperty("target"), properties.getProperty("pending"), args[0]});
                return;
            }
            for (int attempt = 0; attempt < 240; attempt++) {
                try {
                    installBatch(properties, marker);
                    return;
                } catch (IOException exception) {
                    Thread.sleep(500L);
                }
            }
            return;
        }
        if (args.length != 3) {
            return;
        }
        Path target = Paths.get(args[0]);
        Path pending = Paths.get(args[1]);
        Path marker = Paths.get(args[2]);
        for (int attempt = 0; attempt < 240; attempt++) {
            try {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(marker);
                return;
            } catch (Exception exception) {
                Thread.sleep(500L);
            }
        }
    }

    static void installBatch(Properties properties, Path marker) throws Exception {
        int count = Integer.parseInt(properties.getProperty("count"));
        if (count < 1 || count > 2) throw new IOException("Invalid update count");
        // Validate the whole batch before changing either jar. A previous attempt may have
        // installed one component before Windows released its lock on the other one.
        for (int index = 0; index < count; index++) {
            Path target = Paths.get(properties.getProperty(index + ".target"));
            Path pending = Paths.get(properties.getProperty(index + ".pending"));
            String expected = properties.getProperty(index + ".sha256", "");
            if (!Files.isRegularFile(target)) throw new IOException("Installed mod JAR is missing");
            Path source = Files.exists(pending) ? pending : target;
            if (!expected.equals(sha256(Files.readAllBytes(source)))) {
                throw new IOException("Pending update checksum mismatch");
            }
        }
        for (int index = 0; index < count; index++) {
            Path target = Paths.get(properties.getProperty(index + ".target"));
            Path pending = Paths.get(properties.getProperty(index + ".pending"));
            if (Files.exists(pending)) Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(marker);
    }

    static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder value = new StringBuilder();
        for (byte part : digest) value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        return value.toString();
    }
}
