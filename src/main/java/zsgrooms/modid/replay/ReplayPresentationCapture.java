package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.CrossbowItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Render-thread observer. Disabled capture is a single null check, with no allocations. */
public final class ReplayPresentationCapture {
    public interface Context {
        long replayNanos();
        int worldIndex();
        int entityId();
        boolean available();
    }

    private static volatile Capture active;
    private ReplayPresentationCapture() { }

    public static void start(ReplayPresentationTrack track, Context context) {
        Objects.requireNonNull(track, "track"); Objects.requireNonNull(context, "context");
        Capture previous = active;
        active = null;
        if (previous != null) previous.close();
        if (track.accepting()) active = new Capture(track, context);
    }

    public static void stop(ReplayPresentationTrack track) {
        Capture capture = active;
        if (capture != null && capture.track == track) { active = null; capture.close(); }
    }

    public static void invalidate() {
        Capture capture = active;
        if (capture != null) capture.boundary.set(true);
    }

    public static void beginFrame(float delta) {
        Capture capture = active;
        if (capture != null) capture.begin(delta);
    }

    /** RETURN hook observes the effective FOV, including compatible zoom mods' getFov changes. */
    public static void fov(boolean world, double fov, float zoom, float zoomX, float zoomY) {
        Capture capture = active;
        if (capture != null) capture.fov(world, fov, zoom, zoomX, zoomY);
    }

    public static void hands(ReplayPresentationHeldAccess renderer, ClientPlayerEntity player, float delta) {
        Capture capture = active;
        if (capture != null) capture.hands(renderer, player, delta);
    }

    public static void endFrame() {
        Capture capture = active;
        if (capture != null) capture.end();
    }

    private static final class Capture {
        final ReplayPresentationTrack track;
        final Context context;
        final AtomicBoolean boundary = new AtomicBoolean();
        final PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer(512, ReplayPresentationTrack.MAX_FRAME));
        final ReplayDetailedCapture.CachedItem mainCache = new ReplayDetailedCapture.CachedItem();
        final ReplayDetailedCapture.CachedItem offCache = new ReplayDetailedCapture.CachedItem();
        final ReplayDetailedCapture.CachedItem activeCache = new ReplayDetailedCapture.CachedItem();
        long lastBucket = -1;
        int lastTime = -1, time, world, entity, flags;
        boolean collecting, worldFovSeen, handFovSeen, closed, invalid = true;
        double worldFov, handFov;
        float worldZoom, worldZoomX, worldZoomY, handZoom, handZoomX, handZoomY;
        float bobPhase, bobStride, pitch, swayPitch, swayYaw, swing, mainEquip, offEquip, useRemaining;
        ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY, activeItem = ItemStack.EMPTY;

        Capture(ReplayPresentationTrack track, Context context) { this.track = track; this.context = context; }

        synchronized void begin(float delta) {
            collecting = false;
            if (closed || !track.accepting()) return;
            try {
                long nanos = context.replayNanos();
                if (nanos < 0 || nanos / 1000000 > Integer.MAX_VALUE) return;
                int now = (int) (nanos / 1000000);
                if (now <= lastTime) return;
                MinecraftClient client = MinecraftClient.getInstance();
                ClientPlayerEntity player = client.player;
                int nextWorld = context.worldIndex(), nextEntity = context.entityId();
                boolean available = context.available() && client.world != null && player != null
                        && player.world == client.world && player.getEntityId() == nextEntity
                        && client.getCameraEntity() == player && client.options.perspective == 0
                        && !(client.currentScreen instanceof DownloadingTerrainScreen)
                        && !(client.currentScreen instanceof LevelLoadingScreen);
                boolean discontinuity = boundary.getAndSet(false) || world != nextWorld || entity != nextEntity;
                if (!available || discontinuity) {
                    if (!invalid || discontinuity) gap(now, nextWorld, nextEntity);
                    invalid = true;
                    if (!available || discontinuity) return;
                }
                long bucket = nanos / 1000000000 * 60 + nanos % 1000000000 * 60 / 1000000000;
                if (!invalid && bucket == lastBucket) return;
                lastBucket = bucket;
                time = now; world = nextWorld; entity = nextEntity;
                flags = ReplayPresentationTrack.AVAILABLE;
                if (client.options.bobView) flags |= ReplayPresentationTrack.BOB;
                float speed = player.horizontalSpeed - player.prevHorizontalSpeed;
                bobPhase = -(player.horizontalSpeed + speed * delta);
                bobStride = MathHelper.lerp(delta, player.prevStrideDistance, player.strideDistance);
                worldFovSeen = handFovSeen = false;
                worldZoom = handZoom = 1;
                worldZoomX = worldZoomY = handZoomX = handZoomY = 0;
                handFov = 70;
                pitch = swayPitch = swayYaw = swing = mainEquip = offEquip = useRemaining = 0;
                main = off = activeItem = ItemStack.EMPTY;
                collecting = true;
            } catch (RuntimeException ignored) { boundary.set(true); }
        }

