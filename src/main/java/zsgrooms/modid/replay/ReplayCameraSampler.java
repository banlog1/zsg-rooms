package zsgrooms.modid.replay;

/** Render-thread rate limiter. Missed render frames are never synthesized or caught up. */
final class ReplayCameraSampler {
    private final int hz;
    private long lastBucket = -1, lastMillis = -1;
    private int lastWorld = Integer.MIN_VALUE, lastEntity = Integer.MIN_VALUE, lastFlags = -1;
    private boolean interrupted;
    private final java.util.concurrent.atomic.AtomicBoolean boundary = new java.util.concurrent.atomic.AtomicBoolean();

    ReplayCameraSampler(int hz) {
        if (hz != 60 && hz != 120 && hz != 240) throw new IllegalArgumentException("Invalid camera rate");
        this.hz = hz;
    }

    void invalidate() { boundary.set(true); }

    void capture(ReplayCameraTrack track, long nanos, int world, int entity, boolean available, float yaw, float pitch) {
        if (nanos < 0 || nanos / 1000000L > Integer.MAX_VALUE || !track.accepting()) return;
        interrupted |= boundary.getAndSet(false);
        boolean valid = available && world >= 0 && entity >= 0 && Float.isFinite(yaw) && Float.isFinite(pitch)
                && pitch >= -90 && pitch <= 90;
        long millis = nanos / 1000000L;
        if (millis <= lastMillis) {
            if (!valid || world != lastWorld || entity != lastEntity) interrupted = true;
            return;
        }
        int flags = valid ? interrupted ? ReplayCameraTrack.GAP : ReplayCameraTrack.AVAILABLE : 0;
        long bucket = nanos * hz / 1000000000L;
        boolean boundary = world != lastWorld || entity != lastEntity || flags != lastFlags;
        if (!boundary && (!valid || bucket == lastBucket)) return;
        lastMillis = millis;
        lastBucket = bucket;
        lastWorld = world;
        lastEntity = entity;
        lastFlags = flags;
        interrupted = false;
        track.offer(millis, world, entity, flags, valid ? yaw : 0, valid ? pitch : 0);
    }
}
