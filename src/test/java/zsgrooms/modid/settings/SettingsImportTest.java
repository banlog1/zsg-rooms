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

class SettingsImportTest {
    @TempDir Path temporary;
    Path source, target;
    SettingsImportStore store;
    final Set<Category> all = EnumSet.allOf(Category.class);
    final Set<String> bindings = new HashSet<>(Arrays.asList("key_Create New World"));

    @BeforeEach void setup() throws Exception {
        source = Files.createDirectories(temporary.resolve("source/.minecraft"));
        target = Files.createDirectories(temporary.resolve("target/.minecraft"));
        write(source, OPTIONS, "version:2586\nmouseSensitivity:0.3\nkey_key.inventory:key.keyboard.r\nsoundCategory_music:0.0\n");
        write(target, OPTIONS, "version:2567\nmouseSensitivity:0.5\nkey_key.inventory:key.keyboard.e\nsoundCategory_music:1.0\nresourcePacks:[\"keep-me\"]\n");
        store = new SettingsImportStore(target);
    }

    @Test void stagesWithoutTouchingEitherInstanceAndAppliesOnStartup() throws Exception {
        String before = read(target, OPTIONS), originalSource = read(source, OPTIONS);
        store.stage(preview(), all, false);
        assertTrue(store.pending()); assertEquals(before, read(target, OPTIONS));
        store.applyAtStartup();
        assertTrue(read(target, OPTIONS).contains("mouseSensitivity:0.3"));
        assertTrue(read(target, OPTIONS).contains("version:2567"));
        assertTrue(read(target, OPTIONS).contains("resourcePacks:[\"keep-me\"]"));
        assertEquals(originalSource, read(source, OPTIONS));
        assertFalse(store.pending()); assertTrue(store.canUndo());
        assertTrue(store.status().contains("successfully"));
        String imported = read(target, OPTIONS);
        store.applyAtStartup(); assertEquals(imported, read(target, OPTIONS));
    }

    @Test void categorySelectionAndShutdownWritesAreMerged() throws Exception {
        store.stage(preview(), EnumSet.of(Category.CONTROLS), false);
        write(target, OPTIONS, "version:2567\nmouseSensitivity:0.8\nsoundCategory_music:0.7\nmod_option:keep:unchanged\n");
        store.applyAtStartup();
        assertTrue(read(target, OPTIONS).contains("mouseSensitivity:0.3"));
        assertTrue(read(target, OPTIONS).contains("soundCategory_music:0.7"));
        assertTrue(read(target, OPTIONS).contains("mod_option:keep:unchanged"));
    }

    @Test void sourceMayCloseOrDisappearAfterStaging() throws Exception {
        store.stage(preview(), all, false);
        Files.delete(source.resolve(OPTIONS)); store.applyAtStartup();
        assertTrue(read(target, OPTIONS).contains("mouseSensitivity:0.3"));
    }

    @Test void undoRestoresExactPreviousFilesAndAbsence() throws Exception {
        write(source, STANDARD, "{\"renderDistance\":8}");
        String before = read(target, OPTIONS);
        store.stage(preview(), all, false); store.applyAtStartup();
        assertTrue(Files.exists(target.resolve(STANDARD)));
        store.stageUndo(); assertTrue(Files.exists(target.resolve(STANDARD)));
        store.applyAtStartup();
        assertEquals(before, read(target, OPTIONS)); assertFalse(Files.exists(target.resolve(STANDARD))); assertFalse(store.canUndo());
    }

    @Test void pendingOperationCannotBeOverwrittenAndCanBeCancelled() throws Exception {
        String before = read(target, OPTIONS); store.stage(preview(), all, false);
        assertThrows(IOException.class, () -> store.stage(preview(), all, false));
        store.cancel(); store.applyAtStartup(); assertEquals(before, read(target, OPTIONS));
        assertFalse(store.canUndo());
    }

    @Test void onlyCompatibleAllowlistedSettingsCrossVersions() throws Exception {
        write(source, OPTIONS, "fov:0.5\nrenderDistance:64\ngamma:NaN\nkey_key.inventory:18\n"
                + "key_Create New World:key.keyboard.f6\nkey_unknown.mod:key.keyboard.g\n"
                + "lastServer:secret.example\nresourcePacks:[\"source\"]\nfullscreenResolution:1920x1080\nversion:5000\n");
        SettingsImportPlan plan = preview();
        assertEquals(2, plan.changes.size()); assertEquals(8, plan.skipped.size());
        store.stage(plan, all, false); store.applyAtStartup();
        String result = read(target, OPTIONS);
        assertTrue(result.contains("fov:0.5")); assertTrue(result.contains("key_Create New World:key.keyboard.f6"));
        assertFalse(result.contains("secret.example")); assertFalse(result.contains("renderDistance:"));
    }

