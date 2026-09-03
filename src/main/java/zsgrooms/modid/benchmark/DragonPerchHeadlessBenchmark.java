package zsgrooms.modid.benchmark;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.dragon.phase.Phase;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.mixin.EnderDragonFightAccessor;
import zsgrooms.modid.mixin.EntityRandomAccessor;
import zsgrooms.modid.mixin.ServerWorldPlayerListAccessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Development-only, property-gated benchmark for real server-side dragon AI ticks. */
public final class DragonPerchHeadlessBenchmark {
    private static final String ENABLED_PROPERTY = "zsgrooms.perchBenchmark";
    private static final long WORK_BUDGET_NANOS = 20_000_000L;
    private static final int CHUNK_RADIUS = 8;

    private static BenchmarkSession session;

    private DragonPerchHeadlessBenchmark() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    public static void register() {
        if (!isEnabled()) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(DragonPerchHeadlessBenchmark::start);
        ServerTickEvents.END_SERVER_TICK.register(DragonPerchHeadlessBenchmark::tick);
    }

    public static void onNaturalDragonCreated(EnderDragonEntity dragon) {
        BenchmarkSession activeSession = session;
        if (isEnabled() && activeSession != null) {
            activeSession.captureNaturalDragon(dragon);
        }
    }

    public static boolean shouldSuppressWorldTick(Entity entity) {
        BenchmarkSession activeSession = session;
        return isEnabled()
                && activeSession != null
                && activeSession.shouldSuppressWorldTick(entity);
    }

    private static void start(MinecraftServer server) {
        try {
            session = new BenchmarkSession(server, Configuration.read());
            session.start();
        } catch (Throwable error) {
            ZsgRooms.LOGGER.error("[PerchBenchmark] Could not start", error);
            server.stop(false);
        }
    }

    private static void tick(MinecraftServer server) {
        BenchmarkSession activeSession = session;
        if (activeSession == null || activeSession.finished) {
            return;
        }
        try {
            activeSession.runWorkBudget();
        } catch (Throwable error) {
            ZsgRooms.LOGGER.error("[PerchBenchmark] Failed", error);
            activeSession.cleanup();
            activeSession.finished = true;
            server.stop(false);
        }
    }

    private enum Mode {
        VANILLA(false, 1300, "Vanilla (standardisation off)"),
        CURRENT(true, 1300, "Standardised with 1,300-tick grace"),
        IMMEDIATE(true, 0, "Standardised without grace");

        private final boolean standardized;
        private final int graceTicks;
        private final String label;

        Mode(boolean standardized, int graceTicks, String label) {
            this.standardized = standardized;
            this.graceTicks = graceTicks;
            this.label = label;
        }
    }

    private static final class BenchmarkSession {
        private final MinecraftServer server;
        private final ServerWorld world;
        private final EnderDragonFight fight;
        private final Configuration configuration;
        private final long[] trialSeeds;
        private final Mode[] modes;
        private final List<ServerPlayerEntity> worldPlayers;
        private final List<ModeResult> results = new ArrayList<ModeResult>();

        private BenchmarkPlayerEntity player;
        private EnderDragonEntity dragon;
        private Mode mode;
        private int modeIndex;
        private int trialIndex;
        private int trialStartServerTick;
        private int naturalSpawnDelayTicks = -1;
        private int decisionTick = -1;
        private int[] spawnTicks;
        private int[] decisionTicks;
        private int[] perchTicks;
        private int timeouts;
        private boolean waitingForNaturalDragon;
        private boolean manuallyManagingDragon;
        private boolean finished;

        private BenchmarkSession(MinecraftServer server, Configuration configuration) {
            this.server = server;
            this.configuration = configuration;
            this.world = server.getWorld(World.END);
            if (this.world == null) {
                throw new IllegalStateException("The End world is unavailable");
            }
            this.fight = this.world.getEnderDragonFight();
            if (this.fight == null) {
                throw new IllegalStateException("The End dragon fight is unavailable");
            }
            this.worldPlayers = ((ServerWorldPlayerListAccessor) this.world)
                    .zsgRooms$getBenchmarkPlayers();
            this.trialSeeds = createTrialSeeds(configuration.trials, configuration.sampleSeed);
            this.modes = configuration.compareGrace
                    ? new Mode[]{Mode.CURRENT, Mode.IMMEDIATE}
                    : new Mode[]{Mode.VANILLA, Mode.CURRENT};
            this.mode = this.modes[0];
        }

