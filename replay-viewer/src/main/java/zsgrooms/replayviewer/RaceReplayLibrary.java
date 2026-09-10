// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small local library. Originals are never overwritten, moved or extracted. */
final class RaceReplayLibrary {
    private final Path recordings;
    private final Path imported;
    private final Set<Path> sourceDirectories = new java.util.LinkedHashSet<>();

    RaceReplayLibrary(Path gameDirectory) {
        recordings = gameDirectory.resolve("replay_recordings");
        imported = recordings.resolve("zsg-shared");
    }

    List<Entry> scan(RaceRecording current, RaceRecording.Race race) throws IOException {
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        add(result, seen, current, race.group());
        sourceDirectories.add(current.path.getParent());
        if (sourceDirectories.size() > 16) sourceDirectories.remove(sourceDirectories.iterator().next());
        Set<Path> directories = new HashSet<>(sourceDirectories);
        directories.add(recordings); directories.add(imported); directories.add(current.path.getParent());
        int count = 0;
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.mcpr")) {
                for (Path path : files) {
                    if (++count > 2048) throw new IOException("Library scan limit reached (2048 files)");
                    try { add(result, seen, RaceRecording.read(path), race.group()); }
                    catch (IOException ignored) { /* Unrelated/legacy/incomplete files remain individually playable. */ }
                }
            }
        }
        result.sort(Comparator.comparing(Entry::label).thenComparing(entry -> entry.recording.recordingId.toString()));
        return result;
    }

    private static void add(List<Entry> entries, Set<String> seen, RaceRecording recording, String group) {
        for (RaceRecording.Race race : recording.races) {
            if (race.group().equals(group) && seen.add(recording.recordingId + "/" + race.id)) entries.add(new Entry(recording, race));
        }
    }

    Path importReplay(Path source, String group) throws IOException {
        RaceRecording original = RaceRecording.read(source);
        if (original.races.stream().noneMatch(race -> race.group().equals(group))) throw new IOException("Recording belongs to another race/test group");
        Files.createDirectories(imported);
        Path destination = imported.resolve(original.recordingId + ".mcpr");
        if (Files.exists(destination)) {
            RaceRecording existing = RaceRecording.read(destination);
            if (!existing.recordingId.equals(original.recordingId)
                    || existing.races.stream().noneMatch(race -> race.group().equals(group))) throw new IOException("Conflicting recording ID");
            return destination;
        }
        Path temporary = Files.createTempFile(imported, "import-", ".part");
        try {
            try (InputStream input = Files.newInputStream(source); OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long copied = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > RaceRecording.MAX_FILE || Thread.currentThread().isInterrupted()) throw new IOException("Import stopped");
                    output.write(buffer, 0, count);
                }
            }
            RaceRecording copy = RaceRecording.read(temporary);
            if (!copy.recordingId.equals(original.recordingId)
                    || copy.races.stream().noneMatch(race -> race.group().equals(group))) throw new IOException("Source changed during import");
            Files.move(temporary, destination);
            return destination;
        } finally { Files.deleteIfExists(temporary); }
    }

    static final class Entry {
        final RaceRecording recording;
        final RaceRecording.Race race;
        Entry(RaceRecording recording, RaceRecording.Race race) { this.recording = recording; this.race = race; }
        String label() {
            String take = recording.recordingId.toString().substring(0, 8);
            return recording.name + " - " + (race.testGroup == null ? "Recording " : "Test take ") + take
                    + (recording.races.size() > 1 ? " / " + (recording.races.indexOf(race) + 1) : "");
        }
    }
}
