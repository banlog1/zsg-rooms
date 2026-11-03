// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/** Parent supplies current recorder-world identity and loading/seek state once per rendered replay frame. */
final class LocalSoundPlayback {
    private static final LocalSoundCursor cursor = new LocalSoundCursor();
    private static final SoundCategory[] CATEGORIES = SoundCategory.values();
    private LocalSoundPlayback() { }

    static void frame(LocalSoundTrack track, int time, int world, int entity, boolean paused, boolean blocked) {
        cursor.frame(track, time, world, entity, paused, blocked || !ReplayAudio.enabled() || ReplayAudio.muted(),
                LocalSoundPlayback::play);
    }

    static boolean covers(int channel, int entity) { return cursor.covers(channel, entity); }
    static boolean coversPlayerSound(int channel, int entity, net.minecraft.sound.SoundEvent sound) {
        return cursor.covers(channel, entity) && LocalSoundTrack.playerSoundSupported(channel, sound.getId().toString());
    }
    static void reset() { cursor.reset(); }

    private static void play(LocalSoundTrack.Event event) {
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(new Identifier(event.sound),
                CATEGORIES[event.category], event.volume, event.pitch, false, 0, SoundInstance.AttenuationType.LINEAR,
                event.x, event.y, event.z, false));
    }
}
