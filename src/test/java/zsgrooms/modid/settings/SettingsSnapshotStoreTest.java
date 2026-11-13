package zsgrooms.modid.settings;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.modid.settings.SettingsImportPlan.*;
import static zsgrooms.modid.settings.ImportSettingsSchema.*;

class SettingsSnapshotStoreTest {
    @TempDir Path temporary;
    Path game;
    SettingsSnapshotStore store;
    final Set<String> bindings = new HashSet<>(Arrays.asList("key_Mod control"));
    final Set<Category> all = EnumSet.allOf(Category.class);

    @BeforeEach void setup() throws Exception {
        game = Files.createDirectories(temporary.resolve("game"));
        write(game.resolve(OPTIONS), "version:2567\nfov:0.5\nkey_key.inventory:key.keyboard.r\nkey_Mod control:key.keyboard.g\nsoundCategory_music:0.0\nlastServer:private-server\nresourcePacks:[\"private-pack\"]\n");
        write(game.resolve(STANDARD), "{\"fov\":90.0,\"fovOnWorldJoin\":{\"enabled\":false,\"value\":80.0},\".modVersion\":\"ignored\",\"privateThing\":\"secret\"}");
        store = new SettingsSnapshotStore(game);
    }

    @Test void savesSupportedStateWithoutPathsSecretsOrChangingLiveSettings() throws Exception {
        String before = read(game.resolve(OPTIONS));
        SettingsSnapshotStore.Snapshot saved = save("My setup");
        assertEquals("My setup", saved.name); assertEquals(6, saved.count());
        String text = read(saved.file);
        assertFalse(text.contains("private")); assertFalse(text.contains("resourcePacks"));
        assertFalse(text.contains(".modVersion")); assertFalse(text.contains(game.toString()));
        assertEquals(before, read(game.resolve(OPTIONS)));
        assertFalse(new SettingsImportStore(game).pending());
        SettingsSnapshotStore.Snapshot reloaded = new SettingsSnapshotStore(game).list().snapshots.get(0);
        assertEquals(saved.name, reloaded.name); assertEquals(saved.savedAt, reloaded.savedAt); assertEquals(saved.count(), reloaded.count());
    }

