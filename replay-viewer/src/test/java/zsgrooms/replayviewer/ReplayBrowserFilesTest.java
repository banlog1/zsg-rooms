// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ReplayBrowserFilesTest {
    @TempDir Path temp;
    private static final String GENERATED = "12345678-1234-4234-8234-123456789abc.mcpr";

    @Test void readsLegacyMetadataWithoutRequiringRoomsManifest() throws Exception {
        Path file = archive(temp.resolve(GENERATED), "{\"serverName\":\"Legacy run\",\"duration\":61000,\"date\":123456}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        assertTrue(entry.playable());
        assertEquals("Legacy run", entry.name);
        assertEquals(61000, entry.duration);
        assertEquals(123456, entry.date);
    }

    @Test void roomsNameIsUsedButMalformedOptionalManifestDoesNotBlockPlayback() throws Exception {
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(archive(temp.resolve(GENERATED), "{\"duration\":4000}", "{\"displayName\":\"Runner\"}"));
        assertEquals("Runner - ZSG replay", entry.name);
        assertTrue(ReplayBrowserFiles.read(archive(temp.resolve("other.mcpr"), "{\"duration\":4000}", "not json")).playable());
    }

    @Test void scansDefaultDestinationAndBrowseFolderWithoutDuplicates() throws Exception {
        Path defaultFolder = temp.resolve("replay_recordings");
        Path custom = temp.resolve("custom");
        archive(defaultFolder.resolve("old.mcpr"), "{\"duration\":1,\"date\":100}", null);
        archive(custom.resolve("new.MCPR"), "{\"duration\":2,\"date\":200}", null);
        Files.write(custom.resolve("ignored.txt"), new byte[] {1});
        Path config = temp.resolve("config/zsgrooms-replay-storage.properties");
        Files.createDirectories(config.getParent());
        Properties values = new Properties(); values.setProperty("recordingDirectory", custom.toString());
        try (OutputStream output = Files.newOutputStream(config)) { values.store(output, "test"); }
        ReplayBrowserFiles.Result result = ReplayBrowserFiles.scan(temp, custom);
        assertEquals(2, result.entries.size());
        assertEquals("new.MCPR", result.entries.get(0).path.getFileName().toString());
        assertTrue(result.warning.isEmpty());
        assertFalse(result.limited);
    }

    @Test void incompleteAndInvalidFilesRemainVisibleButCannotPlay() throws Exception {
        Path folder = temp.resolve("replay_recordings"); Files.createDirectories(folder);
        Files.write(folder.resolve("unfinished.mcpr"), new byte[] {1, 2});
        archive(folder.resolve("bad-duration.mcpr"), "{\"duration\":-1}", null);
        ReplayBrowserFiles.Result result = ReplayBrowserFiles.scan(temp, null);
        assertEquals(2, result.entries.size());
        assertTrue(result.entries.stream().noneMatch(ReplayBrowserFiles.Entry::playable));
    }

    @Test void emptyScanDoesNotCreateDirectoriesOrTouchFiles() throws Exception {
        assertTrue(ReplayBrowserFiles.scan(temp, temp.resolve("missing")).entries.isEmpty());
        assertFalse(Files.exists(temp.resolve("replay_recordings")));
        assertFalse(Files.exists(temp.resolve("missing")));
    }

    @Test void includesGroupedImportsWithoutRecursingIntoArbitraryFolders() throws Exception {
        Path folder = temp.resolve("replay_recordings");
        archive(folder.resolve("zsg-shared/imported.mcpr"), "{\"duration\":4}", null);
        archive(folder.resolve("unrelated/not-listed.mcpr"), "{\"duration\":4}", null);
        assertEquals(1, ReplayBrowserFiles.scan(temp, null).entries.size());
    }

    @Test void searchesNamesAndFileNamesWithoutCaseSensitivity() throws Exception {
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(archive(temp.resolve(GENERATED), "{\"serverName\":\"Runner One\",\"duration\":3}", null));
        assertTrue(entry.matches("runner"));
        assertTrue(entry.matches("123456789ABC"));
        assertFalse(entry.matches("other runner"));
    }

    @Test void excessiveNestingCannotReachTheRecursiveTreeParser() throws Exception {
        StringBuilder nested = new StringBuilder("{\"duration\":1,\"nested\":");
        for (int i = 0; i < 1000; i++) nested.append('[');
        nested.append('0');
        for (int i = 0; i < 1000; i++) nested.append(']');
        nested.append('}');
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(archive(temp.resolve("deep.mcpr"), nested.toString(), null));
        assertFalse(entry.playable());
        assertEquals("Excessive metadata nesting", entry.error);
        assertTrue(ReplayBrowserFiles.read(archive(temp.resolve("deep-optional.mcpr"), "{\"duration\":2}", nested.toString())).playable());
    }

    @Test void corruptStorageSettingDoesNotHideDefaultRecordings() throws Exception {
        archive(temp.resolve("replay_recordings/default.mcpr"), "{\"duration\":4}", null);
        Path config = temp.resolve("config/zsgrooms-replay-storage.properties");
        Files.createDirectories(config.getParent());
        Files.write(config, "recordingDirectory=\\uZZZZ".getBytes(StandardCharsets.UTF_8));
        ReplayBrowserFiles.Result result = ReplayBrowserFiles.scan(temp, null);
        assertEquals(1, result.entries.size());
        assertFalse(result.warning.isEmpty());
    }

    @Test void deletionRemovesOnlyTheSelectedArchive() throws Exception {
        Path selected = archive(temp.resolve("selected.mcpr"), "{\"duration\":4}", null);
        Path other = archive(temp.resolve("other.mcpr"), "{\"duration\":4}", null);
        Path cache = Files.createDirectory(temp.resolve("selected.mcpr.cache"));
        Files.write(cache.resolve("keep"), new byte[] {1});
        ReplayBrowserFiles.delete(ReplayBrowserFiles.read(selected));
        assertFalse(Files.exists(selected));
        assertTrue(Files.exists(other));
        assertTrue(Files.exists(cache.resolve("keep")));
    }

    @Test void corruptRecordingsCanAlsoBeDeleted() throws Exception {
        Path file = temp.resolve("broken.mcpr");
        Files.write(file, new byte[] {1, 2, 3});
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        assertFalse(entry.playable());
        ReplayBrowserFiles.delete(entry);
        assertFalse(Files.exists(file));
    }

    @Test void changedRecordingIsNotDeletedAfterConfirmationWasPrepared() throws Exception {
        Path file = archive(temp.resolve("changed.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        Files.write(file, new byte[] {7});
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.delete(entry));
        assertArrayEquals(new byte[] {7}, Files.readAllBytes(file));
    }

    @Test void sameSizeButNewModificationTimeIsNotDeleted() throws Exception {
        Path file = archive(temp.resolve("modified.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 2000));
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.delete(entry));
        assertTrue(Files.exists(file));
    }

    @Test void replacementWithSameSizeAndTimestampIsNotDeleted() throws Exception {
        Path file = archive(temp.resolve("replaced.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        java.nio.file.attribute.FileTime modified = Files.getLastModifiedTime(file);
        Path replacement = temp.resolve("replacement.tmp");
        Files.copy(file, replacement);
        Object before = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        Object after = Files.readAttributes(replacement, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        org.junit.jupiter.api.Assumptions.assumeTrue(before != null && after != null && !before.equals(after));
        Files.move(replacement, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(file, modified);
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.delete(entry));
        assertTrue(Files.exists(file));
    }

    @Test void deletionRejectsDirectoriesAndUnrelatedFileTypes() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("folder.mcpr"));
        Path other = archive(temp.resolve("unrelated.zip"), "{\"duration\":4}", null);
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.delete(ReplayBrowserFiles.read(folder)));
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.delete(ReplayBrowserFiles.read(other)));
        assertTrue(Files.exists(folder));
        assertTrue(Files.exists(other));
    }

    @Test void missingSelectionDoesNotDeleteAnyOtherFile() throws Exception {
        Path file = archive(temp.resolve("missing.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        Files.delete(file);
        Path other = archive(temp.resolve("other.mcpr"), "{\"duration\":4}", null);
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.delete(entry));
        assertTrue(Files.exists(other));
    }

    @Test void customFilenameTakesPrecedenceOverBothMetadataTitles() throws Exception {
        Path file = archive(temp.resolve("My best run.MCPR"), "{\"serverName\":\"Generic server\",\"duration\":4}", "{\"displayName\":\"Runner\"}");
        assertEquals("My best run", ReplayBrowserFiles.read(file).name);
        assertEquals("", ReplayBrowserFiles.renameError("My best run.MCPR"));
        assertEquals("", ReplayBrowserFiles.renameError("COM10.mcpr"));
    }

    @Test void renameIsSameDirectoryAndPreservesArchiveHashMetadataAndSiblings() throws Exception {
        Path original = archive(temp.resolve("originals/untouched.mcpr"), "{\"serverName\":\"Generic\",\"duration\":42,\"date\":123456}", "{\"displayName\":\"Runner\"}");
        Path directory = Files.createDirectories(temp.resolve("replay_recordings"));
        Path copy = Files.copy(original, directory.resolve(GENERATED));
        Path sibling = Files.copy(original, directory.resolve("sibling.mcpr"));
        Path cache = Files.createDirectory(directory.resolve(GENERATED + ".cache"));
        Path marker = Files.write(cache.resolve("keep"), new byte[] {1, 2, 3});
        byte[] hash = hash(original);
        ReplayBrowserFiles.Entry selected = ReplayBrowserFiles.read(copy);
        assertEquals("Runner - ZSG replay", selected.name);
        assertEquals("", ReplayBrowserFiles.renameError("Personal best.mcpr"));
        assertTrue(Files.exists(copy)); // Preparing/validating the draft has no side effects.
        Path renamed = ReplayBrowserFiles.rename(selected, "Personal best.mcpr");
        assertEquals(directory.resolve("Personal best.mcpr"), renamed);
        assertFalse(Files.exists(copy));
        assertArrayEquals(hash, hash(renamed));
        assertArrayEquals(hash, hash(original));
        assertArrayEquals(hash, hash(sibling));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(marker));
        ReplayBrowserFiles.Entry refreshed = ReplayBrowserFiles.scan(temp, null).entries.stream()
                .filter(value -> value.path.equals(renamed.toAbsolutePath())).findFirst().get();
        assertEquals("Personal best", refreshed.name);
        assertEquals(42, refreshed.duration);
        assertEquals(123456, refreshed.date);
        assertTrue(refreshed.playable());
        assertTrue(refreshed.matches("PERSONAL BEST"));
        assertEquals("Personal best", ReplayBrowserFiles.read(renamed).name);
        ReplayBrowserFiles.rename(refreshed, GENERATED);
        assertArrayEquals(hash, hash(copy));
        assertArrayEquals(hash, hash(original));
        assertEquals("Runner - ZSG replay", ReplayBrowserFiles.read(copy).name);
    }

    @Test void invalidRenameDraftsCannotTouchTheSelectedArchive() throws Exception {
        Path file = archive(temp.resolve("source.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        byte[] hash = hash(file);
        String[] invalid = {null, "", " ", ".mcpr", "..mcpr", "...mcpr", "name", "name.zip", "name.mcpr.",
                "../escape.mcpr", "..\\escape.mcpr", "nested/run.mcpr", "nested\\run.mcpr", "/run.mcpr", "C:\\run.mcpr",
                "C:run.mcpr", "name:stream.mcpr", "run?.mcpr", "run*.mcpr", "run|.mcpr", "run<.mcpr", "run>.mcpr",
                "run\".mcpr", " run.mcpr", "run.mcpr ", "run .mcpr", "run..mcpr", "\u0000.mcpr", "run\n.mcpr",
                "run\t.mcpr", "run\u007f.mcpr", "run\u0085.mcpr", "run\u202e.mcpr", "\ud800.mcpr", "\udc00.mcpr",
                "CON.mcpr", "prn.mcpr", "AUX.extra.mcpr", "NUL.mcpr", "COM1.mcpr", "com9.mcpr", "LPT1.mcpr",
                "lpt9.extra.mcpr", "CONIN$.mcpr", "CONOUT$.mcpr", "COM\u00b9.mcpr", "LPT\u00b2.mcpr", "NUL .extra.mcpr",
                String.join("", java.util.Collections.nCopies(251, "x")) + ".mcpr",
                String.join("", java.util.Collections.nCopies(126, "\u00e9")) + ".mcpr"};
        for (String name : invalid) {
            assertFalse(ReplayBrowserFiles.renameError(name).isEmpty(), "Accepted invalid draft: " + name);
            assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(entry, name), "Renamed invalid draft: " + name);
            assertArrayEquals(hash, hash(file));
        }
        try (java.util.stream.Stream<Path> files = Files.list(temp)) { assertEquals(1, files.count()); }
    }

    @Test void renameNeverOverwritesFilesDirectoriesOrTheSourceItself() throws Exception {
        Path source = archive(temp.resolve("source.mcpr"), "{\"duration\":4}", null);
        Path replay = archive(temp.resolve("occupied.mcpr"), "{\"duration\":9}", null);
        Path unrelated = Files.write(temp.resolve("unrelated.mcpr"), new byte[] {7, 8});
        Path directory = Files.createDirectory(temp.resolve("directory.mcpr"));
        byte[] sourceHash = hash(source), targetHash = hash(replay);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(source);
        for (String name : new String[] {"occupied.mcpr", "unrelated.mcpr", "directory.mcpr", "source.mcpr"}) {
            assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> ReplayBrowserFiles.rename(entry, name));
            assertArrayEquals(sourceHash, hash(source));
            assertArrayEquals(targetHash, hash(replay));
            assertArrayEquals(new byte[] {7, 8}, Files.readAllBytes(unrelated));
            assertTrue(Files.isDirectory(directory));
        }
    }

    @Test void renameRefusesSizeTimestampAndIdentityChanges() throws Exception {
        Path file = archive(temp.resolve("changed.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 2000));
        final ReplayBrowserFiles.Entry timestampEntry = entry;
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(timestampEntry, "renamed.mcpr"));
        entry = ReplayBrowserFiles.read(file);
        Files.write(file, new byte[] {7});
        final ReplayBrowserFiles.Entry sizeEntry = entry;
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(sizeEntry, "renamed.mcpr"));
        assertArrayEquals(new byte[] {7}, Files.readAllBytes(file));
        assertFalse(Files.exists(temp.resolve("renamed.mcpr")));
    }

    @Test void renameRefusesSameSizeAndTimestampReplacement() throws Exception {
        Path file = archive(temp.resolve("replaced.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        java.nio.file.attribute.FileTime modified = Files.getLastModifiedTime(file);
        Path replacement = Files.copy(file, temp.resolve("replacement.tmp"));
        Object before = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        Object after = Files.readAttributes(replacement, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        org.junit.jupiter.api.Assumptions.assumeTrue(before != null && after != null && !before.equals(after));
        Files.move(replacement, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(file, modified);
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(entry, "renamed.mcpr"));
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(temp.resolve("renamed.mcpr")));
    }

    @Test void renameRefusesMissingNonReplayAndDirectorySources() throws Exception {
        Path missing = archive(temp.resolve("missing.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry missingEntry = ReplayBrowserFiles.read(missing);
        Files.delete(missing);
        Path other = archive(temp.resolve("unrelated.zip"), "{\"duration\":4}", null);
        Path directory = Files.createDirectory(temp.resolve("folder.mcpr"));
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(missingEntry, "renamed.mcpr"));
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(ReplayBrowserFiles.read(other), "renamed.mcpr"));
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(ReplayBrowserFiles.read(directory), "renamed.mcpr"));
        assertFalse(Files.exists(temp.resolve("renamed.mcpr")));
        assertTrue(Files.isRegularFile(other));
        assertTrue(Files.isDirectory(directory));
    }

    @Test void corruptArchivesCanBeRenamedWithoutChangingBytes() throws Exception {
        Path file = Files.write(temp.resolve("broken.mcpr"), new byte[] {1, 2, 3});
        Path target = ReplayBrowserFiles.rename(ReplayBrowserFiles.read(file), "Named broken replay.mcpr");
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(target));
        assertFalse(Files.exists(file));
        assertFalse(ReplayBrowserFiles.read(target).playable());
    }

    @Test void interruptedRenameDoesNotMoveTheArchive() throws Exception {
        Path file = archive(temp.resolve("source.mcpr"), "{\"duration\":4}", null);
        ReplayBrowserFiles.Entry entry = ReplayBrowserFiles.read(file);
        Thread.currentThread().interrupt();
        try { assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(entry, "renamed.mcpr")); }
        finally { Thread.interrupted(); }
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(temp.resolve("renamed.mcpr")));
    }

    @Test void symlinkSourceAndDanglingDestinationAreNotRenamedOrOverwritten() throws Exception {
        Path source = archive(temp.resolve("source.mcpr"), "{\"duration\":4}", null);
        Path link = temp.resolve("link.mcpr");
        Path dangling = temp.resolve("dangling.mcpr");
        try {
            Files.createSymbolicLink(link, source);
            Files.createSymbolicLink(dangling, temp.resolve("missing.mcpr"));
        } catch (java.io.IOException | UnsupportedOperationException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable: " + unavailable.getMessage());
        }
        byte[] hash = hash(source);
        assertThrows(java.io.IOException.class, () -> ReplayBrowserFiles.rename(ReplayBrowserFiles.read(link), "renamed.mcpr"));
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> ReplayBrowserFiles.rename(ReplayBrowserFiles.read(source), "dangling.mcpr"));
        assertTrue(Files.isSymbolicLink(link));
        assertTrue(Files.isSymbolicLink(dangling));
        assertArrayEquals(hash, hash(source));
    }

    private static byte[] hash(Path path) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    }

    private static Path archive(Path path, String metadata, String manifest) throws Exception {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("metaData.json"));
            zip.write(metadata.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            if (manifest != null) {
                zip.putNextEntry(new ZipEntry("zsg-rooms/races.json"));
                zip.write(manifest.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return path;
    }
}
