package zsgrooms.modid;

import zsgrooms.modid.ui.RoomUiPreferences;

public final class SeedDebugLog {
    private SeedDebugLog() {
    }

    public static boolean isEnabled() {
        return RoomUiPreferences.isSeedDebugLoggingEnabled();
    }

    public static void info(String message, Object... arguments) {
        if (isEnabled()) {
            ZsgRooms.LOGGER.info(message, arguments);
        }
    }

    public static void warn(String message, Object... arguments) {
        if (isEnabled()) {
            ZsgRooms.LOGGER.warn(message, arguments);
        }
    }
}
