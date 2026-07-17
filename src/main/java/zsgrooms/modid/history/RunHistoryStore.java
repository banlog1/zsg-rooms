package zsgrooms.modid.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RunHistoryStore {
    public static final int MAX_RUNS = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type RUN_LIST_TYPE = new TypeToken<List<CompletedRun>>() { }.getType();
    private static final RunHistoryStore DEFAULT = new RunHistoryStore(
            Paths.get("config", "zsg-rooms-run-history.json"));

    private final Path path;
    private List<CompletedRun> runs;

    public RunHistoryStore(Path path) {
        this.path = path;
    }

    public static RunHistoryStore getDefault() {
        return DEFAULT;
    }

    public synchronized List<CompletedRun> getRecentRuns() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<CompletedRun>(runs));
    }

    public synchronized void add(CompletedRun run) {
        if (run == null) {
            return;
        }
        ensureLoaded();
        runs.add(0, run);
        if (runs.size() > MAX_RUNS) {
            runs = new ArrayList<CompletedRun>(runs.subList(0, MAX_RUNS));
        }
        save();
    }

    private void ensureLoaded() {
        if (runs != null) {
            return;
        }
        runs = new ArrayList<CompletedRun>();
        try {
            if (!Files.exists(path)) {
                return;
            }
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            List<CompletedRun> saved = GSON.fromJson(json, RUN_LIST_TYPE);
            if (saved != null) {
                runs.addAll(saved.subList(0, Math.min(MAX_RUNS, saved.size())));
            }
        } catch (Exception ignored) {
            runs.clear();
        }
    }

    private void save() {
        Path parent = path.getParent();
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(temporary, GSON.toJson(runs, RUN_LIST_TYPE).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignoredDelete) {
            }
        }
    }
}
