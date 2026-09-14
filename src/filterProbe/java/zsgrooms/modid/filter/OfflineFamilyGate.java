package zsgrooms.modid.filter;

import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.ChunkPos;

/** Single-owner, single-family cache. Never cache terrain or final chest integrity across sisters. */
final class OfflineFamilyGate implements StagedFilterSearch.FamilyGate {
    private final MinecraftServer server;
    private final boolean templeLoot;
    private final boolean bastionLayout;
    private BastionLayoutPrediction current;
    private long currentLower;
    private ChunkPos currentChunk;
    long lootRejected, layoutsChecked, layoutsRejected, layoutNanos, verifiedStarts;

    OfflineFamilyGate(MinecraftServer server, boolean templeLoot, boolean bastionLayout) {
        this.server = server;
        this.templeLoot = templeLoot;
        this.bastionLayout = bastionLayout;
    }

    @Override
    public boolean allows(long lower, ChunkPos main, ChunkPos bastion) {
        current = null;
        if (templeLoot) {
            TempleLootProbe loot = TempleLootPrediction.predict(server.getOverworld(), lower, main);
            if (!TempleCandidateChecks.hasResources(loot.iron, loot.diamonds)) { lootRejected++; return false; }
        }
        if (!bastionLayout) return true;
        long started = System.nanoTime();
        BastionLayoutPrediction prediction = BastionLayoutPrediction.predict(server.getStructureManager(), lower, bastion);
        layoutNanos += System.nanoTime() - started;
        layoutsChecked++;
        if (!prediction.accepted) { layoutsRejected++; return false; }
        current = prediction;
        currentLower = lower;
        currentChunk = bastion;
        return true;
    }

    void verify(FilterCandidateSearch.Candidate candidate, StructureStart<?> start) {
        if (!bastionLayout) return;
        if (current == null || (candidate.seedForValidation() & StagedFilterSearch.MASK) != currentLower
                || !candidate.bastion.equals(currentChunk)) {
            throw new IllegalStateException("Generated bastion does not belong to the cached search family");
        }
        current.verify(start);
        verifiedStarts++;
    }
}
