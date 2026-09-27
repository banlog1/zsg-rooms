package zsgrooms.modid;

/** Owns only the current room timer; restores its original category on leaving the room race. */
final class TimerCategoryOverride {
    interface Access {
        Object timer() throws ReflectiveOperationException;
        Object category(Object timer) throws ReflectiveOperationException;
        void category(Object timer, Object category) throws ReflectiveOperationException;
    }

    private Object managedTimer;
    private Object previous;

    void sync(Access access, Object desired) throws ReflectiveOperationException {
        Object timer = access.timer();
        if (desired == null) {
            if (timer == managedTimer && previous != null) access.category(timer, previous);
            managedTimer = null;
            previous = null;
            return;
        }
        Object current = access.category(timer);
        if (timer != managedTimer) {
            managedTimer = timer;
            previous = current;
        }
        if (!desired.equals(current)) access.category(timer, desired);
    }
}
