package zsgrooms.modid.rng;

import java.util.Random;

/** Returns deterministic values while preserving the wrapped vanilla RNG's advancement. */
public final class MirroringRandom extends Random {
    private final Random vanillaRandom;
    private final Random deterministicRandom;

    public MirroringRandom(Random vanillaRandom, Random deterministicRandom) {
        super(0L);
        this.vanillaRandom = vanillaRandom;
        this.deterministicRandom = deterministicRandom;
    }

    @Override
    protected int next(int bits) {
        this.vanillaRandom.nextInt();
        return this.deterministicRandom.nextInt() >>> (32 - bits);
    }

    @Override
    public int nextInt() {
        this.vanillaRandom.nextInt();
        return this.deterministicRandom.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        this.vanillaRandom.nextInt(bound);
        return this.deterministicRandom.nextInt(bound);
    }

    @Override
    public long nextLong() {
        this.vanillaRandom.nextLong();
        return this.deterministicRandom.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        this.vanillaRandom.nextBoolean();
        return this.deterministicRandom.nextBoolean();
    }

    @Override
    public float nextFloat() {
        this.vanillaRandom.nextFloat();
        return this.deterministicRandom.nextFloat();
    }

    @Override
    public double nextDouble() {
        this.vanillaRandom.nextDouble();
        return this.deterministicRandom.nextDouble();
    }

    @Override
    public synchronized double nextGaussian() {
        this.vanillaRandom.nextGaussian();
        return this.deterministicRandom.nextGaussian();
    }

    @Override
    public void nextBytes(byte[] bytes) {
        byte[] discarded = new byte[bytes.length];
        this.vanillaRandom.nextBytes(discarded);
        this.deterministicRandom.nextBytes(bytes);
    }
}