    @Test void standardResetAndJoinValuesAndDisabledStatesRemainSeparate() throws Exception {
        write(source, STANDARD, "{\"fov\":90.0,\"renderDistance\":12.0,\"renderDistanceOnWorldJoin\":8.0,"
                + "\"fovOnWorldJoin\":{\"enabled\":false,\"value\":80.0},\"toggleStandardSettings\":false,\"key_Create New World\":\"key.keyboard.f6\"}");
        write(target, STANDARD, "{\".modVersion\":\"keep\",\"fov\":70,\"renderDistance\":4,\"fullscreenResolution\":\"keep\"}");
        store.stage(preview(), all, false); store.applyAtStartup();
        JsonObject standard = object(read(target, STANDARD));
        assertEquals(90, standard.get("fov").getAsInt()); assertEquals(12, standard.get("renderDistance").getAsInt());
        assertEquals(8, standard.get("renderDistanceOnWorldJoin").getAsInt());
        assertFalse(standard.getAsJsonObject("fovOnWorldJoin").get("enabled").getAsBoolean());
        assertFalse(standard.get("toggleStandardSettings").getAsBoolean());
        assertEquals("keep", standard.get(".modVersion").getAsString());
        assertEquals("keep", standard.get("fullscreenResolution").getAsString());
    }

    @Test void vanillaImportUpdatesExistingEnabledResetOverrides() throws Exception {
        write(source, OPTIONS, "fov:0.5\nrenderDistance:8\nrenderClouds:fast\nmainHand:left\ntoggleSprint:true\n");
        write(target, STANDARD, "{\"fov\":70,\"fovOnWorldJoin\":80,\"renderDistance\":{\"enabled\":false,\"value\":12},"
                + "\"renderClouds\":2,\"mainHand\":1,\"toggleSprint\":0}");
        store.stage(preview(), all, false); store.applyAtStartup();
        JsonObject result = object(read(target, STANDARD));
        assertEquals(90, result.get("fov").getAsInt()); assertEquals(90, result.get("fovOnWorldJoin").getAsInt());
        assertEquals(12, result.getAsJsonObject("renderDistance").get("value").getAsInt());
        assertEquals(1, result.get("renderClouds").getAsInt()); assertEquals(0, result.get("mainHand").getAsInt());
        assertEquals(1, result.get("toggleSprint").getAsInt());
    }

    @Test void sharedDestinationRequiresConsentAndNeverWritesSharedFile() throws Exception {
        Path shared = temporary.resolve("shared.json"); write(temporary, "shared.json", "{\"fov\":70,\"unselected\":true}");
        write(target, GLOBAL, shared.toString()); write(source, STANDARD, "{\"fov\":90}");
        SettingsImportPlan plan = preview(); assertTrue(plan.detachesShared(all));
        assertThrows(IOException.class, () -> store.stage(plan, all, false));
        String sharedBefore = read(temporary, "shared.json");
        store.stage(plan, all, true); store.applyAtStartup();
        assertEquals(sharedBefore, read(temporary, "shared.json")); assertFalse(Files.exists(target.resolve(GLOBAL)));
        assertEquals(90, object(read(target, STANDARD)).get("fov").getAsInt());
        assertTrue(object(read(target, STANDARD)).get("unselected").getAsBoolean());
        store.stageUndo(); store.applyAtStartup();
        assertEquals(shared.toString(), read(target, GLOBAL)); assertFalse(Files.exists(target.resolve(STANDARD)));
    }

    @Test void sharedSourceIsReadOnly() throws Exception {
        Path shared = temporary.resolve("shared.json"); write(temporary, "shared.json", "{\"fov\":90}");
        write(source, GLOBAL, shared.toString());
        store.stage(preview(), all, false); store.applyAtStartup();
        assertEquals("{\"fov\":90}", read(temporary, "shared.json"));
        assertEquals(90, object(read(target, STANDARD)).get("fov").getAsInt());
    }

    @Test void changedSharedPointerFailsWithoutPartialImport() throws Exception {
        write(source, STANDARD, "{\"fov\":90}");
        String before = read(target, OPTIONS); store.stage(preview(), all, false);
        write(target, GLOBAL, temporary.resolve("other.json").toString()); store.applyAtStartup();
        assertEquals(before, read(target, OPTIONS)); assertTrue(store.status().contains("not applied"));
        assertFalse(store.pending()); assertFalse(store.canUndo());
    }

