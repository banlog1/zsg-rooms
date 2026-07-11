package zsgrooms.modid.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class UpdaterHelper {
    private UpdaterHelper() {
    }

    public static void main(String[] args) throws Exception {
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
}
