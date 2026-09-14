package zsgrooms.modid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SharedNetherEntryState extends PersistentState {
    static final String ID = "zsg_rooms_nether_entry";
    private BlockPos reference;
    private final Set<UUID> enteredPlayers = new HashSet<UUID>();

    public SharedNetherEntryState() {
        super(ID);
    }

    public static SharedNetherEntryState get(ServerWorld overworld) {
        return overworld.getPersistentStateManager().getOrCreate(SharedNetherEntryState::new, ID);
    }

    BlockPos initialize(BlockPos originalSpawn) {
        if (reference == null) {
            reference = referenceForSpawn(originalSpawn);
            markDirty();
        }
        return reference;
    }

    static BlockPos referenceForSpawn(BlockPos originalSpawn) {
        return new BlockPos((originalSpawn.getX() + 0.5D) / 8.0D,
                originalSpawn.getY(), (originalSpawn.getZ() + 0.5D) / 8.0D);
    }

    boolean hasEntered(UUID player) {
        return enteredPlayers.contains(player);
    }

    void complete(UUID player) {
        if (enteredPlayers.add(player)) {
            markDirty();
        }
    }

    @Override
    public void fromTag(CompoundTag tag) {
        reference = tag.contains("Reference", 4) ? BlockPos.fromLong(tag.getLong("Reference")) : null;
        enteredPlayers.clear();
        ListTag players = tag.getList("EnteredPlayers", 8);
        for (int i = 0; i < players.size(); i++) {
            try {
                enteredPlayers.add(UUID.fromString(players.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        if (reference != null) {
            tag.putLong("Reference", reference.asLong());
        }
        ListTag players = new ListTag();
        for (UUID player : enteredPlayers) {
            players.add(StringTag.of(player.toString()));
        }
        tag.put("EnteredPlayers", players);
        return tag;
    }
}