        synchronized void fov(boolean world, double value, float zoom, float x, float y) {
            if (!collecting || closed) return;
            if (!Double.isFinite(value) || value <= 0 || value >= 360 || !Float.isFinite(zoom) || zoom <= 0 || zoom > 1024
                    || !Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > 1024 || Math.abs(y) > 1024) {
                collecting = false; boundary.set(true); return;
            }
            if (world) { worldFov = value; worldZoom = zoom; worldZoomX = x; worldZoomY = y; worldFovSeen = true; }
            else { handFov = value; handZoom = zoom; handZoomX = x; handZoomY = y; handFovSeen = true; }
        }

        synchronized void hands(ReplayPresentationHeldAccess renderer, ClientPlayerEntity player, float delta) {
            if (!collecting || closed || player.getEntityId() != entity) return;
            try {
                flags |= ReplayPresentationTrack.HANDS;
                if (player.isUsingItem()) flags |= ReplayPresentationTrack.USING;
                if (player.isUsingRiptide()) flags |= ReplayPresentationTrack.RIPTIDE;
                if (player.getMainArm() == Arm.LEFT) flags |= ReplayPresentationTrack.LEFT_MAIN;
                if (player.preferredHand == Hand.OFF_HAND) flags |= ReplayPresentationTrack.SWING_OFF;
                if (player.getActiveHand() == Hand.OFF_HAND) flags |= ReplayPresentationTrack.ACTIVE_OFF;
                pitch = MathHelper.lerp(delta, player.prevPitch, player.pitch);
                swayPitch = (player.getPitch(delta) - MathHelper.lerp(delta, player.lastRenderPitch, player.renderPitch)) * .1F;
                swayYaw = (player.getYaw(delta) - MathHelper.lerp(delta, player.lastRenderYaw, player.renderYaw)) * .1F;
                swing = player.getHandSwingProgress(delta);
                mainEquip = 1 - MathHelper.lerp(delta, renderer.zsgPresentation$previousMainEquip(), renderer.zsgPresentation$mainEquip());
                offEquip = 1 - MathHelper.lerp(delta, renderer.zsgPresentation$previousOffEquip(), renderer.zsgPresentation$offEquip());
                useRemaining = Math.max(0, player.getItemUseTimeLeft() - delta + 1);
                main = renderer.zsgPresentation$mainHand(); off = renderer.zsgPresentation$offHand();
                activeItem = player.getActiveItem();
                boolean showMain = true, showOff = true;
                if (player.isUsingItem()) {
                    if (activeItem.getItem() == Items.BOW || activeItem.getItem() == Items.CROSSBOW) {
                        showMain = player.getActiveHand() == Hand.MAIN_HAND; showOff = !showMain;
                    }
                    if (player.getActiveHand() == Hand.MAIN_HAND && charged(player.getOffHandStack())) showOff = false;
                } else {
                    if (charged(player.getMainHandStack())) showOff = !showMain;
                    if (charged(player.getOffHandStack())) { showMain = !player.getMainHandStack().isEmpty(); showOff = !showMain; }
                }
                if (showMain) flags |= ReplayPresentationTrack.SHOW_MAIN;
                if (showOff) flags |= ReplayPresentationTrack.SHOW_OFF;
            } catch (RuntimeException ignored) { collecting = false; boundary.set(true); }
        }

        private static boolean charged(ItemStack stack) {
            return stack.getItem() == Items.CROSSBOW && CrossbowItem.isCharged(stack);
        }

        synchronized void end() {
            if (!collecting || closed) return;
            collecting = false;
            try {
                if (!worldFovSeen || (flags & ReplayPresentationTrack.HANDS) != 0 && !handFovSeen
                        || !context.available() || context.worldIndex() != world || context.entityId() != entity
                        || boundary.get()) { gap(time, world, entity); return; }
                bytes.clear();
                bytes.writeInt(flags);
                bytes.writeDouble(worldFov); bytes.writeDouble(handFov);
                bytes.writeFloat(worldZoom); bytes.writeFloat(worldZoomX); bytes.writeFloat(worldZoomY);
                bytes.writeFloat(handZoom); bytes.writeFloat(handZoomX); bytes.writeFloat(handZoomY);
                bytes.writeFloat(bobPhase); bytes.writeFloat(bobStride);
                bytes.writeFloat(pitch); bytes.writeFloat(swayPitch); bytes.writeFloat(swayYaw);
                bytes.writeFloat(swing); bytes.writeFloat(mainEquip); bytes.writeFloat(offEquip); bytes.writeFloat(useRemaining);
                mainCache.write(bytes, main); offCache.write(bytes, off); activeCache.write(bytes, activeItem);
                byte[] payload = new byte[bytes.writerIndex()];
                bytes.getBytes(0, payload);
                track.offer(time, world, entity, payload);
                lastTime = time;
                invalid = false;
            } catch (RuntimeException ignored) { gap(time, world, entity); }
            finally { main = off = activeItem = ItemStack.EMPTY; }
        }

        private void gap(int now, int nextWorld, int nextEntity) {
            if (now > lastTime) {
                track.offer(now, Math.max(-1, nextWorld), Math.max(-1, nextEntity), new byte[]{0, 0, 0, ReplayPresentationTrack.GAP});
                lastTime = now;
            }
            world = nextWorld; entity = nextEntity; invalid = true;
        }

        synchronized void close() {
            if (closed) return;
            closed = true; collecting = false; bytes.release();
            main = off = activeItem = ItemStack.EMPTY;
        }
    }
}
