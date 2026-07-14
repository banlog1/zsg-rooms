package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoomWebSocketTransportTest {
    @Test
    public void reconnectBackoffGrowsAndCapsAtFifteenSeconds() {
        assertEquals(1000L, RoomWebSocketTransport.reconnectDelayMillis(0));
        assertEquals(2000L, RoomWebSocketTransport.reconnectDelayMillis(1));
        assertEquals(4000L, RoomWebSocketTransport.reconnectDelayMillis(2));
        assertEquals(8000L, RoomWebSocketTransport.reconnectDelayMillis(3));
        assertEquals(15000L, RoomWebSocketTransport.reconnectDelayMillis(4));
        assertEquals(15000L, RoomWebSocketTransport.reconnectDelayMillis(20));
    }
}
