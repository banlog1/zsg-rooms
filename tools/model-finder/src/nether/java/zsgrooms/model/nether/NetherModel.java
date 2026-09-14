package zsgrooms.model.nether;

import Xinyuiii.properties.BastionGenerator;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import zsgrooms.modid.filter.BastionLayoutChecks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Persistent, private process using ZSG's pinned dependencies, separate from village dependencies. */
public final class NetherModel {
    private final BastionGenerator generator = new BastionGenerator(MCVersion.v1_16_1);

    public static void main(String[] args) throws Exception {
        PrintStream pipe = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            NetherModel model = new NetherModel();
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split(" ");
                if (fields.length != 6 || !fields[0].equals("NETHER")) throw new IllegalArgumentException("Bad protocol");
                Result result = model.evaluate(Long.parseLong(fields[1]), Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]));
                pipe.println("NETHER " + result.status + " " + result.type + " " + result.score + " "
                        + result.layoutNanos + " " + result.lootNanos + " " + result.terrainNanos);
                if (pipe.checkError()) throw new IllegalStateException("Private pipe closed");
            }
        }
    }

    Result evaluate(long seed, int bx, int bz, int fx, int fz) {
        if (seed < 0 || seed > 0xffffffffffffL || Math.abs((long) bx) > 6 || Math.abs((long) bz) > 6
                || Math.abs((long) fx) > 16 || Math.abs((long) fz) > 16) throw new IllegalArgumentException("Outside regular ZSG bounds");
        Result result = new Result();
        long start = System.nanoTime();
        CPos bastion = new CPos(bx, bz);
        if (!generator.generate(seed, bastion)) throw new IllegalStateException("Bastion model failed");
        result.type = generator.getType().name();
        boolean layout = BastionLayoutChecks.accepts(generator.getPieces().stream()
                .map(piece -> "minecraft:bastion/" + piece.getName()).collect(Collectors.toList()));
        result.layoutNanos = System.nanoTime() - start;
        if (!layout) return result;
        start = System.nanoTime();
        result.score = ZsgNetherChecks.obsidianScore(generator, bastion);
        result.lootNanos = System.nanoTime() - start;
        result.status = 1;
        if (result.score < 20) return result;
        start = System.nanoTime();
        boolean terrain = ZsgNetherChecks.terrain(seed, bx, bz, fx, fz);
        result.terrainNanos = System.nanoTime() - start;
        result.status = terrain ? 3 : 2;
        return result;
    }

    static final class Result {
        int status, score;
        String type;
        long layoutNanos, lootNanos, terrainNanos;
    }
}