        private void start() {
            log("Starting paired full-period benchmark: trials={}, crystals={}, maxTicks={}, sampleSeed={}",
                    configuration.trials,
                    configuration.crystals,
                    configuration.maxTicks,
                    configuration.sampleSeed);
            log("Comparison: {} versus {}", this.modes[0].label, this.modes[1].label);
            log("Each trial begins at synthetic End entry and uses vanilla EnderDragonFight dragon creation.");
            prepareEndArena();
            createBenchmarkPlayer();
            startMode(this.modes[0]);
        }

        private void prepareEndArena() {
            for (EnderDragonEntity existingDragon : this.world.getAliveEnderDragons()) {
                existingDragon.remove();
            }
            for (int chunkX = -CHUNK_RADIUS; chunkX <= CHUNK_RADIUS; chunkX++) {
                for (int chunkZ = -CHUNK_RADIUS; chunkZ <= CHUNK_RADIUS; chunkZ++) {
                    this.world.getChunk(chunkX, chunkZ);
                    this.world.setChunkForced(chunkX, chunkZ, true);
                }
            }
            setCrystalCount();
        }

        private void createBenchmarkPlayer() {
            BlockPos surface = this.world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ORIGIN);
            ServerPlayerInteractionManager interactionManager =
                    new ServerPlayerInteractionManager(this.world);
            this.player = new BenchmarkPlayerEntity(
                    this.server,
                    this.world,
                    new GameProfile(
                            UUID.nameUUIDFromBytes(
                                    "zsg-perch-benchmark".getBytes(StandardCharsets.UTF_8)),
                            "PerchBenchmark"),
                    interactionManager);
            this.player.refreshPositionAndAngles(
                    surface.getX() + 0.5D,
                    surface.getY() + 1.0D,
                    surface.getZ() + 0.5D,
                    0.0F,
                    0.0F);
            this.player.networkHandler = new DiscardingServerPlayNetworkHandler(
                    this.server, this.player);
            this.worldPlayers.add(this.player);
        }

        private void startMode(Mode nextMode) {
            this.mode = nextMode;
            this.trialIndex = 0;
            this.timeouts = 0;
            this.spawnTicks = new int[this.configuration.trials];
            this.decisionTicks = new int[this.configuration.trials];
            this.perchTicks = new int[this.configuration.trials];
            Arrays.fill(this.spawnTicks, -1);
            Arrays.fill(this.decisionTicks, -1);
            Arrays.fill(this.perchTicks, -1);
            RngStandardization.configure(nextMode.standardized, false);
            RngStandardization.setDragonPerchGraceTicksForTesting(nextMode.graceTicks);
            log("Running {}", nextMode.label);
            startTrial();
        }

        private void startTrial() {
            setCrystalCount();
            this.decisionTick = -1;
            this.naturalSpawnDelayTicks = -1;
            this.manuallyManagingDragon = false;
            this.waitingForNaturalDragon = true;
            this.dragon = null;
            for (EnderDragonEntity existingDragon : this.world.getAliveEnderDragons()) {
                existingDragon.remove();
            }

            long trialSeed = this.trialSeeds[this.trialIndex];
            this.world.random.setSeed(trialSeed ^ 0x594157L);
            this.trialStartServerTick = this.server.getTicks();

            EnderDragonFightAccessor accessor = (EnderDragonFightAccessor) this.fight;
            accessor.zsgRooms$getBossBar().removePlayer(this.player);
            accessor.zsgRooms$setPlayerUpdateTimer(0);
            accessor.zsgRooms$setDoLegacyCheck(false);
            accessor.zsgRooms$setDragonKilled(false);
            accessor.zsgRooms$setDragonUuid(null);
            accessor.zsgRooms$setDragonSeenTimer(0);
        }

