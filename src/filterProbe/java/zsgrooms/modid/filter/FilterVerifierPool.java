package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded private IPC. IO threads dispatch jobs; Minecraft runs only in isolated JVMs. */
final class FilterVerifierPool implements AutoCloseable {
    private final ArrayBlockingQueue<Job> queue;
    private final List<Process> processes = new ArrayList<Process>();
    private final List<Path> directories = new ArrayList<Path>();
    private final List<Thread> dispatchers = new ArrayList<Thread>();
    private volatile boolean closed;
    private volatile Throwable failure;
    private final Thread shutdownHook = new Thread(this::close, "Filter verifier shutdown");
    final long startupMs;

    FilterVerifierPool(String runId, int workers, int capacity) throws Exception {
        if (workers < 1 || workers > 2 || capacity < 1 || capacity > 16) throw new IllegalArgumentException("Invalid verifier bounds");
        queue = new ArrayBlockingQueue<Job>(capacity);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        long start = System.nanoTime();
        try {
            for (int i = 0; i < workers; i++) {
                Path dir = Paths.get("workers", runId, Integer.toString(i)).toAbsolutePath();
                Files.createDirectories(dir);
                Files.copy(Paths.get("eula.txt"), dir.resolve("eula.txt"));
                Properties settings = new Properties();
                settings.setProperty("level-seed", "1");
                settings.setProperty("level-name", "bootstrap");
                settings.setProperty("online-mode", "false");
                settings.setProperty("server-ip", "127.0.0.1");
                settings.setProperty("view-distance", "2");
                settings.setProperty("max-tick-time", "-1");
                try (java.io.OutputStream output = Files.newOutputStream(dir.resolve("server.properties"))) {
                    settings.store(output, "Offline verifier bootstrap");
                }
                Process process = new ProcessBuilder(command()).directory(dir.toFile()).redirectErrorStream(true)
                        .redirectOutput(dir.resolve("worker.log").toFile()).start();
                processes.add(process);
                directories.add(dir);
                awaitFile(process, dir.resolve("ready.json"), System.nanoTime() + TimeUnit.MINUTES.toNanos(3));
                Thread dispatcher = new Thread(() -> dispatch(process, dir), "Filter verifier IO " + i);
                dispatcher.setDaemon(true);
                dispatchers.add(dispatcher);
                dispatcher.start();
            }
        } catch (Exception failure) { close(); throw failure; }
        startupMs = (System.nanoTime() - start) / 1000000;
    }

    private static List<String> command() {
        List<String> command = new ArrayList<String>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        // Inherit only development-loader properties, never agents/debug ports or operator seed flags.
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Dfabric.") || arg.startsWith("-Dlog4j")) command.add(arg);
        }
        command.add("-Xmx3G");
        command.add("-Dzsgrooms.filterProbe=true");
        command.add("-Dzsgrooms.filterVerifierWorker=true");
        command.add("-cp");
        String[] entries = System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));
        List<String> absolute = new ArrayList<String>();
        for (String entry : entries) absolute.add(new File(entry).getAbsolutePath());
        command.add(String.join(File.pathSeparator, absolute));
        command.add("net.fabricmc.devlaunchinjector.Main");
        command.add("--port=0");
        command.add("nogui");
        return command;
    }

    CompletableFuture<JsonObject> submit(JsonObject request) throws Exception {
        Job job = new Job(request);
        while (!queue.offer(job, 100, TimeUnit.MILLISECONDS)) checkHealthy();
        checkHealthy();
        return job.result;
    }

    private void checkHealthy() {
        if (closed || failure != null) throw new IllegalStateException("Verifier pool unavailable", failure);
    }

    private void dispatch(Process process, Path dir) {
        Job active = null;
        try {
            while (!closed) {
                active = queue.poll(100, TimeUnit.MILLISECONDS);
                if (active == null) {
                    if (!process.isAlive()) throw new IllegalStateException("Verifier exited while idle");
                    continue;
                }
                long dispatched = System.nanoTime();
                FilterProbeFiles.write(dir.resolve("request.json"), active.request);
                Path response = dir.resolve("response.json");
                awaitFile(process, response, dispatched + TimeUnit.MINUTES.toNanos(10));
                JsonObject result = FilterVerifierWorker.read(response);
                Files.delete(response);
                if (!active.request.get("id").equals(result.get("id")) || result.get("protocol").getAsInt() != 1
                        || !OfflineFilterValidator.PROFILE.equals(result.get("profile").getAsString())
                        || !("REJECTED".equals(result.get("status").getAsString()) || "VALIDATION_PASS".equals(result.get("status").getAsString())
                        || "ERROR".equals(result.get("status").getAsString()))) {
                    throw new IllegalStateException("Verifier failed or returned an invalid response");
                }
                result.addProperty("queueWaitMs", (dispatched - active.queued) / 1000000.0);
                active.result.complete(result);
                active = null;
            }
        } catch (Throwable error) {
            if (active != null) active.result.completeExceptionally(error);
            failure = error;
            Job waiting;
            while ((waiting = queue.poll()) != null) waiting.result.completeExceptionally(error);
        }
    }

    private void awaitFile(Process process, Path path, long deadline) throws Exception {
        while (!Files.exists(path)) {
            if (closed || !process.isAlive() || System.nanoTime() >= deadline) throw new IllegalStateException("Verifier stopped or timed out");
            Thread.sleep(25);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (Path dir : directories) {
            try { Files.write(dir.resolve("stop"), new byte[0]); } catch (Exception ignored) { }
        }
        for (Thread thread : dispatchers) thread.interrupt();
        for (Process process : processes) {
            try { if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(); } }
            catch (InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
        }
        for (Thread thread : dispatchers) {
            try { thread.join(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        Job waiting;
        while ((waiting = queue.poll()) != null) waiting.result.completeExceptionally(new IllegalStateException("Verifier closed"));
        if (Thread.currentThread() != shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException shuttingDown) { }
        }
    }

    private static final class Job {
        final JsonObject request;
        final long queued = System.nanoTime();
        final CompletableFuture<JsonObject> result = new CompletableFuture<JsonObject>();
        Job(JsonObject request) { this.request = request; }
    }
}
