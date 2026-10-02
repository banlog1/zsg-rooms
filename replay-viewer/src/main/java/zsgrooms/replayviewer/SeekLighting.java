// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.FullReplaySender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.world.chunk.light.LightingProvider;

/** Experimental interactive-seek optimization; never active during capture or video rendering. */
public final class SeekLighting {
    private static final ThreadLocal<SeekLightingBatch> current = new ThreadLocal<>();

    private SeekLighting() {}

    public static void send(FullReplaySender sender, int time, boolean enabled) {
        if (!enabled || !MinecraftClient.getInstance().isOnThread() || sender.isAsyncMode()) {
            sender.sendPacketsTill(time);
            return;
        }
        SeekLightingBatch previous = current.get();
        SeekLightingBatch batch = new SeekLightingBatch(SeekLighting::drain);
        current.set(batch);
        try { sender.sendPacketsTill(time); }
        finally {
            try { batch.flush(); }
            finally { if (previous == null) current.remove(); else current.set(previous); }
        }
    }

    public static boolean defer() {
        SeekLightingBatch batch = current.get();
        if (batch == null) return false;
        batch.defer();
        return true;
    }

    public static void beforePacket(Packet<?> packet) {
        SeekLightingBatch batch = current.get();
        if (batch == null) return;
        // These packets can replace queued light arrays or invalidate the world they belong to.
        if (packet instanceof LightUpdateS2CPacket || packet instanceof BlockUpdateS2CPacket
                || packet instanceof ChunkDeltaUpdateS2CPacket || packet instanceof UnloadChunkS2CPacket
                || packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket
                || packet instanceof ChunkLoadDistanceS2CPacket || packet instanceof ChunkRenderDistanceCenterS2CPacket
                || packet instanceof ExplosionS2CPacket) batch.flush();
    }

    private static void drain() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        LightingProvider provider = client.world.getLightingProvider();
        while (provider.hasUpdates()) provider.doLightUpdates(Integer.MAX_VALUE, true, true);
    }
}
