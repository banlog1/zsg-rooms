package zsgrooms.modid.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsImportInstanceTest {
    @TempDir Path temporary;

    @Test void multiMcUsesDisplayNameAndMinecraftComponentNotFolderOrLoaderVersion() throws Exception {
        Path game = instance("portable", "unfriendly-folder", "name=My Speedruns\nIntendedVersion=1.8.9\n", "1.16.1");
        write(temporary.resolve("portable/multimc.cfg"), "");
        SettingsImportInstance info = SettingsImportInstance.read(game);
        assertEquals("My Speedruns", info.name);
        assertEquals("1.16.1", info.minecraftVersion);
        assertEquals("MultiMC", info.launcher);
        assertEquals("Minecraft 1.16.1 | MultiMC", info.details());
        assertEquals(game.toRealPath(), info.directory);
        assertEquals(info.name, SettingsImportInstance.read(game.getParent()).name);
        assertEquals("name=My Speedruns\nIntendedVersion=1.8.9\n", new String(Files.readAllBytes(game.getParent().resolve("instance.cfg")), StandardCharsets.UTF_8));
    }

    @Test void prismGeneralSectionSupportsQuotedNamesEscapesAndUnicode() throws Exception {
        Path game = instance("prism-portable", "profile", "\uFEFF[General]\nConfigVersion=1.3\nname=\"Caf\\xe9, Runs; \\\"AA\\\" \\x03a9\"\n[Other]\nname=Wrong\n", "1.21.1");
        write(temporary.resolve("prism-portable/prismlauncher.cfg"), "");
        Path alternate = game.resolveSibling("minecraft"); Files.move(game, alternate);
        SettingsImportInstance info = SettingsImportInstance.read(alternate);
        assertEquals("Caf\u00e9, Runs; \"AA\" \u03a9", info.name);
        assertEquals("Prism Launcher", info.launcher);
        assertEquals("1.21.1", info.minecraftVersion);
    }

    @Test void oldNamesPreserveQuotesEqualsAndEscapedHash() throws Exception {
        Path game = instance("old", "profile", "name=My \"Practice\" \\#1=AA # comment\n", "1.16.1");
        assertEquals("My \"Practice\" #1=AA", SettingsImportInstance.read(game).name);
        write(game.getParent().resolve("instance.cfg"), "name=\"Runs, practice; AA=1\"\n");
        assertEquals("Runs, practice; AA=1", SettingsImportInstance.read(game).name);
    }

    @Test void blankNameFallsBackAndLegacyVersionStillWorks() throws Exception {
        Path game = instance("old", "My instance", "name=  \nIntendedVersion=1.7.10\n", null);
        SettingsImportInstance info = SettingsImportInstance.read(game);
        assertEquals("My instance", info.name);
        assertEquals("1.7.10", info.minecraftVersion);
        assertEquals("Unknown launcher", info.launcher);
    }

    @Test void malformedMetadataDoesNotHideValidSettings() throws Exception {
        Path game = instance("portable", "Fallback", "name=Good name\n", "1.16.1");
        write(game.getParent().resolve("mmc-pack.json"), "{bad");
        SettingsImportInstance info = SettingsImportInstance.read(game);
        assertEquals("Good name", info.name); assertEquals("", info.minecraftVersion);
        assertTrue(info.details().contains("version unknown"));
        write(game.getParent().resolve("instance.cfg"), String.join("", Collections.nCopies(1024 * 1024 + 1, "x")));
        assertEquals("Fallback", SettingsImportInstance.read(game).name);
    }

    @Test void ambiguousOrMissingLauncherIsNotGuessedFromFolderName() throws Exception {
        Path game = instance("PrismLauncher", "profile", "name=One\n", null);
        assertEquals("Unknown launcher", SettingsImportInstance.read(game).launcher);
        write(temporary.resolve("PrismLauncher/prismlauncher.cfg"), "");
        write(temporary.resolve("PrismLauncher/multimc.cfg"), "");
        assertEquals("Unknown launcher", SettingsImportInstance.read(game).launcher);
    }

    @Test void unknownVersionDoesNotPickFabricVersionOrOptionsDataVersion() throws Exception {
        Path game = instance("launcher", "profile", "name=One\n", null);
        write(game.getParent().resolve("mmc-pack.json"), "{\"components\":[{\"uid\":\"net.fabricmc.fabric-loader\",\"version\":\"0.19.3\"},{\"uid\":\"net.minecraft\",\"version\":42}]}");
        assertEquals("", SettingsImportInstance.read(game).minecraftVersion);
    }

    @Test void discoverySortsByDisplayNameKeepsDuplicatesAndExcludesCurrent() throws Exception {
        Path current = instance("launcher", "current", "name=Current\n", "1.16.1");
        Path z = instance("launcher", "first-folder", "name=Zulu\n", "1.16.1");
        Path a = instance("launcher", "second-folder", "name=Alpha\n", "1.16.1");
        Path duplicate = instance("launcher", "third-folder", "name=Alpha\n", "1.20.1");
        List<SettingsImportInstance> found = SettingsImportInstance.discover(current);
        found.removeIf(info -> !info.directory.startsWith(temporary));
        assertEquals(3, found.size());
        assertEquals(a.toRealPath(), found.get(0).directory);
        assertEquals(duplicate.toRealPath(), found.get(1).directory);
        assertEquals(z.toRealPath(), found.get(2).directory);
    }

    @Test void displayTextCannotInjectFormattingOrExtraLines() throws Exception {
        Path game = instance("launcher", "profile", "name=\u00a7cRed\\nNext\u202eX\n", "1.16.1");
        assertEquals("Red Next X", SettingsImportInstance.read(game).name);
    }

    @Test void plainMinecraftFolderRemainsSelectableWithoutLauncherMetadata() throws Exception {
        Path game = Files.createDirectories(temporary.resolve("plain/.minecraft"));
        write(game.resolve("options.txt"), "version:2567\n");
        SettingsImportInstance info = SettingsImportInstance.read(game);
        assertEquals("plain", info.name); assertEquals("", info.minecraftVersion);
        assertEquals("Unknown launcher", info.launcher);
    }

    @Test void defaultMinecraftHasUsefulNameInsteadOfParentDirectoryName() throws Exception {
        Path home = temporary.resolve("home");
        Path game = Files.createDirectories(home.resolve(".minecraft"));
        write(game.resolve("options.txt"), "version:2567\n");
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            assertEquals("Default Minecraft", SettingsImportInstance.read(game).name);
        } finally { System.setProperty("user.home", previous); }
    }

    private Path instance(String launcher, String folder, String config, String version) throws Exception {
        Path root = temporary.resolve(launcher).resolve("instances").resolve(folder);
        Path game = Files.createDirectories(root.resolve(".minecraft"));
        write(game.resolve("options.txt"), "version:2567\n");
        write(root.resolve("instance.cfg"), config);
        if (version != null) write(root.resolve("mmc-pack.json"), "{\"components\":[{\"uid\":\"net.fabricmc.fabric-loader\",\"version\":\"0.19.3\"},{\"uid\":\"net.minecraft\",\"version\":\"" + version + "\"}]}");
        return game;
    }
    private static void write(Path file, String text) throws Exception { Files.write(file, text.getBytes(StandardCharsets.UTF_8)); }
}
