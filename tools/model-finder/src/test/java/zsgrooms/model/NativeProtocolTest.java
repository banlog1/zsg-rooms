package zsgrooms.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NativeProtocolTest {
    @TempDir Path directory;

    @Test
    void invalidFamilyCapsAndIncompleteTuningConfigurationFailBeforeOpeningBank() throws Exception {
        for (String cap : new String[]{"0", "5", "4294967297", "-1", "2"}) {
            Path output = directory.resolve("cap-" + cap + ".jsonl");
            ProcessBuilder builder = command("temple", output);
            builder.command().add(cap);
            builder.environment().put("ZSG_MODEL_PIPE", "1");
            builder.environment().put("ZSG_MODEL_TUNE", "1");
            Process process = builder.start();
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                assertEquals(2, process.exitValue());
                assertFalse(Files.exists(output));
                assertEquals(0, process.getInputStream().readAllBytes().length);
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    @Test
    void allProfilesRefuseToSearchWithoutModels() throws Exception {
        for (String type : new String[]{"temple", "shipwreck", "village"}) {
            Path output = directory.resolve(type + ".jsonl");
            ProcessBuilder builder = command(type, output);
            builder.environment().remove("ZSG_MODEL_PIPE");
            Process process = builder.start();
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                assertEquals(2, process.exitValue());
                assertFalse(Files.exists(output));
                assertEquals(0, process.getInputStream().readAllBytes().length);
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    @Test
    void invalidOrMissingNetherResponseAbortsBeforeAnyAcceptance() throws Exception {
        for (String type : new String[]{"temple", "shipwreck", "village"}) {
            for (String reply : new String[]{"", "NETHER 3 19 BRIDGE\n", "NETHER 3 20 UNKNOWN\n", "NETHER 4 20 BRIDGE\n"}) {
                Path output = directory.resolve(type + "-" + Math.abs(reply.hashCode()) + ".jsonl");
                ProcessBuilder builder = command(type, output);
                builder.environment().put("ZSG_MODEL_PIPE", "1");
                Process process = builder.start();
                try {
                    process.getOutputStream().write(reply.getBytes(StandardCharsets.UTF_8));
                    process.getOutputStream().close();
                    assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                    assertEquals(3, process.exitValue(), type);
                    assertEquals(0, Files.size(output));
                    String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    assertEquals("Nether model protocol failed; search aborted", error.trim());
                } finally {
                    process.destroyForcibly();
                    process.waitFor();
                }
            }
        }
    }

    private ProcessBuilder command(String type, Path output) {
        return new ProcessBuilder(Path.of("run/filter-worker/seed-finder.exe").toAbsolutePath().toString(),
                "search", type, "100000000", "256", "1", "10", "0", output.toString());
    }
}
