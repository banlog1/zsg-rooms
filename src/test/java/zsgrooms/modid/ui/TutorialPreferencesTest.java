package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TutorialPreferencesTest {
    @TempDir
    Path directory;

    @Test
    public void newStatePromptsWithoutChoosingOrCreatingFiles() {
        Path path = directory.resolve("config/tutorial.properties");
        TutorialPreferences preferences = new TutorialPreferences(path);

        assertTrue(preferences.shouldPrompt());
        assertNull(preferences.getChoice());
        assertFalse(Files.exists(path.getParent()));
    }

    @Test
    public void everyChoiceRoundTripsIncludingExplicitNone() throws Exception {
        for (TutorialPreferences.Choice choice : TutorialPreferences.Choice.values()) {
            Path path = directory.resolve(choice.name() + "/tutorial.properties");
            TutorialPreferences preferences = new TutorialPreferences(path);
            preferences.choose(choice);

            assertFalse(preferences.shouldPrompt());
            assertEquals(choice, preferences.getChoice());
            TutorialPreferences restored = new TutorialPreferences(path);
            assertFalse(restored.shouldPrompt());
            assertEquals(choice, restored.getChoice());

            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }
            assertEquals("1", properties.getProperty("version"));
            assertEquals(choice.name(), properties.getProperty("choice"));
            assertEquals(2, properties.size());
            try (Stream<Path> files = Files.list(path.getParent())) {
                assertEquals(1L, files.count());
            }
        }
    }

    @Test
    public void corruptOrUnrecognizedPropertiesPromptWithoutRewriting() throws Exception {
        Path path = directory.resolve("tutorial.properties");
        String[] invalid = {
                "", "not properties", "version=1\n", "choice=QUICK\n",
                "version=2\nchoice=QUICK\n", "version=invalid\nchoice=FULL\n",
                "version=1\nchoice=UNKNOWN\n", "version=1\nchoice=quick\n",
                "version=1\nchoice=\n",
                "version=1\nchoice=FULL\nbroken=\\uINVALID\n"
        };
        for (String content : invalid) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes);
            TutorialPreferences preferences = new TutorialPreferences(path);

            assertTrue(preferences.shouldPrompt(), content);
            assertNull(preferences.getChoice(), content);
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
    }

    @Test
    public void repeatedChoicesReplaceTheSavedChoice() throws Exception {
        Path path = directory.resolve("tutorial.properties");
        TutorialPreferences preferences = new TutorialPreferences(path);
        for (TutorialPreferences.Choice choice : TutorialPreferences.Choice.values()) {
            for (int repeat = 0; repeat < 2; repeat++) {
                preferences.choose(choice);
                assertFalse(preferences.shouldPrompt());
                assertEquals(choice, preferences.getChoice());
                assertEquals(choice, new TutorialPreferences(path).getChoice());
            }
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    public void nullIsNotAChoiceAndDoesNotDamageASavedChoice() throws Exception {
        Path path = directory.resolve("tutorial.properties");
        TutorialPreferences preferences = new TutorialPreferences(path);
        assertThrows(NullPointerException.class, () -> preferences.choose(null));
        assertTrue(preferences.shouldPrompt());
        assertNull(preferences.getChoice());
        assertFalse(Files.exists(path));

        preferences.choose(TutorialPreferences.Choice.QUICK);
        byte[] saved = Files.readAllBytes(path);
        assertThrows(NullPointerException.class, () -> preferences.choose(null));
        assertFalse(preferences.shouldPrompt());
        assertEquals(TutorialPreferences.Choice.QUICK, preferences.getChoice());
        assertArrayEquals(saved, Files.readAllBytes(path));
    }

    @Test
    public void failedSaveRemembersTheChoiceAndCanBeRetried() throws Exception {
        Path parent = directory.resolve("config");
        byte[] blocker = "not a directory".getBytes(StandardCharsets.UTF_8);
        Files.write(parent, blocker);
        Path path = parent.resolve("tutorial.properties");
        TutorialPreferences preferences = new TutorialPreferences(path);
        assertTrue(preferences.shouldPrompt());

        assertThrows(IOException.class, () -> preferences.choose(TutorialPreferences.Choice.NONE));
        assertFalse(preferences.shouldPrompt());
        assertEquals(TutorialPreferences.Choice.NONE, preferences.getChoice());
        assertArrayEquals(blocker, Files.readAllBytes(parent));
        assertTrue(new TutorialPreferences(path).shouldPrompt());

        Files.delete(parent);
        preferences.choose(TutorialPreferences.Choice.NONE);
        assertEquals(TutorialPreferences.Choice.NONE, new TutorialPreferences(path).getChoice());
    }

    @Test
    public void failedReplacementCleansTemporaryFileAndPreservesExistingContents() throws Exception {
        Path path = Files.createDirectory(directory.resolve("tutorial.properties"));
        Path existing = path.resolve("existing.properties");
        TutorialPreferences saved = new TutorialPreferences(existing);
        saved.choose(TutorialPreferences.Choice.QUICK);
        byte[] bytes = Files.readAllBytes(existing);
        TutorialPreferences preferences = new TutorialPreferences(path);
        assertTrue(preferences.shouldPrompt());

        assertThrows(IOException.class, () -> preferences.choose(TutorialPreferences.Choice.FULL));
        assertFalse(preferences.shouldPrompt());
        assertEquals(TutorialPreferences.Choice.FULL, preferences.getChoice());
        assertArrayEquals(bytes, Files.readAllBytes(existing));
        assertEquals(TutorialPreferences.Choice.QUICK, new TutorialPreferences(existing).getChoice());
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
        try (Stream<Path> files = Files.list(path)) {
            assertEquals(1L, files.count());
        }
    }
}