        private void captureNaturalDragon(EnderDragonEntity createdDragon) {
            if (!this.waitingForNaturalDragon || createdDragon == null || this.finished) {
                return;
            }
            this.dragon = createdDragon;
            this.naturalSpawnDelayTicks = Math.max(
                    0, this.server.getTicks() - this.trialStartServerTick);
            ((EntityRandomAccessor) this.dragon).zsgRooms$getRandom()
                    .setSeed(this.trialSeeds[this.trialIndex]);
            this.waitingForNaturalDragon = false;
            this.manuallyManagingDragon = true;
            log("{} | trial {}/{} | vanilla dragon spawned {} ticks after End entry",
                    this.mode.label,
                    this.trialIndex + 1,
                    this.configuration.trials,
                    this.naturalSpawnDelayTicks);
        }

        private boolean shouldSuppressWorldTick(Entity entity) {
            return this.manuallyManagingDragon && this.dragon == entity;
        }

        private void runWorkBudget() {
            if (this.waitingForNaturalDragon || this.dragon == null) {
                if (this.server.getTicks() - this.trialStartServerTick
                        >= this.configuration.maxTicks) {
                    throw new IllegalStateException(
                            "Vanilla EnderDragonFight did not create a dragon before timeout");
                }
                return;
            }
            long deadline = System.nanoTime() + WORK_BUDGET_NANOS;
            do {
                runDragonTick();
            } while (!this.finished
                    && !this.waitingForNaturalDragon
                    && System.nanoTime() < deadline);
        }

        private void runDragonTick() {
            setCrystalCount();
            // ServerWorld.tickEntity increments age immediately before Entity.tick().
            this.dragon.age++;
            this.dragon.tick();
            if (this.dragon.age % 100 == 0) {
                removeDragonFireballs();
            }
            Phase phase = this.dragon.getPhaseManager().getCurrent();
            PhaseType<?> phaseType = phase.getType();
            int elapsedTicks = elapsedTicksFromEndEntry();

            if (this.dragon.age % 500 == 0) {
                log("{} | trial {}/{} | elapsed={} | dragonAge={} | phase={}",
                        this.mode.label,
                        this.trialIndex + 1,
                        this.configuration.trials,
                        elapsedTicks,
                        this.dragon.age,
                        phaseType);
            }

            if (this.decisionTick < 0 && phaseType == PhaseType.LANDING_APPROACH) {
                this.decisionTick = elapsedTicks;
            }
            if (isPerched(phaseType)) {
                finishTrial(elapsedTicks, false);
            } else if (elapsedTicks >= this.configuration.maxTicks) {
                finishTrial(this.configuration.maxTicks, true);
            }
        }

        private int elapsedTicksFromEndEntry() {
            return this.naturalSpawnDelayTicks + this.dragon.age;
        }

        private void finishTrial(int perchTick, boolean timedOut) {
            this.spawnTicks[this.trialIndex] = this.naturalSpawnDelayTicks;
            this.decisionTicks[this.trialIndex] = this.decisionTick;
            this.perchTicks[this.trialIndex] = timedOut ? -1 : perchTick;
            if (timedOut) {
                this.timeouts++;
            }
            this.manuallyManagingDragon = false;
            this.dragon.remove();
            removeDragonFireballs();
            this.trialIndex++;

            if (this.trialIndex < this.configuration.trials) {
                startTrial();
                return;
            }

            ModeResult result = new ModeResult(
                    this.mode,
                    summarize(this.spawnTicks),
                    summarize(this.decisionTicks),
                    summarize(this.perchTicks),
                    this.perchTicks.clone(),
                    this.timeouts);
            this.results.add(result);
            printResult(result);

            if (this.modeIndex + 1 < this.modes.length) {
                this.modeIndex++;
                startMode(this.modes[this.modeIndex]);
            } else {
                finishBenchmark();
            }
        }

        private void finishBenchmark() {
            ModeResult baseline = this.results.get(0);
            ModeResult comparison = this.results.get(1);
            Summary pairedDelta = summarizeDifferences(
                    baseline.perchTicks, comparison.perchTicks);
            log("Comparison complete over {}/{} pairs that perched in both modes. "
                            + "Mean End-entry-to-perch delta: {} ticks ({} seconds); "
                            + "median delta: {} ticks ({} seconds)",
                    pairedDelta.completed,
                    this.configuration.trials,
                    format(pairedDelta.mean),
                    format(pairedDelta.mean / 20.0D),
                    pairedDelta.median,
                    pairedDelta.completed == 0
                            ? "n/a"
                            : format(pairedDelta.median / 20.0D));
            log("Interpretation: negative means '{}' was faster; positive means it was slower.",
                    comparison.mode.label);
            cleanup();
            this.finished = true;
            this.server.stop(false);
        }

