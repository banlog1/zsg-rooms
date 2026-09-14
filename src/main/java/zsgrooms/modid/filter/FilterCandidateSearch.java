package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.chunk.StructureConfig;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Chunk-free preliminary search. Results must never be launched without the remaining validation. */
public final class FilterCandidateSearch {
    public static final int FORMAT_VERSION = 1;

    private FilterCandidateSearch() {
    }

    public enum Type {
        VILLAGE, TEMPLE, SHIPWRECK;

        public static Type parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }

        public StructureFeature<?> structure() {
            if (this == VILLAGE) return StructureFeature.VILLAGE;
            if (this == TEMPLE) return StructureFeature.DESERT_PYRAMID;
            return StructureFeature.SHIPWRECK;
        }
    }

    public enum Status { PRELIMINARY, LIMIT_REACHED, TIMED_OUT, CANCELLED }
    public enum Rejection { BASTION_DISTANCE, FORTRESS_DISTANCE, MAIN_DISTANCE, SHIPWRECK_LAYOUT, MAIN_BIOME, TREE_BIOME, NETHER_BIOME, NO_LAKE_ATTEMPT }

    public static final class Candidate {
        private final long seed;
        public final Type type;
        public final boolean overpowered;
        public final ChunkPos main;
        public final ChunkPos bastion;
        public final ChunkPos fortress;
        public final List<BlockPos> lakeAttempts;

        Candidate(long seed, Type type, boolean overpowered, ChunkPos main, ChunkPos bastion,
                          ChunkPos fortress, List<BlockPos> lakeAttempts) {
            this.seed = seed;
            this.type = type;
            this.overpowered = overpowered;
            this.main = main;
            this.bastion = bastion;
            this.fortress = fortress;
            this.lakeAttempts = lakeAttempts;
        }

        public long seedForValidation() {
            return seed;
        }
    }

    public static final class Result {
        public final Status status;
        public final long attempts;
        public final long elapsedMillis;
        private final Candidate candidate;
        private final long[] rejected;

        Result(Status status, long attempts, long started, Candidate candidate, long[] rejected) {
            this.status = status;
            this.attempts = attempts;
            this.elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            this.candidate = candidate;
            this.rejected = rejected.clone();
        }

        public Optional<Candidate> candidate() {
            return Optional.ofNullable(candidate);
        }

        public long rejected(Rejection reason) {
            return rejected[reason.ordinal()];
        }
    }

    static Candidate checkExactSeed(long seed, Type type, boolean overpowered) {
        StructureCheck structure = checkGeometry(seed, type, overpowered, new ChunkRandom());
        return structure.rejection == null ? checkSister(seed, type, overpowered, structure, false,
                new long[Rejection.values().length], () -> false) : null;
    }

    public static Result search(Type type, boolean overpowered, long searchSeed, long maxAttempts,
                                long timeoutMillis, BooleanSupplier cancelled) {
        if (type == null || cancelled == null || maxAttempts < 1 || timeoutMillis < 1 || timeoutMillis > 300000) {
            throw new IllegalArgumentException("Search needs a type, cancellation check, positive attempt limit and 1-300000 ms timeout");
        }
        long started = System.nanoTime();
        long timeout = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        BooleanSupplier stop = () -> Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()
                || System.nanoTime() - started >= timeout;
        SplittableRandom seeds = new SplittableRandom(searchSeed);
        ChunkRandom placement = new ChunkRandom();
        long[] rejected = new long[Rejection.values().length];
        long attempts = 0;
        while (attempts < maxAttempts && !stop.getAsBoolean()) {
            long seed = seeds.nextLong();
            attempts++;
            StructureCheck structure = checkGeometry(seed, type, overpowered, placement);
            if (structure.rejection != null) { rejected[structure.rejection.ordinal()]++; continue; }
            Candidate candidate = checkSister(seed, type, overpowered, structure, false, rejected, stop);
            if (stop.getAsBoolean()) break;
            if (candidate != null) return new Result(Status.PRELIMINARY, attempts, started, candidate, rejected);
        }
        Status status = Thread.currentThread().isInterrupted() || cancelled.getAsBoolean() ? Status.CANCELLED
                : System.nanoTime() - started >= timeout ? Status.TIMED_OUT : Status.LIMIT_REACHED;
        return new Result(status, attempts, started, null, rejected);
    }

    static final class StructureCheck {
        final ChunkPos main, bastion, fortress;
        final Rejection rejection;

        StructureCheck(ChunkPos main, ChunkPos bastion, ChunkPos fortress, Rejection rejection) {
            this.main = main;
            this.bastion = bastion;
            this.fortress = fortress;
            this.rejection = rejection;
        }
    }

    static StructureCheck checkGeometry(long seed, Type type, boolean overpowered, ChunkRandom random) {
        ChunkPos bastion = findNetherPlacement(seed, overpowered ? 32 : 96, true, random);
        if (bastion == null) return new StructureCheck(null, null, null, Rejection.BASTION_DISTANCE);
        ChunkPos fortress = findNetherPlacement(seed, overpowered ? 112 : 256, false, random);
        if (fortress == null) return new StructureCheck(null, null, null, Rejection.FORTRESS_DISTANCE);
        ChunkPos main = placement(seed, type.structure(), 0, 0, random);
        // Temple's old 224-block entry envelope plus the new 96-block pool window.
        int mainLimit = type == Type.TEMPLE ? 320 : type == Type.SHIPWRECK ? 208 : 224;
        if (!within(main, mainLimit)) return new StructureCheck(null, null, null, Rejection.MAIN_DISTANCE);
        return new StructureCheck(main, bastion, fortress, null);
    }

    static boolean netherViable(long seed, StructureCheck structure) {
        BiomeSource nether = MultiNoiseBiomeSource.Preset.NETHER.getBiomeSource(seed);
        return structureBiome(nether, structure.bastion).hasStructureFeature(StructureFeature.BASTION_REMNANT)
                && structureBiome(nether, structure.fortress).hasStructureFeature(StructureFeature.FORTRESS);
    }

    static Candidate checkSister(long seed, Type type, boolean overpowered, StructureCheck structure,
                                 boolean familyChecked, long[] rejected, BooleanSupplier stop) {
        ChunkPos main = structure.main;
        if (!familyChecked && type == Type.SHIPWRECK && !shipwreckLayout(seed, main, new ChunkRandom())) {
            rejected[Rejection.SHIPWRECK_LAYOUT.ordinal()]++;
            return null;
        }
        BiomeSource overworld = new VanillaLayeredBiomeSource(seed, false, false);
        Biome mainBiome = structureBiome(overworld, main);
        if (!mainBiome.hasStructureFeature(type.structure())
                || type == Type.VILLAGE && mainBiome != Biomes.PLAINS && mainBiome != Biomes.SAVANNA && mainBiome != Biomes.DESERT
                || type == Type.SHIPWRECK && mainBiome.getCategory() != Biome.Category.OCEAN) {
            rejected[Rejection.MAIN_BIOME.ordinal()]++;
            return null;
        }
        if (type == Type.VILLAGE && !TreeBiomeChecks.nearby(overworld, seed, new BlockPos(main.x << 4, 64, main.z << 4), true)) {
            rejected[Rejection.TREE_BIOME.ordinal()]++;
            return null;
        }
        if (!familyChecked && !netherViable(seed, structure)) {
            rejected[Rejection.NETHER_BIOME.ordinal()]++;
            return null;
        }
        List<BlockPos> lakes = type == Type.SHIPWRECK ? Collections.emptyList()
                : LavaLakeHints.find(seed, overworld, main.x << 4, main.z << 4, SurfaceTerrainChecks.LAVA_POOL_RADIUS, stop);
        if (stop.getAsBoolean()) return null;
        if (type != Type.SHIPWRECK && lakes.isEmpty()) {
            rejected[Rejection.NO_LAKE_ATTEMPT.ordinal()]++;
            return null;
        }
        return new Candidate(seed, type, overpowered, main, structure.bastion, structure.fortress, lakes);
    }

    static Biome structureBiome(BiomeSource source, ChunkPos chunk) {
        return source.getBiomeForNoiseGen((chunk.x << 2) + 2, 0, (chunk.z << 2) + 2);
    }

    static boolean shipwreckLayout(long seed, ChunkPos pos, ChunkRandom random) {
        random.setCarverSeed(seed, pos.x, pos.z);
        int rotation = random.nextInt(4);
        int template = random.nextInt(20);
        return rotation == 3 && (template == 0 || template == 7 || template == 10 || template == 17);
    }

    static ChunkPos findNetherPlacement(long seed, int range, boolean bastion, ChunkRandom random) {
        StructureFeature<?> feature = bastion ? StructureFeature.BASTION_REMNANT : StructureFeature.FORTRESS;
        int spacing = StructuresConfig.DEFAULT_STRUCTURES.get(feature).getSpacing();
        int chunks = (range + 15) / 16;
        for (int regionX = Math.floorDiv(-chunks, spacing); regionX <= Math.floorDiv(chunks, spacing); regionX++) {
            for (int regionZ = Math.floorDiv(-chunks, spacing); regionZ <= Math.floorDiv(chunks, spacing); regionZ++) {
                ChunkPos pos = placement(seed, feature, regionX, regionZ, random);
                // The two vanilla features share spacing/salt and then make complementary nextInt(5) rolls.
                boolean isBastion = random.nextInt(5) >= 2;
                if (isBastion == bastion && within(pos, range)) return pos;
            }
        }
        return null;
    }

    static ChunkPos placement(long seed, StructureFeature<?> feature, int regionX, int regionZ, ChunkRandom random) {
        StructureConfig config = StructuresConfig.DEFAULT_STRUCTURES.get(feature);
        return feature.method_27218(config, seed, random, regionX * config.getSpacing(), regionZ * config.getSpacing());
    }

    static boolean within(ChunkPos chunk, int blockRange) {
        return Math.abs((long) chunk.x * 16) <= blockRange && Math.abs((long) chunk.z * 16) <= blockRange;
    }
}