    @Test void restoresSameInstanceThroughPreviewRestartAndUndo() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("Original");
        write(game.resolve(OPTIONS), "version:2567\nfov:0.0\nkey_key.inventory:key.keyboard.e\nresourcePacks:[\"keep\"]\n");
        write(game.resolve(STANDARD), "{\"fov\":70,\"fovOnWorldJoin\":110,\"other\":true}");
        SettingsImportPlan plan = previewSnapshot(saved, game, bindings, true);
        assertEquals("Original", plan.sourceName); assertTrue(plan.count(all) > 0);
        SettingsImportStore imports = new SettingsImportStore(game);
        String before = read(game.resolve(OPTIONS)), standardBefore = read(game.resolve(STANDARD));
        imports.stage(plan, all, false);
        assertEquals(before, read(game.resolve(OPTIONS)));
        imports.applyAtStartup();
        assertTrue(read(game.resolve(OPTIONS)).contains("fov:0.5"));
        assertTrue(read(game.resolve(OPTIONS)).contains("key_key.inventory:key.keyboard.r"));
        assertTrue(read(game.resolve(OPTIONS)).contains("resourcePacks:[\"keep\"]"));
        assertFalse(object(read(game.resolve(STANDARD))).getAsJsonObject("fovOnWorldJoin").get("enabled").getAsBoolean());
        assertTrue(object(read(game.resolve(STANDARD))).get("other").getAsBoolean());
        imports.stageUndo(); imports.applyAtStartup();
        assertEquals(before, read(game.resolve(OPTIONS))); assertEquals(standardBefore, read(game.resolve(STANDARD)));
        assertEquals(1, store.list().snapshots.size());
    }

    @Test void categorySelectionDoesNotRestoreUnselectedVideo() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("Original");
        write(game.resolve(OPTIONS), "version:2567\nfov:0.0\nkey_key.inventory:key.keyboard.e\n");
        SettingsImportStore imports = new SettingsImportStore(game);
        imports.stage(previewSnapshot(saved, game, bindings, true), EnumSet.of(Category.CONTROLS), false);
        imports.applyAtStartup();
        assertTrue(read(game.resolve(OPTIONS)).contains("fov:0.0"));
        assertTrue(read(game.resolve(OPTIONS)).contains("key_key.inventory:key.keyboard.r"));
    }

    @Test void renameAndDeleteLeaveCurrentSettingsAndUndoBackupAlone() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("One");
        String before = read(game.resolve(OPTIONS)), data = read(saved.file);
        Path backup = game.resolve("config/zsg-rooms-import/last-backup.json"); write(backup, "untouched");
        SettingsSnapshotStore.Snapshot renamed = store.rename(saved, "Two");
        assertEquals(saved.file, renamed.file); assertEquals(saved.savedAt, renamed.savedAt);
        assertEquals("Two", store.list().snapshots.get(0).name);
        assertNotEquals(data, read(saved.file));
        store.delete(renamed); assertTrue(store.list().snapshots.isEmpty());
        assertEquals(before, read(game.resolve(OPTIONS))); assertEquals("untouched", read(backup));
    }

    @Test void duplicateNamesAndPathLikeNamesCannotOverwriteOrEscape() throws Exception {
        SettingsSnapshotStore.Snapshot first = save("../My setup"), second = save("../My setup");
        assertNotEquals(first.file, second.file); assertEquals(first.file.getParent(), second.file.getParent());
        assertEquals("snapshots", first.file.getParent().getFileName().toString());
        assertEquals(2, store.list().snapshots.size());
        assertThrows(IOException.class, () -> save("\u00a7cUnsafe"));
        assertThrows(IOException.class, () -> save("  "));
        assertThrows(IOException.class, () -> save("Line\nbreak"));
    }

    @Test void portableExportImportRoundTripsAndRequiresOverwriteConsent() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("Portable");
        Path file = temporary.resolve("shared.json"); store.exportFile(saved, file, false);
        assertThrows(IOException.class, () -> store.exportFile(saved, file, false));
        store.exportFile(saved, file, true);
        Path other = Files.createDirectories(temporary.resolve("other"));
        SettingsSnapshotStore otherStore = new SettingsSnapshotStore(other);
        SettingsSnapshotStore.Snapshot imported = otherStore.importFile(file);
        assertEquals(saved.name, imported.name); assertEquals(saved.count(), imported.count());
        assertNotEquals(file, imported.file); assertFalse(Files.exists(other.resolve(OPTIONS)));
        assertThrows(IOException.class, () -> otherStore.delete(saved));
        assertThrows(IOException.class, () -> store.exportFile(saved, temporary.resolve("options.txt"), false));
    }

    @Test void sharedStandardSettingsAreCapturedAsValuesNeverLinks() throws Exception {
        Path shared = temporary.resolve("shared-standard.json"); write(shared, "{\"fov\":100,\"guiScale\":2}");
        write(game.resolve(GLOBAL), shared.toString());
        SettingsSnapshotStore.Snapshot saved = save("Shared");
        assertFalse(read(saved.file).contains(shared.toString()));
        write(shared, "{\"fov\":70,\"guiScale\":3}");
        SettingsImportPlan plan = previewSnapshot(saved, game, bindings, true);
        assertTrue(plan.detachesShared(all));
        SettingsImportStore imports = new SettingsImportStore(game);
        assertThrows(IOException.class, () -> imports.stage(plan, all, false));
        imports.stage(plan, all, true); imports.applyAtStartup();
        assertEquals(100, object(read(game.resolve(STANDARD))).get("fov").getAsInt());
        assertEquals(70, object(read(shared)).get("fov").getAsInt());
        assertFalse(Files.exists(game.resolve(GLOBAL)));
    }

    @Test void captureFailsCompletelyIfEnabledStandardFileCannotBeRead() throws Exception {
        write(game.resolve(GLOBAL), temporary.resolve("missing.json").toString());
        assertThrows(IOException.class, () -> save("Broken"));
        assertTrue(store.list().snapshots.isEmpty());
        assertTrue(store.save("Without Standard", "1.16.1", bindings, false).count() > 0);
    }

    @Test void pendingRestoreIsIndependentOfSnapshotAndSubsequentSaves() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("First");
        write(game.resolve(OPTIONS), "version:2567\nfov:0.0\n");
        SettingsImportStore imports = new SettingsImportStore(game);
        imports.stage(previewSnapshot(saved, game, bindings, true), all, false);
        Path pending = game.resolve("config/zsg-rooms-import/pending.json"); String before = read(pending);
        save("Second"); store.delete(saved);
        assertEquals(before, read(pending)); imports.applyAtStartup();
        assertTrue(read(game.resolve(OPTIONS)).contains("fov:0.5"));
    }

    @Test void unknownKeysInImportedFileCannotEscapeAllowlist() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("Clean");
        JsonObject data = object(read(saved.file));
        data.getAsJsonObject("options").addProperty("lastServer", "not-imported");
        data.getAsJsonObject("options").addProperty("resourcePacks", "[]");
        data.getAsJsonObject("standard").addProperty("../file", "not-imported");
        Path external = temporary.resolve("external.json"); write(external, data.toString());
        SettingsSnapshotStore.Snapshot imported = store.importFile(external);
        SettingsImportPlan plan = previewSnapshot(imported, game, Collections.emptySet(), true);
        assertFalse(plan.changes.stream().anyMatch(c -> c.key.equals("lastServer") || c.key.equals("resourcePacks") || c.key.equals("../file")));
        assertTrue(plan.skipped.size() >= 3);
    }

    @Test void malformedFilesAreSkippedWithoutHidingGoodSnapshots() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("Good");
        write(saved.file.resolveSibling("bad.json"), "{bad");
        SettingsSnapshotStore.Listing list = store.list();
        assertEquals(1, list.snapshots.size()); assertEquals(1, list.unreadable);
        assertThrows(IOException.class, () -> store.importFile(saved.file.resolveSibling("bad.json")));
    }

    @Test void unsupportedFormatsInvalidDatesAndOversizedFilesAreRejected() throws Exception {
        SettingsSnapshotStore.Snapshot saved = save("Good"); Path external = temporary.resolve("external.json");
        JsonObject data = object(read(saved.file)); data.addProperty("version", 2); write(external, data.toString());
        assertThrows(IOException.class, () -> store.importFile(external));
        data.addProperty("version", 1); data.addProperty("savedAt", "+1000000000-12-31T23:59:59.999999999Z"); write(external, data.toString());
        assertThrows(IOException.class, () -> store.importFile(external));
        write(external, new String(new char[1024 * 1024 + 1]).replace('\0', 'x'));
        assertThrows(IOException.class, () -> store.importFile(external));
    }

    @Test void restoringUnchangedStateHasNoChanges() throws Exception {
        assertEquals(0, previewSnapshot(save("Same"), game, bindings, true).count(all));
    }

    private SettingsSnapshotStore.Snapshot save(String name) throws Exception { return store.save(name, "1.16.1", bindings, true); }
    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent()); Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(Path file) throws Exception { return new String(Files.readAllBytes(file), StandardCharsets.UTF_8); }
}
