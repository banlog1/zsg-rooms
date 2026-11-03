// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

/** Per-render bridge. The owner must clear it at seek start and when leaving Faithful Follow. */
public final class PresentationPlayback {
    private static final PresentationState blended = new PresentationState();
    private static PresentationState current, decodedBefore, decodedAfter;
    private static PresentationTrack.Frame before, after;
    private static PresentationTrack bound;
    private static ClientWorld world;
    private static Entity camera;
    private static boolean renderingHand;
    private static long refreshed;

    private PresentationPlayback() { }

    public static void update(PresentationTrack track, double replayMillis, int worldIndex, int entity,
                              int intervalStart, int intervalEnd, boolean enabled) {
        current = null;
        if (!enabled || track == null) { clear(); return; }
        if (bound != track) { clear(); bound = track; }
        PresentationTrack.Pair pair = track.at(replayMillis, worldIndex, entity, intervalStart, intervalEnd);
        if (pair == null) return;
        if (pair.before != before) {
            decodedBefore = pair.before == after ? decodedAfter : PresentationState.decode(pair.before.payload);
            before = pair.before;
        }
        if (pair.after != after) {
            after = pair.after;
            decodedAfter = after == null ? null : PresentationState.decode(after.payload);
        }
        if (decodedBefore == null || pair.fraction != 0 && decodedAfter == null) return;
        PresentationState.interpolate(decodedBefore, pair.fraction == 0 ? decodedBefore : decodedAfter,
                pair.fraction, blended);
        MinecraftClient client = MinecraftClient.getInstance();
        world = client.world; camera = client.getCameraEntity();
        refreshed = System.nanoTime();
        current = blended;
    }

    public static PresentationState current() {
        PresentationState state = current;
        if (state == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        return ReplayViewer.faithfulActive() && client.world == world && world != null
                && client.getCameraEntity() == camera && camera != null && client.options.perspective == 0
                && System.nanoTime() - refreshed <= 250000000L ? state : null;
    }

    /** Getter redirects are live only inside our invocation of vanilla's first-person item renderer. */
    public static PresentationState handState() { return renderingHand ? current() : null; }
    public static void beginHand() { renderingHand = true; }
    public static void endHand() { renderingHand = false; }

    public static void clear() {
        current = null; renderingHand = false; bound = null;
        before = after = null; decodedBefore = decodedAfter = null; world = null; camera = null;
    }
}
