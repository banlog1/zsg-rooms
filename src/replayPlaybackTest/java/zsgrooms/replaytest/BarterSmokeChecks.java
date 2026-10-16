package zsgrooms.replaytest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.CloudRenderMode;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Real replay packets, isolated synthetic metadata, and client-only entities. Never writes to the source. */
final class BarterSmokeChecks {
    private static final String TRACK = "zsg-rooms/barters.json";
    private static final int TRADES = 31;
    private static final int ENTITY_BASE = 901100;
    private int step, frozen, snapshotTime, freezeSamples, jumpTarget = -1, jumpCalls, smallLastPage;
    private boolean jumpCallbackComplete, freezePending;
    private long next, freezeReadyAt, freezeDeadline;
    private Path source, directory;
    private byte[] sourceHash;
    private Object controls, analysis, index, original, history, missing, partial;
    private Screen screen, parent;
    private Vec3d cameraPosition;
    private CloudRenderMode originalCloudMode;
    private final List<PiglinEntity> pigs = new ArrayList<>();

    File prepare(MinecraftClient client, File input) throws Exception {
        Path run = client.runDirectory.toPath().toRealPath();
        require(run.getFileName().toString().equals("replay-playback-test")
                && run.getParent().getFileName().toString().equals("run"), "Unexpected barter smoke run directory: " + run);
        source = input.toPath().toRealPath();
        sourceHash = hash(source);
        directory = Files.createTempDirectory(run, "barter-smoke-").toRealPath();
        require(!source.startsWith(directory), "Source recording is inside the fixture directory");
        Path replay = owned("barter-fixture.mcpr");
        int duration;
        try (ZipFile zip = new ZipFile(source.toFile());
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(replay))) {
            JsonObject races;
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(zip.getEntry("zsg-rooms/races.json")), StandardCharsets.UTF_8)) {
                races = new JsonParser().parse(reader).getAsJsonObject();
            }
            duration = races.get("durationMillis").getAsInt();
            require(duration >= 16000, "Barter smoke fixture needs at least sixteen seconds of replay packets");
            // Only the fresh copy gets deterministic reset coverage; original packet bytes are retained.
            JsonArray intervals = new JsonArray();
            intervals.add(interval(0, 12000, 0));
            intervals.add(interval(12000, duration, 1));
            races.add("intervals", intervals);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[65536];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(TRACK) || entry.getName().equals("zsg-rooms/races.json")) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream stream = zip.getInputStream(entry)) {
                    int count;
                    while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                output.closeEntry();
            }
            write(output, "zsg-rooms/races.json", races.toString());
            write(output, TRACK, fixture(false).toString());
        }
        Path absent = owned("missing-history.zip"), limited = owned("partial-history.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(absent))) {
            write(output, "test-owned.txt", "No optional barter track");
        }
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(limited))) {
            write(output, TRACK, fixture(true).toString());
        }
        Class<?> type = Class.forName("zsgrooms.replayviewer.BarterHistory");
        history = callStatic(type, "read", new Class<?>[]{Path.class, int.class}, replay, duration);
        missing = callStatic(type, "read", new Class<?>[]{Path.class, int.class}, absent, duration);
        partial = callStatic(type, "read", new Class<?>[]{Path.class, int.class}, limited, duration);
        require((Boolean) get(history, "available") && !(Boolean) get(history, "truncated"), "Fixture history did not parse");
        require(!(Boolean) get(missing, "available") && (Boolean) get(partial, "truncated"), "Optional history availability/partial flags lost");
        require(list(history, "events").size() == 35, "Fixture parser lost barter events");
        require(Arrays.equals(sourceHash, hash(source)), "Preparing barter fixtures changed the source recording");
        ZsgRooms.LOGGER.info("[ReplayBarterSmoke] Test-owned replay and JSON fixtures: {}; source untouched", directory);
        return replay.toFile();
    }

    boolean tick(ReplayHandler handler, MinecraftClient client) throws Exception {
        try {
            boolean complete = tickChecks(handler, client);
            if (complete) restoreClouds(client);
            return complete;
        } catch (Exception | Error error) {
            restoreClouds(client);
            throw error;
        }
    }

    private boolean tickChecks(ReplayHandler handler, MinecraftClient client) throws Exception {
        if (cameraPosition != null && handler.getCameraEntity() != null) {
            handler.getCameraEntity().setCameraPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            handler.getCameraEntity().setCameraRotation(0, 0, 0);
        }
        if (client.world == null || handler.getCameraEntity() == null) return false;
        if (screen != null && client.currentScreen == screen) {
            frozen(handler);
            if (freezePending) {
                long now = System.nanoTime();
                require(now <= freezeDeadline, "Timed out establishing the paused barter clock at step " + step);
                if (++freezeSamples < 2 || now < freezeReadyAt) return false;
                freezePending = false;
                ZsgRooms.LOGGER.info("[ReplayBarterSmoke] Paused clock stable at {}ms; history snapshot {}ms", frozen, snapshotTime);
            }
        }
        if (System.nanoTime() < next) return false;
        if (screen != null && client.currentScreen == screen) {
            screen.tick();
            frozen(handler);
        }
        switch (step) {
            case 0:
                if (handler.getReplaySender().currentTimeStamp() < 2500) return false;
                controls = get(field(Class.forName("zsgrooms.replayviewer.ReplayViewer"), "instance").get(null), "controls");
                analysis = get(controls, "analysis");
                index = get(controls, "recordingIndex");
                if (get(index, "recording") == null || get(index, "barters") == null) return false;
                require((Boolean) get(get(index, "barters"), "available"), "Live RecordingIndex did not load the fixture history");
                original = call(controls, "snapshot");
                handler.getReplaySender().setReplaySpeed(0);
                set(analysis, "dragonTrail", false); set(analysis, "piglinTrail", false); set(analysis, "playerTrail", false);
                call(get(analysis, "mobTrails"), "clear");
                client.options.guiScale = 2;
                client.onResolutionChanged();
                resize(client, 1280, 720);
                seek(handler, 10000);
                break;
            case 1:
                call(controls, "openSettings");
                parent = client.currentScreen;
                click(parent, "Analysis");
                require(button(parent, "Barters").active, "Barter entry point is unavailable");
                handler.getReplaySender().setReplaySpeed(2);
                click(parent, "Barters");
                screen = client.currentScreen;
                require(screen.getClass().getSimpleName().equals("BarterScreen"), "Settings did not open BarterScreen");
                beginFreeze(handler);
                require(snapshotTime >= 10000 && snapshotTime < 10500, "Opening barter history advanced to a future trade");
                require(!handler.getOverlay().isVisible(), "Barter screen left the replay overlay visible");
                break;
            case 2:
                assertStats(0, TRADES, 2);
                assertTotal("minecraft:gravel", 60); assertTotal("minecraft:ender_pearl", 34);
                assertOrder(false);
                viewport(client, 1280, 720); bounds();
                capture(client, "barter-history-wide.png");
                resize(client, 640, 480);
                break;
            case 3:
                viewport(client, 640, 480); bounds();
                capture(client, "barter-history-small.png");
                paging();
                search("MINECRAFT:ENDER_PEARL");
                require(list(screen, "trades").size() == 11 && (Integer) get(screen, "page") == 0, "Registry/type filter did not reset paging or match all stacks");
                searchSelectionChecks();
                search("  EnDeR PeArL  ");
                require(list(screen, "trades").size() == 11, "Localized item-name search is not case/whitespace insensitive");
                search("minecraft:diamond");
                require(list(screen, "trades").isEmpty(), "Search leaked future/reset diamond trades");
                search("no-such-barter-item");
                require(list(screen, "trades").isEmpty() && !button(screen, ">").active, "Empty filter retained rows/paging");
                search("");
                click(screen, "Items");
                require((Boolean) get(screen, "items") && list(screen, "totals").size() == 2, "Items tab did not expose aggregate quantities");
                require(!button(screen, "Newest first").active, "Trade sorting is enabled in totals view");
                assertTotalOrder();
                break;
            case 4:
                bounds(); capture(client, "barter-items-small.png");
                rows().get(0).onPress();
                require(!(Boolean) get(screen, "items") && list(screen, "trades").size() == 30,
                        "Item drill-down did not filter gravel history");
                require(!((String) get(screen, "query")).isEmpty(), "Item drill-down lost its search query");
                search("");
                click(screen, "Newest first");
                require((Boolean) get(screen, "oldest"), "Sort toggle did not change direction");
                assertOrder(true);
                while (button(screen, ">").active) click(screen, ">");
                smallLastPage = (Integer) get(screen, "page");
                require(smallLastPage > 0 && !rows().isEmpty(), "Small viewport did not reach its populated last history page");
                resize(client, 1280, 720);
                break;
            case 5:
                viewport(client, 1280, 720); bounds();
                require((Boolean) get(screen, "oldest") && list(screen, "trades").size() == TRADES, "Resize lost history state");
                int wideRows = (Integer) get(get(screen, "layout"), "rows");
                int wideLastPage = (TRADES - 1) / wideRows;
                require(smallLastPage > wideLastPage && (Integer) get(screen, "page") == wideLastPage,
                        "Small last-page index was not clamped after resizing wide");
                require(rows().size() == TRADES - wideLastPage * wideRows && !button(screen, ">").active,
                        "Resized last page is empty, incomplete, or still allows forward paging");
                assertVisibleRows(wideLastPage * wideRows);
                capture(client, "barter-oldest-wide.png");
                Screen closed = screen;
                click(screen, "Done");
                require(client.currentScreen == parent && handler.getReplaySender().getReplaySpeed() == 2
                        && !handler.getReplaySender().paused(), "Done did not restore parent/running speed");
                require(handler.getOverlay().isVisible(), "Done did not restore replay overlay");
                handler.getReplaySender().setReplaySpeed(0);
                closed.onClose();
                require(handler.getReplaySender().paused(), "A second close reapplied stale speed");
                inject(missing, "No barter history in this recording");
                open(handler, client, null);
                break;
            case 6:
                require(!(Boolean) get(get(screen, "loaded"), "available") && list(screen, "trades").isEmpty(), "Legacy recording invented barter history");
                require(((String) get(screen, "status")).contains("No barter history"), "Legacy history status missing");
                capture(client, "barter-unavailable-wide.png");
                toggle("Bartering labels", true); toggle("Cluster counter", true);
                require((Boolean) get(analysis, "barteringLabels") && (Boolean) get(analysis, "piglinCounter"), "Legacy history disabled visual settings");
                inject(null, "Reading barter history..."); screen.tick();
                require(list(screen, "trades").isEmpty(), "Loading history retained stale trades");
                inject(partial, "Partial barter history"); screen.tick();
                break;
            case 7:
                assertStats(0, TRADES, 2);
                require((Boolean) get(get(screen, "loaded"), "truncated"), "Asynchronously supplied partial history flag lost");
                capture(client, "barter-partial-wide.png");
                screen.onClose();
                require(handler.getReplaySender().paused(), "Closing a paused barter screen resumed playback");
                inject(history, "");
                seek(handler, 3500); open(handler, client, null);
                break;
            case 8:
                assertStats(0, 0, 0);
                require(list(screen, "totals").isEmpty(), "Backward seek retained future totals");
                screen.onClose(); seek(handler, 14000); open(handler, client, null);
                break;
            case 9:
                assertStats(1, 2, 1); assertTotal("minecraft:iron_nugget", 96);
                require(list(screen, "totals").size() == 1, "Reset retained previous-world item totals");
                capture(client, "barter-reset-world-wide.png");
                screen.onClose(); seek(handler, 10000);
                handler.getReplaySender().setReplaySpeed(0.5);
                open(handler, client, target -> {
                    jumpTarget = target; jumpCalls++;
                    try {
                        // Terrain/loading replacement may remove the screen before a real seek returns.
                        screen.removed();
                        frozen(handler);
                        require(!handler.getOverlay().isVisible() && !(Boolean) get(screen, "closed"),
                                "Removal during a trade jump prematurely restored/closed the barter screen");
                        call(controls, "seekTo", new Class<?>[]{int.class}, target);
                        require(handler.getReplaySender().paused(), "Trade seek resumed playback before its callback finished");
                        jumpCallbackComplete = true;
                    }
                    catch (Exception error) { throw new IllegalStateException("Barter seek callback failed", error); }
                });
                break;
            case 10:
                ButtonWidget trade = rows().get(1);
                int target = (Integer) get(list(screen, "trades").get(1), "time");
                trade.onPress();
                require(jumpCallbackComplete && jumpCalls == 1 && jumpTarget == target && Math.abs(handler.getReplaySender().currentTimeStamp() - target) <= 1,
                        "Trade row did not seek to its exact event through the callback");
                require(client.currentScreen == null && handler.getOverlay().isVisible()
                        && !handler.getReplaySender().paused() && handler.getReplaySender().getReplaySpeed() == 0.5,
                        "Trade jump failed to close/restore playback speed");
                trade.onPress(); screen.onClose();
                require(jumpCalls == 1 && client.currentScreen == null, "Closed history accepted a stale row action");
                handler.getReplaySender().setReplaySpeed(0);
                seek(handler, 10000);
                spawn(handler, client);
                snapshotChecks();
                handler.getOverlay().setMouseVisible(false);
                break;
            case 11:
                require((Boolean) get(analysis, "counterAvailable") && (Integer) get(analysis, "piglins") >= 3,
                        "Synthetic adult piglins were not counted in the loaded replay world");
                require(bartering(pigs.get(0)), "Gold-offhand adult is not recognized as bartering");
                capture(client, "barter-icon-label-wide.png");
                set(analysis, "barteringLabels", false);
                break;
            case 12:
                capture(client, "barter-icon-label-off-wide.png");
                break;
            case 13:
                BufferedImage enabled = image(client, "barter-icon-label-wide.png", 1280, 720);
                BufferedImage disabled = image(client, "barter-icon-label-off-wide.png", 1280, 720);
                if (enabled == null || disabled == null) return false;
                require(amber(enabled) > amber(disabled) + 8, "Bartering label did not produce visible amber pixels, or disabling labels left them visible");
                face(enabled, client);
                set(analysis, "barteringLabels", true);
                resize(client, 640, 480);
                break;
            case 14:
                viewport(client, 640, 480);
                capture(client, "barter-icon-label-small.png");
                break;
            case 15:
                BufferedImage small = image(client, "barter-icon-label-small.png", 640, 480);
                if (small == null) return false;
                require(amber(small) > 8, "Small viewport lost bartering labels");
                face(small, client);
                restoreClouds(client);
                handler.getReplaySender().setReplaySpeed(1.75);
                open(handler, client, value -> jumpCalls++);
                break;
            case 16:
                assertStats(0, TRADES, 2);
                assertTotal("minecraft:ender_pearl", 34);
                replacementChecks(handler, client, 1.75);
                handler.getReplaySender().setReplaySpeed(0);
                open(handler, client, value -> jumpCalls++);
                break;
            case 17:
                assertStats(0, TRADES, 2);
                replacementChecks(handler, client, 0);
                handler.getReplaySender().setReplaySpeed(0);
                client.openScreen(null);
                break;
            case 18:
                for (PiglinEntity pig : pigs) if (client.world.getEntityById(pig.getEntityId()) == pig) client.world.removeEntity(pig.getEntityId());
                cameraPosition = null;
                call(controls, "restore", new Class<?>[]{original.getClass()}, original);
                require(Arrays.equals(sourceHash, hash(source)), "Barter smoke changed the user recording");
                ZsgRooms.LOGGER.info("[ReplayBarterSmoke] PASS: live settings entry, parsed history, unavailable/partial/loading, future and reset filtering, exact totals, type/name search, cursor/selection without rebuild, sorting, paging/drill-down, small-last-page resize clamp, frozen playback, jump/removal guard, close/replacement pause and speed restoration, idempotent stale actions, state snapshots, adult gold-offhand labels and counter icon pixels, 640x480 and 1280x720 screenshots; source SHA-256 unchanged; fixtures {}", directory);
                return true;
            default: throw new IllegalStateException("Invalid barter smoke step " + step);
        }
        ZsgRooms.LOGGER.info("[ReplayBarterSmoke] Completed step {}", step);
        step++;
        next = System.nanoTime() + 650000000L;
        return false;
    }

    private void open(ReplayHandler handler, MinecraftClient client, IntConsumer seek) throws Exception {
        Constructor<?> constructor = Class.forName("zsgrooms.replayviewer.BarterScreen").getDeclaredConstructor(
                Screen.class, analysis.getClass(), index.getClass(), ReplayHandler.class, IntConsumer.class);
        constructor.setAccessible(true);
        screen = (Screen) constructor.newInstance(parent, analysis, index, handler, seek == null ? (IntConsumer) value -> {
            throw new IllegalStateException("Unexpected seek from read-only check: " + value);
        } : seek);
        client.openScreen(screen);
        beginFreeze(handler);
    }

    private void beginFreeze(ReplayHandler handler) throws Exception {
        require(handler.getReplaySender().paused(), "Opening barter history did not pause playback");
        require(!(handler.getReplaySender() instanceof FullReplaySender)
                || !((FullReplaySender) handler.getReplaySender()).isHurrying(), "Barter clock baseline was captured during a seek");
        snapshotTime = (Integer) get(screen, "time");
        // Running projection rounds at fractional speeds; snapshot time must be read after pausing.
        // Keep the post-pause baseline fixed; never absorb later movement into a new expectation.
        frozen = handler.getReplaySender().currentTimeStamp();
        require(frozen >= 0 && frozen == snapshotTime,
                "Barter history must snapshot the post-pause clock at step " + step + ": snapshot " + snapshotTime
                        + ", paused sender " + frozen + "; capture time after setReplaySpeed(0)");
        long now = System.nanoTime();
        freezeSamples = 0;
        freezeReadyAt = now + 150000000L;
        freezeDeadline = now + 5000000000L;
        freezePending = true;
        frozen(handler);
    }

    private void frozen(ReplayHandler handler) {
        require(handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == frozen,
                "Open barter screen moved the paused replay at step " + step + ": expected " + frozen + ", got "
                        + handler.getReplaySender().currentTimeStamp() + "; history snapshot " + snapshotTime);
    }

    private void inject(Object value, String status) throws Exception { set(index, "barters", value); set(index, "barterStatus", status); }
    private void search(String query) throws Exception { ((TextFieldWidget) get(screen, "search")).setText(query); }

    private void searchSelectionChecks() throws Exception {
        click(screen, ">");
        int page = (Integer) get(screen, "page");
        TextFieldWidget search = (TextFieldWidget) get(screen, "search");
        String query = search.getText();
        Object stats = get(screen, "stats");
        List<?> children = new ArrayList<>(screen.children());
        List<?> trades = new ArrayList<>(list(screen, "trades"));
        List<?> totals = new ArrayList<>(list(screen, "totals"));
        search.setSelected(true);
        require(search.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0) && search.getCursor() == 0,
                "Search did not accept Home cursor movement");
        require(search.keyPressed(GLFW.GLFW_KEY_END, 0, 0) && search.getCursor() == query.length(),
                "Search did not accept End cursor movement");
        // The same cursor/selection operations used by vanilla Ctrl+A, without native modifier state.
        search.setCursorToEnd();
        search.setSelectionEnd(0);
        require(search.getSelectedText().equals(query), "Select-all lost the search selection");
        search.setCursor(4);
        search.setSelectionEnd(9);
        require(search.getSelectedText().equals(query.substring(4, 9)), "Search lost its partial text selection");
        require(get(screen, "search") == search && children.equals(screen.children()),
                "Cursor/selection-only notifications rebuilt the search widget or controls");
        require(query.equals(get(screen, "query")) && (Integer) get(screen, "page") == page
                && get(screen, "stats") == stats && trades.equals(list(screen, "trades")) && totals.equals(list(screen, "totals")),
                "Cursor/selection-only notifications changed query, page, or history statistics");
        search.setCursorToEnd(); search.setSelectionEnd(0);
        search.write("minecraft:gravel");
        require("minecraft:gravel".equals(get(screen, "query")) && list(screen, "trades").size() == 30
                && (Integer) get(screen, "page") == 0, "Replacing selected search text did not filter/reset the page");
    }

    private void replacementChecks(ReplayHandler handler, MinecraftClient client, double speed) throws Exception {
        Screen removed = screen;
        Object stats = get(removed, "stats");
        List<?> trades = new ArrayList<>(list(removed, "trades"));
        List<?> totals = new ArrayList<>(list(removed, "totals"));
        List<?> parentChildren = new ArrayList<>(parent.children());
        ButtonWidget staleRow = rows().get(0);
        int calls = jumpCalls;
        Screen replacement = new Screen(new LiteralText("Barter smoke replacement")) {
            @Override public boolean isPauseScreen() { return false; }
        };
        client.openScreen(replacement);
        require(client.currentScreen == replacement && (Boolean) get(removed, "closed")
                && handler.getOverlay().isVisible() && handler.getReplaySender().paused() == (speed == 0)
                && (speed == 0 || handler.getReplaySender().getReplaySpeed() == speed),
                "External screen replacement failed to restore pause/speed/overlay without reopening the parent");
        double changedSpeed = speed == 0 ? 0.75 : 0;
        handler.getReplaySender().setReplaySpeed(changedSpeed);
        handler.getOverlay().setVisible(false);
        removed.removed(); removed.onClose(); staleRow.onPress(); removed.removed();
        require(client.currentScreen == replacement && !handler.getOverlay().isVisible() && jumpCalls == calls
                && handler.getReplaySender().paused() == (changedSpeed == 0)
                && (changedSpeed == 0 || handler.getReplaySender().getReplaySpeed() == changedSpeed),
                "Repeated removal/close or a stale row overwrote replacement screen, speed, overlay, or seek state");
        require(get(removed, "stats") == stats && trades.equals(list(removed, "trades"))
                && totals.equals(list(removed, "totals")) && parentChildren.equals(parent.children()),
                "External removal mutated retained history statistics or parent controls");
        handler.getOverlay().setVisible(true);
    }

    private void assertStats(int world, int count, int piglins) throws Exception {
        Object stats = get(screen, "stats");
        require(stats != null && list(stats, "events").size() == count && list(screen, "trades").size() == count,
                "Incorrect current-world/history count at step " + step);
        require((Integer) get(stats, "piglins") == piglins, "Unique piglin total is incorrect");
        for (Object event : list(stats, "events")) require((Integer) get(event, "world") == world
                && (Integer) get(event, "time") <= snapshotTime, "History leaked a future/reset event");
    }

    private void assertTotal(String item, long count) throws Exception {
        for (Object total : list(screen, "totals")) {
            ItemStack stack = (ItemStack) get(total, "item");
            if (Registry.ITEM.getId(stack.getItem()).toString().equals(item)) {
                require(((Number) get(total, "count")).longValue() == count, "Wrong aggregate for " + item);
                return;
            }
        }
        throw new IllegalStateException("Missing item total " + item);
    }

    private void assertTotalOrder() throws Exception {
        long previous = Long.MAX_VALUE;
        for (Object total : list(screen, "totals")) {
            long count = ((Number) get(total, "count")).longValue();
            require(count <= previous, "Item totals are not sorted by quantity"); previous = count;
        }
    }

    private void assertOrder(boolean oldest) throws Exception {
        int previous = oldest ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Object event : list(screen, "trades")) {
            int value = (Integer) get(event, "time");
            require(oldest ? value >= previous : value <= previous, "Trade order does not match the sort toggle"); previous = value;
        }
    }

    private void paging() throws Exception {
        int rows = (Integer) get(get(screen, "layout"), "rows");
        int pages = (TRADES + rows - 1) / rows;
        require(pages > 1 && !button(screen, "<").active, "Fixture did not exercise first-page boundaries");
        int seen = 0;
        for (int page = 0; page < pages; page++) {
            List<ButtonWidget> buttons = rows();
            require((Integer) get(screen, "page") == page && buttons.size() == Math.min(rows, TRADES - seen), "Page skipped or duplicated visible trade rows");
            assertVisibleRows(seen);
            bounds(); seen += buttons.size();
            if (page + 1 < pages) click(screen, ">");
        }
        require(seen == TRADES && !button(screen, ">").active, "Last-page boundary is incorrect");
        screen.mouseScrolled(0, 0, -1);
        require((Integer) get(screen, "page") == pages - 1, "Scroll passed the last page");
        for (int i = 0; i <= pages; i++) screen.mouseScrolled(0, 0, 1);
        require((Integer) get(screen, "page") == 0 && !button(screen, "<").active, "Scroll passed the first page");
        click(screen, ">");
    }

    private void assertVisibleRows(int offset) throws Exception {
        List<ButtonWidget> buttons = rows();
        Map<?, ?> tips = (Map<?, ?>) get(screen, "tips");
        for (int row = 0; row < buttons.size(); row++) {
            Object event = list(screen, "trades").get(offset + row);
            String time = (String) callStatic(Class.forName("zsgrooms.replayviewer.ReplayTimings"), "clock", new Class<?>[]{long.class}, ((Number) get(event, "time")).longValue());
            require(((String) tips.get(buttons.get(row))).startsWith("Replay " + time + "\n"), "Visible page row does not match its event timestamp");
        }
    }

    private List<ButtonWidget> rows() {
        List<ButtonWidget> result = new ArrayList<>();
        for (Element child : screen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (button.visible && button.getMessage().getString().isEmpty()) result.add(button);
        }
        return result;
    }

    private void toggle(String label, boolean enabled) {
        for (Element child : screen.children()) if (child instanceof CheckboxWidget) {
            CheckboxWidget box = (CheckboxWidget) child;
            if (box.getMessage().getString().equals(label)) {
                if (box.isChecked() != enabled) box.onPress();
                require(box.isChecked() == enabled && box.active, "Barter checkbox failed: " + label); return;
            }
        }
        throw new IllegalStateException("Missing barter checkbox: " + label);
    }

    private void snapshotChecks() throws Exception {
        set(analysis, "barteringLabels", true); set(analysis, "piglinCounter", true);
        Object enabled = call(controls, "snapshot");
        set(analysis, "barteringLabels", false); set(analysis, "piglinCounter", false);
        call(controls, "restore", new Class<?>[]{enabled.getClass()}, enabled);
        require((Boolean) get(analysis, "barteringLabels") && (Boolean) get(analysis, "piglinCounter"), "Snapshot failed to preserve enabled labels/counter");
        set(analysis, "barteringLabels", false);
        Object disabled = call(controls, "snapshot");
        set(analysis, "barteringLabels", true);
        call(controls, "restore", new Class<?>[]{disabled.getClass()}, disabled);
        require(!(Boolean) get(analysis, "barteringLabels") && (Boolean) get(analysis, "piglinCounter"), "Snapshot coupled labels to the cluster setting");
        set(analysis, "barteringLabels", true);
    }

    private void spawn(ReplayHandler handler, MinecraftClient client) throws Exception {
        originalCloudMode = client.options.cloudRenderMode;
        client.options.cloudRenderMode = CloudRenderMode.OFF;
        client.options.hudHidden = false;
        client.options.perspective = 0;
        client.options.fov = 70;
        Object menu = get(controls, "camera");
        call(menu, "setSelected", new Class<?>[]{int.class}, 0);
        call(controls, "updateCamera");
        Entity player = client.world.getPlayers().stream().filter(value -> !(value instanceof CameraEntity)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Recorded player missing from live replay"));
        Vec3d anchor = player.getPos().add(0, 40, 0);
        cameraPosition = anchor.add(0.15, 1.5, -4);
        for (int i = 0; i < 3; i++) {
            require(client.world.getEntityById(ENTITY_BASE + i) == null, "Synthetic barter entity ID is already occupied");
            PiglinEntity pig = new PiglinEntity(EntityType.PIGLIN, client.world);
            pig.setBaby(false);
            pig.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
            position(pig, anchor.x + i * 0.15, anchor.y, anchor.z + 3);
            require(pig.squaredDistanceTo(player) <= 4096, "Synthetic barter piglin is outside the recorded-player counter range");
            client.world.addEntity(ENTITY_BASE + i, pig);
            pigs.add(pig);
        }
        PiglinEntity pig = pigs.get(0);
        require(bartering(pig), "Adult offhand gold did not enter the tracked admiring state");
        pig.setBaby(true); require(!bartering(pig), "Baby piglin counted as bartering"); pig.setBaby(false);
        pig.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        pig.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT));
        require(!bartering(pig), "Main-hand gold incorrectly counted as bartering");
        pig.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        pig.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
        set(analysis, "piglinCounter", true); call(analysis, "clear");
    }

    private void restoreClouds(MinecraftClient client) {
        if (originalCloudMode == null) return;
        client.options.cloudRenderMode = originalCloudMode;
        originalCloudMode = null;
    }

    private static boolean bartering(PiglinEntity pig) throws Exception {
        return (Boolean) callStatic(Class.forName("zsgrooms.replayviewer.PiglinVisuals"), "bartering", new Class<?>[]{PiglinEntity.class}, pig);
    }

    private static void position(Entity entity, double x, double y, double z) {
        entity.refreshPositionAndAngles(x, y, z, 0, 0);
        entity.prevX = entity.lastRenderX = x; entity.prevY = entity.lastRenderY = y; entity.prevZ = entity.lastRenderZ = z;
    }

    private void bounds() throws Exception {
        List<AbstractButtonWidget> visible = new ArrayList<>();
        for (Element child : screen.children()) if (child instanceof AbstractButtonWidget && ((AbstractButtonWidget) child).visible) {
            AbstractButtonWidget button = (AbstractButtonWidget) child;
            require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= screen.width
                    && button.y + button.getHeight() <= screen.height, "Barter control outside viewport: " + button.getMessage().getString());
            visible.add(button);
        }
        for (int i = 0; i < visible.size(); i++) for (int j = i + 1; j < visible.size(); j++) {
            AbstractButtonWidget a = visible.get(i), b = visible.get(j);
            require(a.x + a.getWidth() <= b.x || b.x + b.getWidth() <= a.x || a.y + a.getHeight() <= b.y || b.y + b.getHeight() <= a.y,
                    "Overlapping barter controls: " + a.getMessage().getString() + " / " + b.getMessage().getString());
        }
        TextFieldWidget search = (TextFieldWidget) get(screen, "search");
        require(search.x >= 0 && search.y >= 0 && search.x + search.getWidth() <= screen.width
                && search.y + search.getHeight() <= screen.height, "Barter search outside viewport");
    }

    private static int amber(BufferedImage image) {
        int count = 0;
        for (int y = image.getHeight() / 6; y < image.getHeight() * 3 / 4; y++) for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
            int color = image.getRGB(x, y);
            int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
            // World-space text uses a lightmap; compare amber chroma rather than full-bright RGB.
            if (red >= 140 && green >= 85 && blue <= 125 && Math.abs(green * 255 - red * 191) <= red * 12
                    && Math.abs(blue * 255 - red * 71) <= red * 12) count++;
        }
        return count;
    }

    private void face(BufferedImage image, MinecraftClient client) throws Exception {
        int count = (Integer) get(analysis, "piglins");
        int width = (Integer) callStatic(Class.forName("zsgrooms.replayviewer.PiglinVisuals"), "counterWidth", new Class<?>[]{int.class, boolean.class}, count, true);
        int left = client.getWindow().getScaledWidth() - 12 - width;
        double scaleX = (double) image.getWidth() / client.getWindow().getScaledWidth();
        double scaleY = (double) image.getHeight() / client.getWindow().getScaledHeight();
        int[] palette = {0x503A32, 0x241C18, 0xD39A7D, 0xA96F55, 0xE8B392, 0xFFE8BC, 0x9B6254};
        int pixels = 0, colors = 0;
        for (int color : palette) {
            boolean found = false;
            for (int y = (int) (36 * scaleY); y < (int) (48 * scaleY); y++) for (int x = (int) (left * scaleX); x < (int) ((left + 16) * scaleX); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == color) { pixels++; found = true; }
            }
            if (found) colors++;
        }
        require(pixels >= 32 && colors >= 4, "Piglin counter face did not render its palette in the HUD icon bounds");
    }

    private Path owned(String name) {
        Path value = directory.resolve(name).toAbsolutePath().normalize();
        require(value.startsWith(directory) && !value.equals(directory) && !value.equals(source), "Unowned barter fixture path: " + value);
        return value;
    }

    private static JsonObject fixture(boolean truncated) {
        JsonObject result = new JsonObject();
        result.addProperty("version", 1); result.addProperty("protocol", 736); result.addProperty("truncated", truncated);
        JsonArray events = new JsonArray();
        for (int i = 0; i < 30; i++) events.add(event(4000 + i * 100, 0, i % 2,
                i % 3 == 0 ? new ItemStack[]{new ItemStack(Items.GRAVEL, 2), new ItemStack(Items.ENDER_PEARL, 3)}
                        : new ItemStack[]{new ItemStack(Items.GRAVEL, 2)}));
        events.add(event(10000, 0, 0, new ItemStack(Items.ENDER_PEARL, 4)));
        events.add(event(10500, 0, 0, new ItemStack(Items.DIAMOND, 63)));
        events.add(event(12500, 1, 0, new ItemStack(Items.IRON_NUGGET, 64)));
        events.add(event(13500, 1, 0, new ItemStack(Items.IRON_NUGGET, 32)));
        events.add(event(14500, 1, 0, new ItemStack(Items.DIAMOND, 64)));
        result.add("events", events); return result;
    }

    private static JsonObject event(int time, int world, int pig, ItemStack... stacks) {
        JsonObject event = new JsonObject();
        event.addProperty("time", time); event.addProperty("world", world);
        event.addProperty("dimension", "minecraft:the_nether");
        event.addProperty("piglin", pig == 0 ? "00000000-0000-0000-0000-000000000001" : "00000000-0000-0000-0000-000000000002");
        event.addProperty("x", 1.5); event.addProperty("y", 64); event.addProperty("z", -2.5);
        JsonArray items = new JsonArray();
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        try {
            for (ItemStack stack : stacks) {
                data.clear(); data.writeItemStack(stack);
                byte[] bytes = new byte[data.readableBytes()]; data.readBytes(bytes);
                items.add(Base64.getEncoder().encodeToString(bytes));
            }
        } finally { data.release(); }
        event.add("items", items); return event;
    }

    private static JsonObject interval(int start, int end, int world) {
        JsonObject result = new JsonObject();
        result.addProperty("startReplayMillis", start); result.addProperty("endReplayMillis", end); result.addProperty("worldIndex", world);
        return result;
    }

    private static void write(ZipOutputStream output, String name, String data) throws Exception {
        output.putNextEntry(new ZipEntry(name)); output.write(data.getBytes(StandardCharsets.UTF_8)); output.closeEntry();
    }

    private static byte[] hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    private static void seek(ReplayHandler handler, int time) throws Exception { handler.doJump(time, true); handler.getReplaySender().setReplaySpeed(0); }
    private static void resize(MinecraftClient client, int width, int height) { GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height); }
    private static void viewport(MinecraftClient client, int width, int height) {
        require(client.getWindow().getFramebufferWidth() == width && client.getWindow().getFramebufferHeight() == height, "Barter screenshot viewport has not resized to " + width + "x" + height);
    }
    private BufferedImage image(MinecraftClient client, String name, int width, int height) throws Exception {
        File file = new File(client.runDirectory, "screenshots/" + screenshotName(name));
        if (!file.isFile()) return null;
        BufferedImage image = javax.imageio.ImageIO.read(file);
        require(image != null && image.getWidth() == width && image.getHeight() == height, "Invalid barter screenshot: " + file); return image;
    }
    private String screenshotName(String name) { return name.replace(".png", "-" + directory.getFileName() + ".png"); }
    private void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, screenshotName(name), client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static ButtonWidget button(Screen screen, String label) {
        for (Element child : screen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (button.visible && button.getMessage().getString().equals(label)) return button;
        }
        throw new IllegalStateException("Missing barter control: " + label);
    }
    private static void click(Screen screen, String label) { ButtonWidget button = button(screen, label); require(button.active, "Disabled barter control: " + label); button.onPress(); }
    private static List<?> list(Object object, String name) throws Exception { return (List<?>) get(object, name); }
    private static Object get(Object object, String name) throws Exception { return field(object.getClass(), name).get(object); }
    private static void set(Object object, String name, Object value) throws Exception { field(object.getClass(), name).set(object, value); }
    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object callStatic(Class<?> type, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(null, args);
    }
    private static Object call(Object object, String name) throws Exception { return call(object, name, new Class<?>[0]); }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
