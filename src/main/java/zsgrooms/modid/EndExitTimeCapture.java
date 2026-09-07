package zsgrooms.modid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import zsgrooms.modid.net.RaceFinishArbiter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Carries the local integrated server's portal event time across to the client packet handler. */
public final class EndExitTimeCapture {
    private static final AtomicReference<Entry> CAPTURED = new AtomicReference<Entry>();

    private EndExitTimeCapture() {
    }

    public static void capture(ServerPlayerEntity player) {
        if (!player.server.isDedicated()) {
            CAPTURED.set(new Entry(player.server, player.getUuid(), RaceFinishArbiter.now()));
        }
    }

    public static long consume(MinecraftClient client) {
        long now = RaceFinishArbiter.now();
        Entry entry = CAPTURED.getAndSet(null);
        if (entry != null && client != null && client.getServer() == entry.server
                && client.player != null && client.player.getUuid().equals(entry.player)
                && now >= entry.time && now - entry.time <= 10000L) {
            return entry.time;
        }
        return now;
    }

    private static final class Entry {
        final MinecraftServer server;
        final UUID player;
        final long time;

        Entry(MinecraftServer server, UUID player, long time) {
            this.server = server;
            this.player = player;
            this.time = time;
        }
    }
}
