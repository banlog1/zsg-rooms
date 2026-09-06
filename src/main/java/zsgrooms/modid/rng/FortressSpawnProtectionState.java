package zsgrooms.modid.rng;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import zsgrooms.modid.SeedDebugLog;

import java.util.HashMap;
import java.util.Map;

public final class FortressSpawnProtectionState extends PersistentState {
    public static final int INITIAL_PACK_OPPORTUNITIES = 8;
    public static final int MAX_INITIAL_CYCLES = 4096;
    private static final String ID = "zsg_rooms_fortress_opportunities";
    private final Map<Long, Allowance> allowances = new HashMap<Long, Allowance>();
    private Long firstFortress;
    private Allowance firstAllowance;
    private boolean legacyClosed;

    public FortressSpawnProtectionState() {
        super(ID);
    }

    public static FortressSpawnProtectionState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(FortressSpawnProtectionState::new, ID);
    }

    public boolean hasRemaining(long fortress) {
        if (this.legacyClosed || (this.firstFortress != null && this.firstFortress.longValue() != fortress)) {
            return false;
        }
        Allowance allowance = this.allowances.get(fortress);
        return allowance == null || allowance.hasRemaining();
    }

    public boolean isFinished() {
        return this.legacyClosed || (this.firstAllowance != null && !this.firstAllowance.hasRemaining());
    }

    public Evaluation beginCycle(long fortress) {
        if (!hasRemaining(fortress)) {
            return null;
        }
        if (this.firstFortress == null) {
            this.firstFortress = fortress;
        }
        Allowance allowance = this.allowances.get(fortress);
        if (allowance == null) {
            allowance = new Allowance();
            this.allowances.put(fortress, allowance);
            if (SeedDebugLog.isEnabled()) {
                SeedDebugLog.info("[ZSG-Rooms/FortressProtection] activated fortress={},{} opportunities={}",
                        new ChunkPos(fortress).x, new ChunkPos(fortress).z, INITIAL_PACK_OPPORTUNITIES);
            }
        }
        if (!allowance.hasRemaining()) {
            return null;
        }
        this.firstAllowance = allowance;
        allowance.cycles++;
        markDirty();
        if (allowance.cycles == MAX_INITIAL_CYCLES && SeedDebugLog.isEnabled()) {
            SeedDebugLog.info("[ZSG-Rooms/FortressProtection] final-evaluation fortress={},{} "
                            + "reason=evaluation_limit used={} limit={}",
                    new ChunkPos(fortress).x, new ChunkPos(fortress).z,
                    allowance.used, MAX_INITIAL_CYCLES);
        }
        return new Evaluation(fortress, allowance);
    }

    @Override
    public void fromTag(CompoundTag tag) {
        this.allowances.clear();
        this.firstFortress = tag.contains("FirstFortress", 4) ? tag.getLong("FirstFortress") : null;
        ListTag entries = tag.getList("Fortresses", 10);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            Allowance allowance = new Allowance();
            allowance.used = Math.max(0, entry.getInt("Used"));
            allowance.cycles = Math.max(0, entry.getInt("Cycles"));
            this.allowances.put(entry.getLong("StartChunk"), allowance);
        }
        this.legacyClosed = tag.getBoolean("LegacyClosed");
        if (this.firstFortress == null && !this.allowances.isEmpty()) {
            // One old entry identifies the first fortress; multiple entries have no activation order.
            if (this.allowances.size() == 1) {
                this.firstFortress = this.allowances.keySet().iterator().next();
            } else {
                this.legacyClosed = true;
            }
            markDirty();
        }
        this.firstAllowance = this.allowances.get(this.firstFortress);
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        if (this.firstFortress != null) {
            tag.putLong("FirstFortress", this.firstFortress);
        } else {
            tag.remove("FirstFortress");
        }
        tag.putBoolean("LegacyClosed", this.legacyClosed);
        ListTag entries = new ListTag();
        for (Map.Entry<Long, Allowance> item : this.allowances.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("StartChunk", item.getKey());
            entry.putInt("Used", item.getValue().used);
            entry.putInt("Cycles", item.getValue().cycles);
            entries.add(entry);
        }
        tag.put("Fortresses", entries);
        return tag;
    }

    public final class Evaluation {
        private final long fortress;
        private final Allowance allowance;

        private Evaluation(long fortress, Allowance allowance) {
            this.fortress = fortress;
            this.allowance = allowance;
        }

        public int consumeOpportunity() {
            if (this.allowance.used >= INITIAL_PACK_OPPORTUNITIES) {
                return -1;
            }
            markDirty();
            return ++this.allowance.used;
        }

        public long getFortress() {
            return this.fortress;
        }
    }

    private static final class Allowance {
        private int used;
        private int cycles;

        private boolean hasRemaining() {
            return this.used < INITIAL_PACK_OPPORTUNITIES && this.cycles < MAX_INITIAL_CYCLES;
        }
    }
}
