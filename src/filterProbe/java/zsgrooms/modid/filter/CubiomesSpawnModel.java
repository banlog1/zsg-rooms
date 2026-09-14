package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One private, persistent model process. No seed values in arguments or diagnostics. */
final class CubiomesSpawnModel implements AutoCloseable {
    static final String REVISION = "e61f90580cbdd883214a8054670dacae655e59c0";
    private final Process process;
    private final BufferedReader output;
    private final BufferedWriter input;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ZSG spawn model reader");
        thread.setDaemon(true);
        return thread;
    });
    private boolean closed;

    CubiomesSpawnModel(Path executable) throws IOException {
        process = new ProcessBuilder(executable.toAbsolutePath().toString()).redirectErrorStream(true).start();
        output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII));
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII));
        try {
            if (!("ZSG_SPAWN_1 " + REVISION).equals(line())) throw new IOException("Spawn model protocol/revision mismatch");
        } catch (IOException failure) { close(); throw failure; }
    }

    synchronized BlockPos predict(long seed) throws IOException {
        if (closed) throw new IOException("Spawn model is closed");
        try {
            input.write(Long.toString(seed));
            input.newLine();
            input.flush();
            String[] values = line().split(" ");
            if (values.length != 2) throw new IOException("Invalid spawn model response");
            int x = Integer.parseInt(values[0]), z = Integer.parseInt(values[1]);
            if (Math.abs((long)x) > 30000000 || Math.abs((long)z) > 30000000) throw new IOException("Invalid spawn model position");
            return new BlockPos(x, 64, z);
        } catch (IOException | NumberFormatException failure) {
            close();
            throw new IOException("Spawn model request failed");
        }
    }

    private String line() throws IOException {
        Future<String> result = reader.submit(output::readLine);
        try {
            String line = result.get(30, TimeUnit.SECONDS);
            if (line == null || line.length() > 128) throw new IOException("Spawn model closed or invalid response");
            return line;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Spawn model interrupted");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IOException("Spawn model unavailable or timed out");
        } finally { result.cancel(true); }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        process.destroyForcibly();
        try { process.waitFor(5, TimeUnit.SECONDS); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        try { input.close(); } catch (IOException ignored) { }
        try { output.close(); } catch (IOException ignored) { }
        reader.shutdownNow();
    }
}
