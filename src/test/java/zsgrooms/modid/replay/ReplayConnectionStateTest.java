package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayConnectionStateTest {
    @Test
    void repeatedResetsKeepOneSessionAndRejectOldAttachments() {
        Object firstConnection = new Object();
        ReplayConnectionState<Object> state = new ReplayConnectionState<>(firstConnection);
        ReplayConnectionState.Attachment<Object> first = state.current();
        for (int i = 0; i < 3; i++) {
            ReplayConnectionState.Attachment<Object> old = state.current();
            state.beginReset();
            assertTrue(state.accepts(old));
            assertFalse(state.disconnect());
            assertFalse(state.disconnect()); // Repeated teardown while loading is not a quit.
            assertFalse(state.accepts(old));
            assertTrue(state.attach(new Object()));
            assertFalse(state.accepts(first));
            assertFalse(state.accepts(old));
            assertTrue(state.accepts(state.current()));
        }
        assertTrue(state.disconnect());
        assertFalse(state.attach(new Object()));
    }

    @Test
    void ordinaryDisconnectDoesNotResumeInAnotherWorld() {
        ReplayConnectionState<Object> state = new ReplayConnectionState<>(new Object());
        assertTrue(state.disconnect());
        state.beginReset();
        assertFalse(state.attach(new Object()));
    }

    @Test
    void matchResultOrManualStopWinsOverPendingReset() {
        ReplayConnectionState<Object> state = new ReplayConnectionState<>(new Object());
        state.beginReset();
        state.stop();
        assertTrue(state.disconnect());
        assertFalse(state.attach(new Object()));
    }

    @Test
    void cancelBeforeTeardownKeepsRecordingButCancelDuringLoadEndsIt() {
        ReplayConnectionState<Object> state = new ReplayConnectionState<>(new Object());
        ReplayConnectionState.Attachment<Object> original = state.current();
        state.beginReset();
        assertFalse(state.cancelReset());
        assertTrue(state.accepts(original));
        state.beginReset();
        assertFalse(state.disconnect());
        assertTrue(state.cancelReset());
        assertFalse(state.attach(new Object()));
    }

    @Test
    void replacementRequiresAnExplicitResetAndTeardown() {
        ReplayConnectionState<Object> state = new ReplayConnectionState<>(new Object());
        assertFalse(state.attach(new Object()));
        state.beginReset();
        assertFalse(state.attach(new Object()));
        assertFalse(state.disconnect());
        assertTrue(state.attach(new Object()));
        assertFalse(state.attach(new Object()));
    }
}
