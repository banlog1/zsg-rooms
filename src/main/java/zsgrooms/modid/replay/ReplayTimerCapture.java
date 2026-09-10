package zsgrooms.modid.replay;

import java.lang.reflect.Method;

/** Optional timer integration; reflective lookup happens once, not once per sample. */
final class ReplayTimerCapture {
    static final long[] UNAVAILABLE = {-1L, -1L};
    private static final ReplayTimerCapture ACCESS = create();
    private final Method instance;
    private final Method rta;
    private final Method igt;

    private ReplayTimerCapture(Class<?> type) throws ReflectiveOperationException {
        instance = type.getMethod("getInstance");
        rta = type.getMethod("getRealTimeAttack");
        igt = type.getMethod("getInGameTime", boolean.class);
    }

    private static ReplayTimerCapture create() {
        try { return new ReplayTimerCapture(Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer")); }
        catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
    }

    static long[] read() {
        if (ACCESS == null) return UNAVAILABLE;
        try {
            Object timer = ACCESS.instance.invoke(null);
            return new long[] {Math.max(0L, ((Number) ACCESS.rta.invoke(timer)).longValue()),
                    Math.max(0L, ((Number) ACCESS.igt.invoke(timer, false)).longValue())};
        } catch (ReflectiveOperationException | RuntimeException error) { return UNAVAILABLE; }
    }
}
