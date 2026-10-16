package zsgrooms.replaytest;

import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/** Destructive actions are restricted to fresh copies under the ignored development run directory. */
final class BrowserDeleteSmokeChecks {
    private int step;
    private long started, next;
    private Screen browser;
    private Path root, recordings, source, remove, replace, survivor, backup, cache, renamed, collision;
    private byte[] sourceHash;

    boolean tick(MinecraftClient client, File fixture) throws Exception {
        if (System.nanoTime() < next) return false;
        if (started == 0) started = System.nanoTime();
        require(System.nanoTime() - started < 120000000000L, "Browser deletion smoke timed out at step " + step);
        require(client.world == null && ReplayModReplay.instance.getReplayHandler() == null,
                "Browser deletion smoke must run before playback");
        if (step == 0) {
            Path run = client.runDirectory.toPath().toRealPath();
            require(run.getFileName().toString().equals("replay-playback-test")
                    && run.getParent().getFileName().toString().equals("run"), "Unexpected development run directory: " + run);
            Path cases = run.resolve("browser-delete-smoke");
            Files.createDirectories(cases);
            root = Files.createTempDirectory(cases, "case-").toRealPath();
            recordings = Files.createDirectories(root.resolve("replay_recordings")).toRealPath();
            source = fixture.toPath().toRealPath();
            require(!source.startsWith(root), "Original fixture is inside the deletion test directory");
            sourceHash = hash(source);
            remove = copy("delete-target.mcpr");
            replace = copy("replace-target.mcpr");
            survivor = copy("keep-target.mcpr");
            backup = owned(root.resolve("replaced-archive.backup"));
            cache = owned(root.resolve("replay_recordings/delete-target.mcpr.cache/keep.txt"));
            Files.createDirectories(cache.getParent());
            Files.write(cache, "test-owned sibling cache; must survive".getBytes(StandardCharsets.US_ASCII));
            browser = (Screen) Class.forName("zsgrooms.replayviewer.ReplayBrowserScreen")
                    .getConstructor(Screen.class).newInstance(client.currentScreen);
            // Isolate even the default-folder scan; never expose a user's recording as a selectable test target.
            set(browser, "game", root);
            client.options.guiScale = 2;
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            client.openScreen(browser);
            advance(1);
        } else if (step == 1) {
            if (busy()) return false;
            assertOnlyTestEntries(3);
            require(get(browser, "selected") == null && !button(browser, "Delete selected recording").active,
                    "Delete is enabled without a selection");
            require(!button(browser, "Rename selected recording").active, "Rename is enabled without a selection");
            assertViewport(client, 1280, 720);
            assertControlsFit(browser);
            capture(client, "browser-delete-wide.png");
            select(remove);
            openConfirmation(client, remove);
            advance(2);
        } else if (step == 2) {
            require(client.currentScreen instanceof ConfirmScreen, "Delete confirmation did not stay open");
            capture(client, "browser-delete-confirm-wide.png");
            click(client.currentScreen, "Cancel");
            require(client.currentScreen == browser, "Cancel did not return to browser");
            assertArchive(remove);
            assertPreserved();
            require(entriesContain(remove), "Cancel removed the selected recording from the list");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
            advance(3);
        } else if (step == 3) {
            assertViewport(client, 640, 480);
            assertControlsFit(browser);
            capture(client, "browser-delete-small.png");
            openConfirmation(client, remove);
            advance(4);
        } else if (step == 4) {
            assertControlsFit(client.currentScreen);
            capture(client, "browser-delete-confirm-small.png");
            click(client.currentScreen, "Delete");
            advance(5);
        } else if (step == 5) {
            if (busy()) return false;
            require(!Files.exists(remove) && !entriesContain(remove), "Confirmed delete did not remove only the selected archive/list row");
            require(get(browser, "selected") == null && !button(browser, "Delete selected recording").active,
                    "Deletion left an actionable stale selection");
            require(((String) get(browser, "status")).startsWith("Deleted "), "Successful deletion status missing");
            assertOnlyTestEntries(2);
            assertPreserved();
            select(replace);
            openConfirmation(client, replace);
            // Replace the test-owned archive after confirmation has captured the original scan identity.
            FileTime oldTime = Files.getLastModifiedTime(replace);
            Files.move(owned(replace), backup);
            Files.copy(source, owned(replace));
            Files.setLastModifiedTime(replace, FileTime.fromMillis(oldTime.toMillis() + 10000));
            assertArchive(replace);
            click(client.currentScreen, "Delete");
            advance(6);
        } else if (step == 6) {
            if (busy()) return false;
            assertArchive(replace);
            require(entriesContain(replace), "Failed delete dropped the replacement from the list");
            String status = (String) get(browser, "status");
            require(status.startsWith("Cannot delete recording:") && status.toLowerCase(java.util.Locale.ROOT).contains("refresh"),
                    "Replacement deletion did not show a refresh/error message: " + status);
            assertPreserved();
            capture(client, "browser-delete-replacement-error-small.png");
            click(browser, "Refresh recordings");
            advance(7);
        } else if (step == 7) {
            if (busy()) return false;
            require(!((String) get(browser, "status")).startsWith("Cannot delete"), "Refresh left the stale deletion error visible");
            search("");
            assertOnlyTestEntries(2);
            require(entriesContain(replace) && entriesContain(survivor) && !entriesContain(remove),
                    "Refresh did not reflect the current files");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            advance(8);
        } else if (step == 8) {
            assertViewport(client, 1280, 720);
            assertControlsFit(browser);
            capture(client, "browser-delete-refreshed-wide.png");
            select(replace);
            openConfirmation(client, replace);
            click(client.currentScreen, "Delete");
            advance(9);
        } else if (step == 9) {
            if (busy()) return false;
            require(!Files.exists(replace) && !entriesContain(replace), "Refreshed replacement identity could not be deleted");
            assertOnlyTestEntries(1);
            assertPreserved();
            require(Files.exists(backup) && Arrays.equals(sourceHash, hash(backup)), "Replacement backup changed");
            renamed = owned(recordings.resolve("Personal best.mcpr"));
            collision = owned(recordings.resolve("rename-collision.mcpr"));
            select(survivor);
            openRename(client, survivor);
            advance(10);
        } else if (step == 10) {
            require(!button(client.currentScreen, "Rename").active, "Unchanged filename is actionable");
            renameDraft(client, "../escape.mcpr");
            require(!button(client.currentScreen, "Rename").active, "Traversal draft is actionable");
            renameDraft(client, renamed.getFileName().toString());
            set(browser, "selected", null);
            client.currentScreen.tick();
            require(!button(client.currentScreen, "Rename").active, "Changed selection is still actionable");
            set(browser, "selected", survivor);
            client.currentScreen.tick();
            require(button(client.currentScreen, "Rename").active, "Valid rename stayed disabled");
            client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(client.currentScreen == browser, "Escape did not cancel rename");
            assertPreserved();
            require(!Files.exists(renamed) && entriesContain(survivor), "Escape changed the archive or list");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
            advance(11);
        } else if (step == 11) {
            assertViewport(client, 640, 480);
            assertControlsFit(browser);
            openRename(client, survivor);
            renameDraft(client, renamed.getFileName().toString());
            advance(12);
        } else if (step == 12) {
            assertControlsFit(client.currentScreen);
            capture(client, "browser-rename-small.png");
            Screen cancelled = client.currentScreen;
            click(cancelled, "Cancel");
            cancelled.onClose();
            require(client.currentScreen == browser, "Repeated cancel navigated away from browser");
            assertPreserved();
            require(!Files.exists(renamed), "Cancel renamed a recording");
            Files.copy(source, collision);
            openRename(client, survivor);
            renameDraft(client, collision.getFileName().toString());
            click(client.currentScreen, "Rename");
            advance(13);
        } else if (step == 13) {
            if (busy()) return false;
            require(((String) get(browser, "status")).startsWith("Cannot rename recording:"), "Collision error missing");
            assertArchive(collision);
            assertPreserved();
            Files.delete(owned(collision));
            openRename(client, survivor);
            renameDraft(client, renamed.getFileName().toString());
            FileTime oldTime = Files.getLastModifiedTime(survivor);
            Files.setLastModifiedTime(owned(survivor), FileTime.fromMillis(oldTime.toMillis() + 10000));
            click(client.currentScreen, "Rename");
            advance(14);
        } else if (step == 14) {
            if (busy()) return false;
            String status = (String) get(browser, "status");
            require(status.startsWith("Cannot rename recording:") && status.toLowerCase(java.util.Locale.ROOT).contains("refresh"),
                    "Stale rename identity did not show a refresh error: " + status);
            assertPreserved();
            require(!Files.exists(renamed) && entriesContain(survivor), "Stale identity rename changed the source or list");
            click(browser, "Refresh recordings");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            advance(15);
        } else if (step == 15) {
            if (busy()) return false;
            assertViewport(client, 1280, 720);
            select(survivor);
            openRename(client, survivor);
            renameDraft(client, renamed.getFileName().toString());
            advance(16);
        } else if (step == 16) {
            assertControlsFit(client.currentScreen);
            capture(client, "browser-rename-wide.png");
            click(client.currentScreen, "Rename");
            advance(17);
        } else if (step == 17) {
            if (busy()) return false;
            require(!Files.exists(survivor) && !entriesContain(survivor) && entriesContain(renamed), "Rename did not update archive/list paths");
            require(renamed.equals(get(browser, "selected")), "Rename did not follow the selected path");
            require(((String) get(browser, "status")).startsWith("Renamed to "), "Rename success status missing");
            assertArchive(renamed);
            require(Arrays.equals(sourceHash, hash(source)), "Rename changed the original input recording");
            require(((String) get(browser, "query")).isEmpty(), "Old filename query hid the renamed selection");
            assertRenamedTitle();
            capture(client, "browser-renamed-wide.png");
            click(browser, "Refresh recordings");
            advance(18);
        } else if (step == 18) {
            if (busy()) return false;
            assertRenamedTitle();
            browser.onClose();
            browser = (Screen) Class.forName("zsgrooms.replayviewer.ReplayBrowserScreen")
                    .getConstructor(Screen.class).newInstance(client.currentScreen);
            set(browser, "game", root);
            client.openScreen(browser);
            advance(19);
        } else if (step == 19) {
            if (busy()) return false;
            assertOnlyTestEntries(1);
            assertRenamedTitle();
            select(renamed);
            openRename(client, renamed);
            renameDraft(client, survivor.getFileName().toString());
            click(client.currentScreen, "Rename");
            advance(20);
        } else if (step == 20) {
            if (busy()) return false;
            require(!Files.exists(renamed) && entriesContain(survivor), "Test-owned recording was not renamed back");
            assertPreserved();
            openRename(client, survivor);
            renameDraft(client, renamed.getFileName().toString());
            Screen abandoned = client.currentScreen;
            client.openScreen(browser);
            abandoned.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            abandoned.onClose();
            require(client.currentScreen == browser && !busy(), "Abandoned dialog reopened or submitted work");
            require(!Files.exists(renamed), "Abandoned dialog renamed the recording");
            browser.onClose();
            Screen parent = client.currentScreen;
            abandoned.onClose();
            require(client.currentScreen == parent, "Stale dialog reopened a closed browser");
            assertPreserved();
            ZsgRooms.LOGGER.info("[BrowserDeleteSmoke] PASS: deletion coverage preserved; rename disabled without selection, read-only dialog, invalid/changed selection guards, cancel/escape, collision, stale identity, hash preservation, title after refresh/reopen, rename back, abandoned dialog, 640x480 and 1280x720 screenshots; all mutation targets test-owned under {}", root);
            return true;
        }
        return false;
    }

