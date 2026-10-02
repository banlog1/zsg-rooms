package zsgrooms.modid.seedbank;

import zsgrooms.modid.ZsgSeedBridge;

public final class SeedBankEntry {
    public final String seed;
    public final int structureX;
    public final int structureZ;

    SeedBankEntry(String seed, int structureX, int structureZ) {
        this.seed = seed;
        this.structureX = structureX;
        this.structureZ = structureZ;
    }

    public String roomSeed(SeedBankProfile profile) {
        return ZsgSeedBridge.buildSeedForStructure(seed, profile.specification, 4)
                + "|target:" + structureX + "," + structureZ;
    }
}
