package zsgrooms.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Separate process prevents ZSG's SeedFinding versions from changing village-model results. */
final class NetherModelClient implements AutoCloseable {
    private final Process process;
    private final BufferedReader input;
    private final PrintWriter output;

    NetherModelClient() throws Exception {
        String classpath = System.getProperty("zsg.netherModelClasspath");
        if (classpath == null || classpath.isEmpty()) throw new IllegalArgumentException("Missing Nether model classpath");
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        process = new ProcessBuilder(java, "-Xmx1G", "-cp", classpath, "zsgrooms.model.nether.NetherModel")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        output = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    Result evaluate(String request) throws Exception {
        if (!request.matches("NETHER [0-9]+ -?[0-9]+ -?[0-9]+ -?[0-9]+ -?[0-9]+")) {
            throw new IllegalArgumentException("Bad Nether request");
        }
        output.println(request);
        if (output.checkError()) throw new IllegalStateException("Nether model pipe closed");
        String line = input.readLine();
        if (line == null || !line.matches("NETHER [0-3] (HOUSING|STABLES|TREASURE|BRIDGE) [0-9]+ [0-9]+ [0-9]+ [0-9]+")) {
            throw new IllegalStateException("Nether model protocol failed");
        }
        String[] fields = line.split(" ");
        return new Result(Integer.parseInt(fields[1]), fields[2], Integer.parseInt(fields[3]),
                Long.parseLong(fields[4]), Long.parseLong(fields[5]), Long.parseLong(fields[6]));
    }

    @Override
    public void close() throws Exception {
        // Kill before closing a reader which may be blocked inside a timed-out model request.
        process.destroyForcibly();
        process.waitFor();
        input.close();
        output.close();
    }

    static final class Result {
        final int status, score;
        final String type;
        final long layoutNanos, lootNanos, terrainNanos;

        Result(int status, String type, int score, long layoutNanos, long lootNanos, long terrainNanos) {
            if (score > 127 || (status >= 2 && score < 20)) throw new IllegalArgumentException("Invalid obsidian score");
            this.status = status;
            this.type = type;
            this.score = score;
            this.layoutNanos = layoutNanos;
            this.lootNanos = lootNanos;
            this.terrainNanos = terrainNanos;
        }
    }
}
