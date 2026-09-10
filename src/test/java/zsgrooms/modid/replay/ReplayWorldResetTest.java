package zsgrooms.modid.replay;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplayWorldResetTest {
    @Test
    void handoffRemovesOnlyRemainingBossBarsAndClearsTheTrackedState() throws Exception {
        ReplayWorldReset reset = new ReplayWorldReset();
        BossBar first = bar();
        BossBar removed = bar();
        reset.observe(new BossBarS2CPacket(BossBarS2CPacket.Type.ADD, first));
        reset.observe(new BossBarS2CPacket(BossBarS2CPacket.Type.ADD, removed));
        reset.observe(new BossBarS2CPacket(BossBarS2CPacket.Type.REMOVE, removed));
        List<Packet<?>> cleanup = reset.cleanup();
        assertEquals(2, cleanup.size());
        BossBarS2CPacket removal = (BossBarS2CPacket) cleanup.get(0);
        assertEquals(first.getUuid(), removal.getUuid());
        assertEquals(BossBarS2CPacket.Type.REMOVE, removal.getType());
        assertTrue(cleanup.get(1) instanceof StopSoundS2CPacket);
        for (Packet<?> packet : cleanup) reset.observe(packet);
        assertEquals(1, reset.cleanup().size());
    }

    @Test
    void handoffRemovesRecordedPlayersWithoutChangingLiveProfiles() throws Exception {
        ReplayWorldReset reset = new ReplayWorldReset();
        UUID player = UUID.randomUUID();
        UUID departed = UUID.randomUUID();
        PlayerListS2CPacket added = list(PlayerListS2CPacket.Action.ADD_PLAYER, player, departed);
        reset.observe(added);
        reset.observe(list(PlayerListS2CPacket.Action.REMOVE_PLAYER, departed));
        List<Packet<?>> cleanup = reset.cleanup();
        PlayerListS2CPacket removal = (PlayerListS2CPacket) cleanup.get(0);
        assertEquals(PlayerListS2CPacket.Action.REMOVE_PLAYER, removal.getAction());
        assertEquals(1, removal.getEntries().size());
        assertEquals(player, removal.getEntries().get(0).getProfile().getId());
        assertEquals(2, added.getEntries().size());
        for (Packet<?> packet : cleanup) reset.observe(packet);
        assertEquals(1, reset.cleanup().size());
    }

    private static BossBar bar() {
        return new BossBar(UUID.randomUUID(), new LiteralText("Test"), BossBar.Color.WHITE, BossBar.Style.PROGRESS) {};
    }

    private static PlayerListS2CPacket list(PlayerListS2CPacket.Action action, UUID... players) {
        PlayerListS2CPacket packet = new PlayerListS2CPacket(action);
        for (UUID player : players) {
            packet.getEntries().add(packet.new Entry(new GameProfile(player, "Test"), 0, GameMode.SURVIVAL, null));
        }
        return packet;
    }
}
