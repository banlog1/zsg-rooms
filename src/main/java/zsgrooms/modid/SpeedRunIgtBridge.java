package zsgrooms.modid;

import java.lang.reflect.Method;

public final class SpeedRunIgtBridge {
    private static final TimerCategoryOverride CATEGORY = new TimerCategoryOverride();
    private static TimerApi api;
    private static boolean apiChecked;
    private static boolean failureLogged;
    private SpeedRunIgtBridge() {
    }

    public static void syncCategory(InGame game) {
        TimerApi timer = api();
        if (timer == null) return;
        try {
            CATEGORY.sync(timer, game == null || !game.getIsInGame() ? null
                    : game.isAaThunderless() ? timer.aa : timer.any);
        } catch (ReflectiveOperationException | LinkageError error) {
            reportFailure(error);
        }
    }

    public static void syncActiveCategory() {
        String room = ZsgRooms.getActiveRoomName();
        syncCategory(room == null ? null : ZsgRooms.getGame(room));
    }

    public static void completeAaRun() {
        TimerApi timer = api();
        if (timer == null) return;
        try {
            timer.complete.invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            reportFailure(error);
        }
    }

    private static synchronized TimerApi api() {
        if (!apiChecked) {
            apiChecked = true;
            try { api = new TimerApi(); }
            catch (ClassNotFoundException ignored) { /* Optional timer mod. */ }
            catch (ReflectiveOperationException | LinkageError error) { reportFailure(error); }
        }
        return api;
    }

    private static void reportFailure(Throwable error) {
        if (!failureLogged) {
            failureLogged = true;
            ZsgRooms.LOGGER.warn("Could not control the SpeedrunIGT race category", error);
        }
    }

    private static final class TimerApi implements TimerCategoryOverride.Access {
        private final Method instance, getCategory, setCategory, complete;
        private final Object aa, any;

        TimerApi() throws ReflectiveOperationException {
            Class<?> timer = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
            Class<?> category = Class.forName("com.redlimerl.speedrunigt.timer.category.RunCategory");
            Class<?> categories = Class.forName("com.redlimerl.speedrunigt.timer.category.RunCategories");
            instance = timer.getMethod("getInstance");
            getCategory = timer.getMethod("getCategory");
            setCategory = timer.getMethod("setCategory", category, boolean.class);
            complete = timer.getMethod("complete");
            aa = categories.getField("ALL_ADVANCEMENTS").get(null);
            any = categories.getField("ANY").get(null);
        }

        public Object timer() throws ReflectiveOperationException { return instance.invoke(null); }
        public Object category(Object timer) throws ReflectiveOperationException { return getCategory.invoke(timer); }
        public void category(Object timer, Object category) throws ReflectiveOperationException {
            setCategory.invoke(timer, category, false);
        }
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