    private void advance(int value) { step = value; next = System.nanoTime() + 600000000L; }
    private boolean busy() throws Exception { return (Boolean) get(browser, "busy"); }

    private Path owned(Path path) {
        Path value = path.toAbsolutePath().normalize();
        require(root != null && value.startsWith(root) && !value.equals(root) && !value.equals(source),
                "Refusing operation outside fresh test directory: " + value);
        return value;
    }
    private Path copy(String name) throws Exception {
        Path path = owned(recordings.resolve(name));
        Files.copy(source, path);
        return path.toRealPath();
    }
    private void assertPreserved() throws Exception {
        require(Arrays.equals(sourceHash, hash(source)), "Original input recording changed");
        assertArchive(survivor);
        if (step < 9) assertArchive(replace);
        else require(!Files.exists(replace), "Confirmed replacement deletion was undone");
        require(Files.isRegularFile(cache) && new String(Files.readAllBytes(cache), StandardCharsets.US_ASCII)
                .equals("test-owned sibling cache; must survive"), "Deletion removed or changed a sibling cache");
    }
    private void assertArchive(Path path) throws Exception {
        owned(path);
        require(Files.isRegularFile(path) && Arrays.equals(sourceHash, hash(path)), "Test archive missing or changed: " + path);
    }
    private void assertOnlyTestEntries(int size) throws Exception {
        List<?> entries = (List<?>) get(browser, "entries");
        require(entries.size() == size, "Unexpected isolated library size: " + entries.size() + " instead of " + size);
        for (Object entry : entries) {
            Path path = (Path) get(entry, "path");
            owned(path);
            require(path.equals(remove) || path.equals(replace) || path.equals(survivor) || path.equals(renamed), "Unowned library entry: " + path);
        }
    }
    private boolean entriesContain(Path path) throws Exception {
        for (Object entry : (List<?>) get(browser, "entries")) if (get(entry, "path").equals(path)) return true;
        return false;
    }
    private void search(String value) throws Exception { ((TextFieldWidget) get(browser, "search")).setText(value); }
    private void select(Path path) throws Exception {
        owned(path);
        search(path.getFileName().toString());
        List<?> filtered = (List<?>) get(browser, "filtered");
        require(filtered.size() == 1 && get(filtered.get(0), "path").equals(path), "Search did not isolate the owned deletion target");
        List<?> rows = (List<?>) get(browser, "rowButtons");
        require(rows.size() == 1, "Owned target row missing");
        ((ButtonWidget) rows.get(0)).onPress();
        require(path.equals(get(browser, "selected")), "Browser selected the wrong recording");
        require(button(browser, "Delete selected recording").active, "Delete is disabled for the owned selected recording");
    }
    private void openConfirmation(MinecraftClient client, Path expected) throws Exception {
        owned(expected);
        require(expected.equals(get(browser, "selected")), "Refusing to confirm an unexpected selection");
        click(browser, "Delete selected recording");
        require(client.currentScreen instanceof ConfirmScreen, "Delete did not ask for confirmation");
        require(Files.exists(expected), "Opening confirmation deleted the archive prematurely");
    }
    private void openRename(MinecraftClient client, Path expected) throws Exception {
        owned(expected);
        require(expected.equals(get(browser, "selected")), "Refusing to rename an unexpected selection");
        click(browser, "Rename selected recording");
        require(client.currentScreen.getClass().getName().equals("zsgrooms.replayviewer.ReplayRenameScreen"), "Rename dialog missing");
        require(get(client.currentScreen, "entry") != null
                && get(get(client.currentScreen, "entry"), "path").equals(expected), "Rename captured a different selection");
        assertArchive(expected);
    }
    private static void renameDraft(MinecraftClient client, String value) throws Exception {
        ((TextFieldWidget) get(client.currentScreen, "filename")).setText(value);
    }
    private void assertRenamedTitle() throws Exception {
        for (Object entry : (List<?>) get(browser, "entries")) if (get(entry, "path").equals(renamed)) {
            require(get(entry, "name").equals("Personal best"), "Renamed title was hidden by generic metadata");
            return;
        }
        throw new IllegalStateException("Renamed entry missing after refresh/reopen");
    }
    private static ButtonWidget button(Screen screen, String label) {
        for (Element element : screen.children()) if (element instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) element;
            if (button.visible && button.getMessage().getString().equals(label)) return button;
        }
        throw new IllegalStateException("Missing browser control: " + label);
    }
    private static void click(Screen screen, String label) {
        ButtonWidget button = button(screen, label);
        require(button.active, "Browser control is disabled: " + label);
        button.onPress();
    }
    private static void assertControlsFit(Screen screen) {
        for (Element element : screen.children()) if (element instanceof AbstractButtonWidget) {
            AbstractButtonWidget button = (AbstractButtonWidget) element;
            if (button.visible) require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= screen.width
                    && button.y + button.getHeight() <= screen.height, "Browser control outside viewport: " + button.getMessage().getString());
        }
    }
    private static void assertViewport(MinecraftClient client, int width, int height) {
        require(client.getWindow().getFramebufferWidth() == width && client.getWindow().getFramebufferHeight() == height,
                "Browser screenshot viewport has not resized to " + width + "x" + height);
    }
    private static byte[] hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }
    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
