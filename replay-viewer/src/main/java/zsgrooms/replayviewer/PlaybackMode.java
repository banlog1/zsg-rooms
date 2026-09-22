// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplaySender;
import org.apache.logging.log4j.LogManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import zsgrooms.replayviewer.mixin.QuickReplaySenderAccessor;

/** Viewer-only indexing. The full sender remains stopped while its replacement is prepared. */
final class PlaybackMode {
    private final ReplayHandler handler;
    private final Runnable changed;
    private ListenableFuture<Void> initialization;
    private volatile double progress;
    private boolean busy, closed;
    private double speed;
    private String error;
    private Screen settings;

    PlaybackMode(ReplayHandler handler, Runnable changed) {
        this.handler = handler;
        this.changed = changed;
    }

    boolean quick() { return handler.isQuickMode(); }
    boolean busy() { return busy; }
    boolean available() { return !closed && !busy && !ReplayMod.isMinimalMode(); }
    String status() {
        if (busy) return "Preparing Quick Mode: " + (int) (progress * 100) + "%";
        return error == null ? "" : error;
    }

    void setQuick(boolean enabled) {
        ReplaySender sender = handler.getReplaySender();
        if (!available() || enabled == quick() || !sender.isAsyncMode()
                || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
        busy = true;
        error = null;
        progress = 0;
        speed = sender.paused() ? 0 : sender.getReplaySpeed();
        Screen current = MinecraftClient.getInstance().currentScreen;
        settings = current instanceof AnalysisScreen ? current : null;
        try {
            sender.setReplaySpeed(0);
            sender.setSyncModeAndWait();
        } catch (RuntimeException failure) {
            failed(failure);
            return;
        }
        // Match ReplayMod's deferred switch: never rebuild the world inside an input callback.
        ReplayMod.instance.runLaterWithoutLock(() -> {
            if (closed) return;
            try {
                if (enabled) {
                    initialization = ((QuickReplaySenderAccessor) handler).zsgViewer$getQuickSender()
                            .initialize(value -> progress = value);
                } else finish(false);
            } catch (RuntimeException failure) { failed(failure); }
        });
    }

    void update() {
        if (closed || initialization == null || !initialization.isDone()) return;
        ListenableFuture<Void> completed = initialization;
        initialization = null;
        ReplayMod.instance.runLaterWithoutLock(() -> {
            if (closed) return;
            try {
                completed.get();
                finish(true);
            } catch (Exception failure) { failed(failure); }
        });
    }

    private void finish(boolean enabled) {
        handler.setQuickMode(enabled);
        resume();
        changed.run();
    }

    private void resume() {
        ReplaySender sender = handler.getReplaySender();
        sender.setReplaySpeed(speed);
        sender.setAsyncMode(true);
        busy = false;
        if (settings != null) {
            MinecraftClient.getInstance().openScreen(settings);
            settings = null;
        }
    }

    private void failed(Exception failure) {
        LogManager.getLogger("ZSG Replay Viewer").warn("Playback mode switch failed", failure);
        error = "Mode switch failed; see latest.log";
        initialization = null;
        resume();
        changed.run();
    }

    void close() { closed = true; initialization = null; }
}
