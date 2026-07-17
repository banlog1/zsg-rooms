package zsgrooms.modid;

import java.lang.reflect.Method;

public final class SpeedRunIgtBridge {
    private SpeedRunIgtBridge() {
    }

    public static String completedInGameTime() {
        long milliseconds = currentInGameTimeMilliseconds();
        return milliseconds > 0L ? formatMilliseconds(milliseconds) : "";
    }

    public static long currentInGameTimeMilliseconds() {
        try {
            Class<?> timerClass = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
            Object timer = timerClass.getMethod("getInstance").invoke(null);
            Method getInGameTime = timerClass.getMethod("getInGameTime", boolean.class);
            return Math.max(0L, ((Number) getInGameTime.invoke(timer, false)).longValue());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static String formatMilliseconds(long milliseconds) {
        long safeTime = Math.max(0L, milliseconds);
        long hours = safeTime / 3600000L;
        long minutes = (safeTime / 60000L) % 60L;
        long seconds = (safeTime / 1000L) % 60L;
        long millis = safeTime % 1000L;
        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}
