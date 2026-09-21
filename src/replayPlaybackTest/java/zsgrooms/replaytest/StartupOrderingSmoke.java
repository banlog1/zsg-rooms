package zsgrooms.replaytest;

import com.replaymod.core.ReplayMod;
import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import zsgrooms.modid.ZsgRooms;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Forces the two startup queues into both orders, without editing the recording. */
final class StartupOrderingSmoke {
    static void run(ReplayHandler handler, MinecraftClient client) throws Exception {
        FullReplaySender sender = (FullReplaySender) handler.getReplaySender();
        sender.setSyncModeAndWait();
        drainClient(client);
        ReplayMod.instance.runTasks();
        Method process = FullReplaySender.class.getDeclaredMethod("processPacket", Packet.class);
        process.setAccessible(true);
        // Relative X makes ReplayMod skip camera placement, but still run its terrain dismissal.
        PlayerPositionLookS2CPacket position = new PlayerPositionLookS2CPacket(0, 0, 0, 0, 0,
                Collections.singleton(PlayerPositionLookS2CPacket.Flag.X), 0);

        for (boolean replayQueueFirst : new boolean[] {true, false}) {
            client.openScreen(null);
            enqueue(client, sender, process, position, new DownloadingTerrainScreen());
            if (replayQueueFirst) ReplayMod.instance.runTasks();
            drainClient(client);
            ReplayMod.instance.runTasks();
            require(client.currentScreen == null, "Terrain dismissal lost; replayQueueFirst=" + replayQueueFirst);
        }

        Screen unrelated = new Screen(new net.minecraft.text.LiteralText("Startup test menu")) {};
        enqueue(client, sender, process, position, unrelated);
        ReplayMod.instance.runTasks();
        drainClient(client);
        ReplayMod.instance.runTasks();
        require(client.currentScreen == unrelated, "Dismissal closed an unrelated screen");

        client.openScreen(new DownloadingTerrainScreen());
        drainClient(client);
        ReplayMod.instance.runTasks();
        require(client.currentScreen instanceof DownloadingTerrainScreen, "Closed without a position packet");
        process.invoke(sender, position);
        drainClient(client);
        ReplayMod.instance.runTasks();
        require(client.currentScreen == null, "Synchronous position did not dismiss terrain screen");
        ZsgRooms.LOGGER.info("[ReplayStartupSmoke] PASS: both queue orders, unrelated screen, missing position, synchronous position");
    }

    private static void enqueue(MinecraftClient client, FullReplaySender sender, Method process,
                                PlayerPositionLookS2CPacket position, Screen screen) throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                // Model the screen opened by the already-queued JoinGame handler.
                client.execute(() -> client.openScreen(screen));
                process.invoke(sender, position);
            } catch (Exception error) { failure.set(error); }
        }, "ZSG replay startup ordering test");
        worker.setDaemon(true);
        worker.start();
        worker.join(5000);
        require(!worker.isAlive(), "Position processing timed out");
        if (failure.get() != null) throw failure.get();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void drainClient(MinecraftClient client) throws Exception {
        Method drain = net.minecraft.util.thread.ThreadExecutor.class.getDeclaredMethod("runTasks");
        drain.setAccessible(true);
        drain.invoke(client);
    }
}