        private void printResult(ModeResult result) {
            log("{} | natural spawn delay: completed={}/{}, mean={} ticks ({}s), median={} ticks, p90={}, p95={}, p99={}",
                    result.mode.label,
                    result.spawn.completed,
                    this.configuration.trials,
                    format(result.spawn.mean),
                    format(result.spawn.mean / 20.0D),
                    result.spawn.median,
                    result.spawn.p90,
                    result.spawn.p95,
                    result.spawn.p99);
            log("{} | End entry to landing decision: completed={}/{}, mean={} ticks ({}s), median={} ticks, p90={}, p95={}, p99={}",
                    result.mode.label,
                    result.decision.completed,
                    this.configuration.trials,
                    format(result.decision.mean),
                    format(result.decision.mean / 20.0D),
                    result.decision.median,
                    result.decision.p90,
                    result.decision.p95,
                    result.decision.p99);
            log("{} | End entry to actual perch: completed={}/{}, mean={} ticks ({}s), median={} ticks ({}s), p90={}, p95={}, p99={}, timeouts={}",
                    result.mode.label,
                    result.perch.completed,
                    this.configuration.trials,
                    format(result.perch.mean),
                    format(result.perch.mean / 20.0D),
                    result.perch.median,
                    formatTicksAsSeconds(result.perch.median),
                    result.perch.p90,
                    result.perch.p95,
                    result.perch.p99,
                    result.timeouts);
        }

        private void cleanup() {
            RngStandardization.configure(false, false);
            this.manuallyManagingDragon = false;
            this.waitingForNaturalDragon = false;
            if (this.dragon != null) {
                this.dragon.remove();
            }
            if (this.player != null) {
                this.worldPlayers.remove(this.player);
            }
            for (int chunkX = -CHUNK_RADIUS; chunkX <= CHUNK_RADIUS; chunkX++) {
                for (int chunkZ = -CHUNK_RADIUS; chunkZ <= CHUNK_RADIUS; chunkZ++) {
                    this.world.setChunkForced(chunkX, chunkZ, false);
                }
            }
            removeDragonFireballs();
        }

        private void setCrystalCount() {
            ((EnderDragonFightAccessor) this.fight)
                    .zsgRooms$setEndCrystalsAlive(this.configuration.crystals);
        }

