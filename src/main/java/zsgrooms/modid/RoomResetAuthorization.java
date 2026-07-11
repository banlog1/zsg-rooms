package zsgrooms.modid;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RoomResetAuthorization {
    private static final AtomicBoolean NEXT_RESET_ALLOWED = new AtomicBoolean(false);

    private RoomResetAuthorization() {
    }

    public static void allowNextReset() {
        NEXT_RESET_ALLOWED.set(true);
    }

    public static boolean consumeResetPermission() {
        return NEXT_RESET_ALLOWED.compareAndSet(true, false);
    }

    public static void clear() {
        NEXT_RESET_ALLOWED.set(false);
    }
}
