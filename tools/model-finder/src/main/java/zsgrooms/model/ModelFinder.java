package zsgrooms.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Standalone operator process: no Minecraft, Fabric, save folders, or server bootstrap. */
public final class ModelFinder {
    private ModelFinder() { }

    public static void main(String[] args) {
        PrintStream publicOutput = System.out;
        PrintStream publicError = System.err;
        // A library diagnostic must not disclose a private candidate or corrupt the pipe.
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            search(args, publicOutput);
        } catch (Exception error) {
            publicError.println("Model search failed (" + error.getClass().getSimpleName()
                    + "). No Minecraft fallback was used; the private bank may be partial.");
            System.exit(1);
        }
    }

    private static void search(String[] args, PrintStream publicOutput) throws Exception {
        if (args.length != 9 && args.length != 10) throw new IllegalArgumentException("Expected native executable and search arguments");
        ProcessBuilder builder = new ProcessBuilder(Arrays.asList(args));
        builder.environment().put("ZSG_MODEL_PIPE", "1");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process child = builder.start();
        ExecutorService model = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "standalone-seed-model");
            thread.setDaemon(true);
            return thread;
        });
        long checks = 0;
        long nanos = 0;
        long layoutNanos = 0, lootNanos = 0, missingGolem = 0, acceptedWithoutGolem = 0;
        long smithChests = 0, modeledChests = 0;
        long[] outcomes = new long[SmithLootModel.Outcome.values().length];
        long[] netherOutcomes = new long[4];
        long netherLayoutNanos = 0, netherLootNanos = 0, netherTerrainNanos = 0, acceptedStables = 0;
        long[] portalOutcomes = new long[5];
        long portalNanos = 0;
        String report = null;
        try (NetherModelClient nether = new NetherModelClient();
             BufferedReader input = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter response = new PrintWriter(new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("NETHER ")) {
                    if (report != null) throw new IllegalStateException("Request after report");
                    String request = line;
                    NetherModelClient.Result result = model.submit(() -> nether.evaluate(request)).get(30, TimeUnit.SECONDS);
                    netherOutcomes[result.status]++;
                    netherLayoutNanos += result.layoutNanos;
                    netherLootNanos += result.lootNanos;
                    netherTerrainNanos += result.terrainNanos;
                    if (result.status == 3 && result.type.equals("STABLES")) acceptedStables++;
                    response.println("NETHER " + result.status + " " + result.score + " " + result.type);
                    if (response.checkError()) throw new IllegalStateException("Private pipe closed");
                } else if (line.startsWith("PORTAL ")) {
                    String[] fields = line.split(" ");
                    if (fields.length != 9 || report != null) throw new IllegalStateException("Bad private protocol");
                    long seed = Long.parseLong(fields[1]);
                    int[] values = new int[7];
                    for (int i = 0; i < values.length; i++) values[i] = Integer.parseInt(fields[i + 2]);
                    long start = System.nanoTime();
                    PortalCompletionModel.Result result = model.submit(() -> PortalCompletionModel.evaluate(seed,
                            values[0], values[1], values[2], values[3], values[4], values[5], values[6])).get(30, TimeUnit.SECONDS);
                    portalNanos += System.nanoTime() - start;
                    portalOutcomes[result.status()]++;
                    response.println("PORTAL_RESULT " + result.status() + " " + result.missing() + " " + result.lava()
                            + " " + result.cast() + " " + result.template() + " " + result.y());
                    if (response.checkError()) throw new IllegalStateException("Private pipe closed");
                } else if (line.startsWith("SMITH ")) {
                    String[] fields = line.split(" ");
                    if (fields.length != 4 || report != null) throw new IllegalStateException("Bad private protocol");
                    long seed = Long.parseLong(fields[1]);
                    int x = Integer.parseInt(fields[2]), z = Integer.parseInt(fields[3]);
                    long start = System.nanoTime();
                    SmithLootModel.Result result = model.submit(() -> SmithLootModel.evaluate(seed, x, z)).get(30, TimeUnit.SECONDS);
                    nanos += System.nanoTime() - start;
                    checks++;
                    layoutNanos += result.layoutNanos;
                    lootNanos += result.lootNanos;
                    smithChests += result.smithChests;
                    modeledChests += result.modeledChests;
                    outcomes[result.outcome.ordinal()]++;
                    if (!result.modeledGolem && result.outcome != SmithLootModel.Outcome.LAYOUT_REJECTED) {
                        missingGolem++;
                        if (result.outcome == SmithLootModel.Outcome.ACCEPTED) acceptedWithoutGolem++;
                    }
                    response.println("SMITH_LOOT " + result.iron + " " + result.ironPickaxes + " " + result.diamonds);
                    if (response.checkError()) throw new IllegalStateException("Private pipe closed");
                } else if (line.startsWith("{\"profile\":\"zsg-model-only-v5\",") && report == null && !line.contains("\"seed\"")) {
                    report = line;
                } else {
                    throw new IllegalStateException("Unexpected native output");
                }
            }
            if (child.waitFor() != 0 || report == null) throw new IllegalStateException("Native search failed");
            publicOutput.println(report);
            publicOutput.printf(java.util.Locale.ROOT,
                    "{\"netherModel\":{\"layout\":{\"reached\":%d,\"rejected\":%d,\"ms\":%.3f},"
                            + "\"obsidian\":{\"reached\":%d,\"rejected\":%d,\"ms\":%.3f},"
                            + "\"terrain\":{\"reached\":%d,\"rejected\":%d,\"ms\":%.3f},"
                            + "\"acceptedFamilies\":%d,\"acceptedStablesFamilies\":%d},\"minecraftWorlds\":0}%n",
                    Arrays.stream(netherOutcomes).sum(), netherOutcomes[0], netherLayoutNanos / 1e6,
                    netherOutcomes[1] + netherOutcomes[2] + netherOutcomes[3], netherOutcomes[1], netherLootNanos / 1e6,
                    netherOutcomes[2] + netherOutcomes[3], netherOutcomes[2], netherTerrainNanos / 1e6,
                    netherOutcomes[3], acceptedStables);
            publicOutput.printf(java.util.Locale.ROOT,
                    "{\"villageModelChecks\":%d,\"villageModelMs\":%.3f,\"layoutMs\":%.3f,\"lootMs\":%.3f,"
                            + "\"smithChests\":%d,\"modeledSmithChests\":%d,\"missingModeledGolem\":%d,"
                            + "\"acceptedWithoutModeledGolem\":%d,\"minecraftWorlds\":0,\"outcomes\":{",
                    checks, nanos / 1e6, layoutNanos / 1e6, lootNanos / 1e6,
                    smithChests, modeledChests, missingGolem, acceptedWithoutGolem);
            for (SmithLootModel.Outcome outcome : SmithLootModel.Outcome.values()) {
                publicOutput.printf("%s\"%s\":%d", outcome.ordinal() == 0 ? "" : ",", outcome.name(), outcomes[outcome.ordinal()]);
            }
            publicOutput.println("}}");
            publicOutput.printf(java.util.Locale.ROOT,
                    "{\"portalModel\":{\"checks\":%d,\"ms\":%.3f,\"layoutRejected\":%d,\"frameRejected\":%d,"
                            + "\"resourcesRejected\":%d,\"needsWaterCheck\":%d,\"obsidianOnly\":%d},\"minecraftWorlds\":0}%n",
                    Arrays.stream(portalOutcomes).sum(), portalNanos / 1e6,
                    portalOutcomes[0], portalOutcomes[1], portalOutcomes[2], portalOutcomes[3], portalOutcomes[4]);
        } finally {
            model.shutdownNow();
            child.destroyForcibly();
            child.waitFor();
        }
    }
}