        private void removeDragonFireballs() {
            List<Entity> fireballs = this.world.getEntities(
                    EntityType.DRAGON_FIREBALL, entity -> true);
            for (Entity fireball : fireballs) {
                fireball.remove();
            }
        }
    }

    private static boolean isPerched(PhaseType<?> phaseType) {
        return phaseType == PhaseType.SITTING_SCANNING
                || phaseType == PhaseType.SITTING_ATTACKING
                || phaseType == PhaseType.SITTING_FLAMING;
    }

    private static Summary summarize(int[] values) {
        int[] completed = Arrays.stream(values).filter(value -> value >= 0).toArray();
        if (completed.length == 0) {
            return new Summary(0, Double.NaN, -1, -1, -1, -1);
        }
        Arrays.sort(completed);
        long total = 0L;
        for (int value : completed) {
            total += value;
        }
        return new Summary(
                completed.length,
                total / (double) completed.length,
                percentile(completed, 0.50D),
                percentile(completed, 0.90D),
                percentile(completed, 0.95D),
                percentile(completed, 0.99D));
    }

    private static Summary summarizeDifferences(int[] baseline, int[] comparison) {
        int completedCount = 0;
        for (int index = 0; index < baseline.length; index++) {
            if (baseline[index] >= 0 && comparison[index] >= 0) {
                completedCount++;
            }
        }
        if (completedCount == 0) {
            return new Summary(0, Double.NaN, -1, -1, -1, -1);
        }

        int[] differences = new int[completedCount];
        int differenceIndex = 0;
        long total = 0L;
        for (int index = 0; index < baseline.length; index++) {
            if (baseline[index] >= 0 && comparison[index] >= 0) {
                int difference = comparison[index] - baseline[index];
                differences[differenceIndex++] = difference;
                total += difference;
            }
        }
        Arrays.sort(differences);
        return new Summary(
                completedCount,
                total / (double) completedCount,
                percentile(differences, 0.50D),
                percentile(differences, 0.90D),
                percentile(differences, 0.95D),
                percentile(differences, 0.99D));
    }

    private static int percentile(int[] sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.length) - 1;
        return sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
    }

    private static long[] createTrialSeeds(int trials, long sampleSeed) {
        long[] seeds = new long[trials];
        Random random = new Random(sampleSeed);
        for (int index = 0; index < trials; index++) {
            seeds[index] = random.nextLong();
        }
        return seeds;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatTicksAsSeconds(int ticks) {
        return ticks < 0 ? "n/a" : format(ticks / 20.0D);
    }

    private static void log(String message, Object... arguments) {
        ZsgRooms.LOGGER.info("[PerchBenchmark] " + message, arguments);
    }

    private static final class Summary {
        private final int completed;
        private final double mean;
        private final int median;
        private final int p90;
        private final int p95;
        private final int p99;

        private Summary(int completed, double mean, int median, int p90, int p95, int p99) {
            this.completed = completed;
            this.mean = mean;
            this.median = median;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
        }
    }

    private static final class ModeResult {
        private final Mode mode;
        private final Summary spawn;
        private final Summary decision;
        private final Summary perch;
        private final int[] perchTicks;
        private final int timeouts;

        private ModeResult(
                Mode mode,
                Summary spawn,
                Summary decision,
                Summary perch,
                int[] perchTicks,
                int timeouts
        ) {
            this.mode = mode;
            this.spawn = spawn;
            this.decision = decision;
            this.perch = perch;
            this.perchTicks = perchTicks;
            this.timeouts = timeouts;
        }
    }

    private static final class Configuration {
        private final int trials;
        private final int crystals;
        private final int maxTicks;
        private final long sampleSeed;
        private final boolean compareGrace;

        private Configuration(
                int trials,
                int crystals,
                int maxTicks,
                long sampleSeed,
                boolean compareGrace
        ) {
            this.trials = trials;
            this.crystals = crystals;
            this.maxTicks = maxTicks;
            this.sampleSeed = sampleSeed;
            this.compareGrace = compareGrace;
        }

        private static Configuration read() {
            int trials = positiveInt("zsgrooms.perchBenchmark.trials", 10);
            int crystals = nonNegativeInt("zsgrooms.perchBenchmark.crystals", 10);
            int maxTicks = positiveInt("zsgrooms.perchBenchmark.maxTicks", 12000);
            long sampleSeed = Long.parseLong(System.getProperty(
                    "zsgrooms.perchBenchmark.sampleSeed", "6505258691473851208"));
            boolean compareGrace = Boolean.parseBoolean(System.getProperty(
                    "zsgrooms.perchBenchmark.compareGrace", "false"));
            return new Configuration(
                    trials, crystals, maxTicks, sampleSeed, compareGrace);
        }

        private static int positiveInt(String property, int fallback) {
            int value = Integer.parseInt(System.getProperty(property, Integer.toString(fallback)));
            if (value <= 0) {
                throw new IllegalArgumentException(property + " must be positive");
            }
            return value;
        }

        private static int nonNegativeInt(String property, int fallback) {
            int value = Integer.parseInt(System.getProperty(property, Integer.toString(fallback)));
            if (value < 0) {
                throw new IllegalArgumentException(property + " must be non-negative");
            }
            return value;
        }
    }

    private static final class BenchmarkPlayerEntity extends ServerPlayerEntity {
        private BenchmarkPlayerEntity(
                MinecraftServer server,
                ServerWorld world,
                GameProfile profile,
                ServerPlayerInteractionManager interactionManager
        ) {
            super(server, world, profile, interactionManager);
        }

        @Override
        public void tick() {
            // The benchmark keeps this player stationary and outside the normal entity tick list.
        }

        @Override
        public boolean damage(DamageSource source, float amount) {
            return false;
        }
    }

    private static final class DiscardingServerPlayNetworkHandler
            extends ServerPlayNetworkHandler {
        private DiscardingServerPlayNetworkHandler(
                MinecraftServer server,
                ServerPlayerEntity player
        ) {
            super(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
        }

        @Override
        public void sendPacket(Packet<?> packet) {
            // The synthetic player has no client. Dragon tracking packets are irrelevant here.
        }
    }
}
