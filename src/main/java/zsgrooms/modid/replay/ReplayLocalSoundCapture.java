package zsgrooms.modid.replay;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;

/** Explicit local call sites only. Never observes the global sound engine or incoming sound packets. */
public final class ReplayLocalSoundCapture {
    private static volatile Binding active;
    private ReplayLocalSoundCapture() { }

    static void attach(ReplayLocalSoundTrack track, long originNanos, IntSupplier world, BooleanSupplier available) {
        active = new Binding(track, originNanos, world, available);
    }

    static void detach(ReplayLocalSoundTrack track) {
        Binding binding = active;
        if (binding != null && binding.track == track) active = null;
    }

    static void invalidate(ReplayLocalSoundTrack track) {
        Binding binding = active;
        if (binding != null && binding.track == track)
            track.context((System.nanoTime() - binding.origin) / 1000000, -1, -1, false);
    }

    public static void player(PlayerEntity player, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        Binding binding = active;
        if (binding == null) return;
        try {
            int channel = playerChannel(sound.getId().toString());
            if (channel != 0) capture(player, channel, sound.getId().toString(), category.ordinal(), volume, pitch,
                    player.getX(), player.getY(), player.getZ());
        } catch (RuntimeException | LinkageError error) { failed(binding, error); }
    }

    public static void world(PlayerEntity player, SoundEvent sound, SoundCategory category, float volume, float pitch,
                             double x, double y, double z, boolean bucket) {
        Binding binding = active;
        if (binding == null) return;
        try {
            capture(player, bucket ? ReplayLocalSoundTrack.BUCKET : ReplayLocalSoundTrack.PLACEMENT,
                    sound.getId().toString(), category.ordinal(), volume, pitch, x, y, z);
        } catch (RuntimeException | LinkageError error) { failed(binding, error); }
    }

    public static void mining(SoundEvent sound, SoundCategory category, float volume, float pitch, BlockPos pos) {
        Binding binding = active;
        if (binding == null) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            capture(client.player, ReplayLocalSoundTrack.MINING, sound.getId().toString(), category.ordinal(),
                    volume, pitch, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        } catch (RuntimeException | LinkageError error) { failed(binding, error); }
    }

    public static void worldEvent(ClientWorld world, PlayerEntity player, int event, BlockPos pos, int data) {
        Binding binding = active;
        if (binding == null || event != 2001) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!client.isOnThread() || player == null || player != client.player || world != client.world) return;
            // WorldRenderer's 2001 case, reached locally via Block.onBreak. Incoming packets have a null source.
            BlockState state = Block.getStateFromRawId(data);
            if (state.isAir()) return;
            BlockSoundGroup group = state.getSoundGroup();
            capture(player, ReplayLocalSoundTrack.MINING, group.getBreakSound().getId().toString(), SoundCategory.BLOCKS.ordinal(),
                    (group.getVolume() + 1) / 2, group.getPitch() * 0.8F,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        } catch (RuntimeException | LinkageError error) { failed(binding, error); }
    }

    static int playerChannel(String id) {
        if (!id.startsWith("minecraft:")) return 0;
        if (id.startsWith("minecraft:block.") && id.endsWith(".step")) return ReplayLocalSoundTrack.STEP;
        if (id.equals("minecraft:entity.player.big_fall") || id.equals("minecraft:entity.player.small_fall")
                || id.startsWith("minecraft:block.") && id.endsWith(".fall")) return ReplayLocalSoundTrack.FALL;
        if (id.startsWith("minecraft:entity.player.hurt") || id.equals("minecraft:entity.player.death")
                || id.equals("minecraft:enchant.thorns.hit") || id.equals("minecraft:item.shield.block")
                || id.equals("minecraft:item.shield.break")) return ReplayLocalSoundTrack.HURT;
        if (id.equals("minecraft:entity.generic.eat") || id.equals("minecraft:entity.generic.drink")
                || id.equals("minecraft:item.honey_bottle.drink")) return ReplayLocalSoundTrack.CONSUMPTION;
        if (id.equals("minecraft:item.bucket.fill") || id.equals("minecraft:item.bucket.fill_lava")) return ReplayLocalSoundTrack.BUCKET;
        return 0;
    }

    private static void capture(PlayerEntity player, int channel, String sound, int category, float volume, float pitch,
                                double x, double y, double z) {
        Binding binding = active;
        if (binding == null) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!client.isOnThread() || player == null || player != client.player || player.world != client.world
                    || !binding.available.getAsBoolean()) return;
            long time = (System.nanoTime() - binding.origin) / 1000000;
            int world = binding.world.getAsInt();
            if (binding.track.context(time, world, player.getEntityId(), true))
                binding.track.offer(time, world, player.getEntityId(), channel, category, sound, volume, pitch, x, y, z);
            if (!binding.track.accepting()) detach(binding.track);
        } catch (RuntimeException | LinkageError error) { failed(binding, error); }
    }

    private static void failed(Binding binding, Throwable error) {
        binding.track.fail(error.getClass().getSimpleName());
        detach(binding.track);
    }

    private static final class Binding {
        final ReplayLocalSoundTrack track;
        final long origin;
        final IntSupplier world;
        final BooleanSupplier available;
        Binding(ReplayLocalSoundTrack track, long origin, IntSupplier world, BooleanSupplier available) {
            this.track = track; this.origin = origin; this.world = world; this.available = available;
        }
    }
}
