// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.IOException;
import java.util.function.Consumer;

/** Normal forward play only. Discontinuities establish a new cursor without replaying historical audio. */
final class LocalSoundCursor {
    private LocalSoundTrack track;
    private int time = -1, world = -1, entity = -1, slot;
    private boolean blocked = true;
    private int mask;
    private LocalSoundTrack.Event pending;

    void frame(LocalSoundTrack next, int now, int world, int entity, boolean paused, boolean blocked,
               Consumer<LocalSoundTrack.Event> play) {
        mask = next == null ? 0 : next.coverage(now, world, entity);
        try {
            if (next != track || now < time || world != this.world || entity != this.entity || blocked || this.blocked
                    || now - (long) time > 250) {
                slot = next == null ? 0 : next.after(now);
                pending = null;
            } else if (!paused && next != null) {
                int played = 0;
                while (slot < next.eventCount()) {
                    LocalSoundTrack.Event event = pending == null ? next.event(slot) : pending;
                    pending = event;
                    if (event.time > now) break;
                    slot++;
                    pending = null;
                    if (event.time > time && event.world == world && event.entity == entity
                            && (next.coverage(event.time, world, entity) & event.channel) != 0) {
                        if (++played > 128) break;
                        play.accept(event);
                    }
                }
                // A malicious event burst must never produce deferred catch-up audio.
                if (played > 128) { slot = next.after(now); pending = null; }
            } else if (paused && next != null) { slot = next.after(now); pending = null; }
        } catch (IOException error) { mask = 0; }
        this.track = next; this.time = now; this.world = world; this.entity = entity; this.blocked = blocked;
        if (blocked) mask = 0;
    }

    boolean covers(int channel, int entity) { return this.entity == entity && (mask & channel) != 0; }
    void reset() { track = null; time = -1; world = entity = -1; blocked = true; mask = 0; pending = null; }
}