    @Test void corruptJsonOrDuplicateOptionsAreRejectedBeforeStaging() throws Exception {
        write(source, STANDARD, "{bad"); assertThrows(IOException.class, this::preview);
        Files.delete(source.resolve(STANDARD)); write(source, OPTIONS, "fov:0\nfov:0.5\n");
        assertThrows(IOException.class, this::preview); assertFalse(store.pending());
    }

    @Test void rejectsSelfAndAcceptsInstanceRoot() throws Exception {
        assertThrows(IOException.class, () -> SettingsImportPlan.preview(target, target, bindings, true));
        assertEquals(source.toRealPath(), SettingsImportPlan.gameDirectory(source.getParent()));
    }

    @Test void withoutStandardSettingsOnlyVanillaIsImported() throws Exception {
        write(source, STANDARD, "{\"fov\":90}");
        SettingsImportPlan plan = SettingsImportPlan.preview(source, target, bindings, false);
        assertTrue(plan.changes.stream().noneMatch(c -> c.file.equals(STANDARD)));
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("not installed")));
    }

    @Test void invalidStandardValuesAreSkipped() throws Exception {
        write(source, STANDARD, "{\"fov\":500,\"renderClouds\":6,\"mainHand\":7,\"fovOnWorldJoin\":{\"value\":80},\"commands\":\"bad\"}");
        SettingsImportPlan plan = preview();
        assertTrue(plan.changes.stream().noneMatch(c -> c.file.equals(STANDARD))); assertTrue(plan.skipped.size() >= 5);
    }

    @Test void standardControlTogglesCannotHaveOptionWrappers() throws Exception {
        write(source, STANDARD, "{\"toggleStandardSettings\":{\"enabled\":false,\"value\":true},\"heldItemTooltips\":true}");
        assertTrue(preview().changes.stream().noneMatch(c -> c.file.equals(STANDARD)));
    }

    @Test void crashRecoveryRestoresOriginalAndDoesNotRetry() throws Exception {
        String before = read(target, OPTIONS); store.stage(preview(), all, false);
        JsonObject files = new JsonObject(); files.addProperty(OPTIONS, before);
        JsonObject recovery = new JsonObject(); recovery.add("files", files); recovery.add("previousBackup", JsonNull.INSTANCE);
        write(target, "config/zsg-rooms-import/recovery.json", recovery.toString());
        write(target, OPTIONS, "partial-change:true\n");
        store.applyAtStartup();
        assertEquals(before, read(target, OPTIONS)); assertFalse(store.pending()); assertTrue(store.status().contains("rolled back"));
    }

    @Test void corruptPendingCannotWriteArbitraryFiles() throws Exception {
        String before = read(target, OPTIONS);
        write(target, "config/zsg-rooms-import/pending.json", "{\"kind\":\"undo\",\"backup\":{\"../outside\":\"bad\"}}");
        store.applyAtStartup(); assertEquals(before, read(target, OPTIONS)); assertFalse(Files.exists(target.getParent().resolve("outside")));
        assertTrue(store.status().contains("not applied"));
    }

    @Test void preservesLatestTargetMetadataAndUnselectedStandardSettings() throws Exception {
        write(source, STANDARD, "{\"fov\":90}"); write(target, STANDARD, "{\"fov\":70,\"gamma\":1}");
        store.stage(preview(), EnumSet.of(Category.VIDEO), false);
        write(target, STANDARD, "{\"fov\":70,\"gamma\":2,\".modVersion\":\"new\"}");
        store.applyAtStartup();
        assertEquals(2, object(read(target, STANDARD)).get("gamma").getAsInt());
        assertEquals("new", object(read(target, STANDARD)).get(".modVersion").getAsString());
    }

    @Test void discoveryOnlyFindsOtherInstances() throws Exception {
        write(target.getParent(), "instance.cfg", "name=target");
        assertTrue(SettingsImportPlan.discover(target).contains(source.toRealPath()));
        assertFalse(SettingsImportPlan.discover(target).contains(target.toRealPath()));
    }

    @Test void rejectsUnrecognizedKeysAndPreservesSupportedMouseAndFunctionKeys() {
        assertNull(vanillaValue("key_key.inventory", "key.keyboard.not.a.real.key", bindings));
        assertNull(vanillaValue("key_key.inventory", "key.mouse.999999999999", bindings));
        assertEquals("key.mouse.5", vanillaValue("key_key.inventory", "key.mouse.5", bindings));
        assertEquals("key.keyboard.f25", vanillaValue("key_key.inventory", "key.keyboard.f25", bindings));
        assertEquals("key.keyboard.left.control", vanillaValue("key_key.inventory", "key.keyboard.left.control", bindings));
    }

    @Test void oversizedSourceDoesNotCreatePendingImport() throws Exception {
        byte[] huge = new byte[1024 * 1024 + 1]; Arrays.fill(huge, (byte) 'x');
        Files.write(source.resolve(OPTIONS), huge);
        assertThrows(IOException.class, this::preview); assertFalse(store.pending());
    }

    @Test void emptySelectionDoesNotCreatePendingImport() throws Exception {
        assertThrows(IOException.class, () -> store.stage(preview(), EnumSet.noneOf(Category.class), false));
        assertFalse(store.pending());
    }

    @Test void missingSharedSourceFailsClearly() throws Exception {
        write(source, GLOBAL, temporary.resolve("missing-shared.json").toString());
        assertThrows(IOException.class, this::preview); assertFalse(store.pending());
    }

    @Test void malformedDestinationDoesNotPartiallyApplyVanillaChanges() throws Exception {
        write(source, STANDARD, "{\"fov\":90}");
        String before = read(target, OPTIONS); store.stage(preview(), all, false);
        write(target, STANDARD, "broken-json"); store.applyAtStartup();
        assertEquals(before, read(target, OPTIONS)); assertEquals("broken-json", read(target, STANDARD));
        assertTrue(store.status().contains("not applied")); assertFalse(store.canUndo());
    }

    @Test void invalidSourceOverrideCannotUndoAnOtherwiseValidVanillaImport() throws Exception {
        write(source, OPTIONS, "fov:0.5\n"); write(source, STANDARD, "{\"fov\":999}"); write(target, STANDARD, "{\"fov\":70}");
        store.stage(preview(), all, false); store.applyAtStartup();
        assertEquals(90, object(read(target, STANDARD)).get("fov").getAsInt());
    }

    @Test void freshPackWithoutVersionGetsCorrectDestinationFormat() throws Exception {
        write(target, OPTIONS, "soundCategory_music:0.0\n");
        store.stage(preview(), all, false); store.applyAtStartup();
        assertTrue(read(target, OPTIONS).contains("version:2567"));
        assertTrue(read(target, OPTIONS).contains("key_key.inventory:key.keyboard.r"));
    }

    @Test void oldDestinationKeyCodesAreNotMislabelledAsModernOptions() throws Exception {
        SettingsImportPlan plan = preview();
        write(target, OPTIONS, "key_key.inventory:18\n");
        assertThrows(IOException.class, this::preview);
        String before = read(target, OPTIONS); store.stage(plan, all, false); store.applyAtStartup();
        assertEquals(before, read(target, OPTIONS)); assertTrue(store.status().contains("not applied"));
    }

    @Test void duplicateJsonOrExcessiveNestingFailsWithoutHanging() throws Exception {
        write(source, STANDARD, "{\"fov\":70,\"fov\":90}"); assertThrows(IOException.class, this::preview);
        String nested = "0"; for (int i = 0; i < 40; i++) nested = "[" + nested + "]";
        write(source, STANDARD, "{\"unknown\":" + nested + "}"); assertThrows(IOException.class, this::preview);
    }

    @Test void interruptedUndoPreservesOriginalUndoBackup() throws Exception {
        String before = read(target, OPTIONS); store.stage(preview(), all, false); store.applyAtStartup();
        JsonObject savedBackup = object(read(target, "config/zsg-rooms-import/last-backup.json"));
        String imported = read(target, OPTIONS);
        JsonObject files = new JsonObject(); files.addProperty(OPTIONS, imported);
        JsonObject recovery = new JsonObject(); recovery.add("files", files); recovery.add("previousBackup", savedBackup);
        store.stageUndo(); write(target, "config/zsg-rooms-import/recovery.json", recovery.toString());
        write(target, OPTIONS, before); Files.delete(target.resolve("config/zsg-rooms-import/last-backup.json"));
        store.applyAtStartup(); assertEquals(imported, read(target, OPTIONS)); assertTrue(store.canUndo());
        store.stageUndo(); store.applyAtStartup(); assertEquals(before, read(target, OPTIONS));
    }

    private SettingsImportPlan preview() throws IOException { return SettingsImportPlan.preview(source, target, bindings, true); }
    static void write(Path root, String file, String text) throws IOException {
        Path path = root.resolve(file); Files.createDirectories(path.getParent()); Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
    static String read(Path root, String file) throws IOException { return new String(Files.readAllBytes(root.resolve(file)), StandardCharsets.UTF_8); }
}
