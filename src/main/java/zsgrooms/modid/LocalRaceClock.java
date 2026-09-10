package zsgrooms.modid;

import zsgrooms.modid.net.RaceFinishArbiter;

import java.util.UUID;

/** One local monotonic clock per launched race, independent of world resets and timer mods. */
final class LocalRaceClock {
    private String raceId = "";
    private Object server;
    private UUID player;
    private boolean started;
    private long startNanos;
    private Long finishElapsedNanos;

    synchronized void arm(String id, Object server, UUID player) {
        if (id == null || id.isEmpty() || server == null || player == null || raceId.equals(id)) {
            return;
        }
        raceId = id;
        this.server = server;
        this.player = player;
        started = false;
        finishElapsedNanos = null;
    }

    synchronized Start onResumedTick(Object server, long nowNanos) {
        if (!raceId.isEmpty() && !started && this.server == server) {
            startNanos = nowNanos;
            started = true;
            return new Start(raceId, player, startNanos);
        }
        return null;
    }

    static final class Start {
        final String raceId;
        final UUID player;
        final long nanos;

        private Start(String raceId, UUID player, long nanos) {
            this.raceId = raceId;
            this.player = player;
            this.nanos = nanos;
        }
    }

    synchronized void bindWorld(String id, Object server, UUID player) {
        if (started && raceId.equals(id) && server != null && this.player.equals(player) && this.server != server) {
            this.server = server;
            finishElapsedNanos = null;
        }
    }

    synchronized void capture(Object server, UUID player, long nowNanos) {
        if (started && this.server == server && this.player.equals(player) && finishElapsedNanos == null) {
            long elapsed = nowNanos - startNanos;
            if (elapsed >= 0L && elapsed <= RaceFinishArbiter.MAX_ELAPSED_NANOS) {
                finishElapsedNanos = elapsed;
            }
        }
    }

    synchronized long consume(String id, Object server, UUID player) {
        if (!raceId.equals(id) || this.server != server || this.player == null || !this.player.equals(player)
                || finishElapsedNanos == null) {
            return -1L;
        }
        long elapsed = finishElapsedNanos;
        finishElapsedNanos = null;
        return elapsed;
    }

    synchronized void clear() {
        raceId = "";
        server = null;
        player = null;
        started = false;
        finishElapsedNanos = null;
    }
}
