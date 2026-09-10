package zsgrooms.modid.replay;

/** Connection handoffs for one recording. Attachment identity rejects late old-world callbacks. */
final class ReplayConnectionState<T> {
    private Attachment<T> current;
    private boolean resetting;
    private boolean stopped;

    ReplayConnectionState(T connection) {
        current = new Attachment<>(connection);
    }

    synchronized Attachment<T> current() {
        return current;
    }

    synchronized boolean accepts(Attachment<T> attachment) {
        return !stopped && attachment != null && current == attachment;
    }

    synchronized void beginReset() {
        if (!stopped) resetting = true;
    }

    synchronized boolean attach(T connection) {
        if (stopped || !resetting || current != null) return false;
        current = new Attachment<>(connection);
        resetting = false;
        return true;
    }

    /** True means a real session end; false means wait for the replacement world. */
    synchronized boolean disconnect() {
        current = null;
        if (resetting && !stopped) return false;
        stop();
        return true;
    }

    synchronized boolean cancelReset() {
        boolean abandoned = resetting && current == null;
        resetting = false;
        if (abandoned) stop();
        return abandoned;
    }

    synchronized void stop() {
        stopped = true;
        resetting = false;
        current = null;
    }

    static final class Attachment<T> {
        final T connection;

        private Attachment(T connection) {
            this.connection = connection;
        }
    }
}
