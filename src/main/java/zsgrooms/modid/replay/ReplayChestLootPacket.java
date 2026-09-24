package zsgrooms.modid.replay;

import java.util.List;

/** In-process attachment only: never serialized into Minecraft's live chunk packet. */
public interface ReplayChestLootPacket {
    List<Loot> zsgRooms$getChestLoot();

    final class Loot {
        public final long pos, seed;
        public final int state;
        public final String dimension, table;

        public Loot(long pos, int state, String dimension, String table, long seed) {
            this.pos = pos;
            this.state = state;
            this.dimension = dimension;
            this.table = table;
            this.seed = seed;
        }
    }
}
