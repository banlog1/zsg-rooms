// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.OptionalDouble;

final class ReplayAnalysis extends RenderPhase {
    private static RenderLayer lines(int width) {
        return RenderLayer.of("zsg_entity_trail_" + width, VertexFormats.POSITION_COLOR,
            1, 4096, false, true, RenderLayer.MultiPhaseParameters.builder()
                    .texture(NO_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING)
                    .lineWidth(new LineWidth(OptionalDouble.of(width)))
                    .depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK).fog(NO_FOG).build(false));
    }
    private static final RenderLayer[] LINES = {lines(1), lines(2), lines(3), lines(4)};
    static final int MAX_PIGLIN_TRAILS = 128;
    static final int MAX_MOB_TRAILS = 128;
    static final int MAX_PLAYER_TRAILS = 32;
    boolean dragonTrail;
    boolean piglinTrail;
    boolean playerTrail;
    boolean piglinCounter;
    boolean runTimers = true;
    boolean renderDistance;
    boolean barteringLabels;
    final MobHighlights highlights = new MobHighlights();
    final MobSelection mobTrails = new MobSelection();
    final TrailStyle dragonStyle = new TrailStyle(TrailStyle.Color.CYAN);
    final TrailStyle piglinStyle = new TrailStyle(TrailStyle.Color.GOLD);
    final TrailStyle playerStyle = new TrailStyle(TrailStyle.Color.GREEN);
    final TrailStyle mobStyle = new TrailStyle(TrailStyle.Color.VIOLET);
    int piglins;
    boolean counterAvailable;
    private ClientWorld world;
    private int lastTime = -1;
    private long lastSample = Long.MIN_VALUE;
    private int lastOptions;
    private TrailIndex trailIndex;
    private long appliedRevision = -1;
    private int appliedTime = -1;
    private int appliedOptions;
    private final Set<UUID> historyEntities = new HashSet<>();
    private int historyDuration;
    final Map<UUID, TrailHistory> trails = new HashMap<>();
    final Map<UUID, TrailHistory> piglinTrails = new HashMap<>();
    final Map<UUID, TrailHistory> playerTrails = new HashMap<>();
    final Map<UUID, TrailHistory> mobPaths = new HashMap<>();

    ReplayAnalysis() { super("zsg_replay_analysis", () -> {}, () -> {}); }

    void clear() {
        trails.clear();
        piglinTrails.clear();
        playerTrails.clear();
        mobPaths.clear();
        historyEntities.clear();
        appliedRevision = -1;
        appliedTime = -1;
        lastTime = -1;
        lastSample = Long.MIN_VALUE;
        piglins = 0;
        counterAvailable = false;
    }

    void update(ReplayHandler handler) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (world != client.world) { clear(); world = client.world; }
        if (!dragonTrail) trails.clear();
        if (!piglinTrail) piglinTrails.clear();
        if (!playerTrail) playerTrails.clear();
        if (!mobTrails.active()) mobPaths.clear();
        if (!piglinCounter) { piglins = 0; counterAvailable = false; }
        if (world == null || !trailsEnabled() && !piglinCounter) return;
        if (!handler.getReplaySender().isAsyncMode()
                || handler.getReplaySender() instanceof FullReplaySender && ((FullReplaySender) handler.getReplaySender()).isHurrying()) {
            clear();
            return;
        }
        int time = handler.getReplaySender().currentTimeStamp();
        if (lastTime >= 0 && (time < lastTime || (long) time - lastTime > 1000)) clear();
        lastTime = time;
        int options = optionsKey();
        if (lastSample != Long.MIN_VALUE && time - lastSample < TrailHistory.INTERVAL && options == lastOptions) {
            restoreHistory(handler, time);
            return;
        }
        lastOptions = options;
        lastSample = time;
        PlayerEntity player = FarFollowController.recordedPlayer(client);
        List<Vec3d> pigs = piglinCounter && player != null ? new ArrayList<>() : null;
        Set<UUID> dragons = dragonTrail ? new HashSet<>() : null;
        Set<UUID> trackedPigs = piglinTrail && player != null ? new HashSet<>() : null;
        Set<UUID> players = new HashSet<>();
        List<Entity> mobs = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (!entity.isAlive()) continue;
            if (playerTrail && entity instanceof PlayerEntity && !(entity instanceof CameraEntity) && players.size() < MAX_PLAYER_TRAILS) {
                players.add(entity.getUuid());
                playerTrails.computeIfAbsent(entity.getUuid(), key -> new TrailHistory()).add(time,
                        entity.getX(), entity.getY(), entity.getZ(), playerStyle.duration());
            }
            if (mobTrails.active() && entity instanceof MobEntity
                    && mobTrails.selected(Registry.ENTITY_TYPE.getId(entity.getType()))) mobs.add(entity);
            if ((pigs != null || trackedPigs != null) && entity instanceof PiglinEntity && entity.squaredDistanceTo(player) <= 4096) {
                if (pigs != null) pigs.add(entity.getPos());
                UUID id = entity.getUuid();
                if (trackedPigs != null && (piglinTrails.containsKey(id) || piglinTrails.size() < MAX_PIGLIN_TRAILS)) {
                    trackedPigs.add(id);
                    piglinTrails.computeIfAbsent(id, key -> new TrailHistory()).add(time,
                            entity.getX(), entity.getY(), entity.getZ(), piglinStyle.duration());
                }
            }
            if (dragons != null && entity instanceof EnderDragonEntity && dragons.size() < 8) {
                UUID id = entity.getUuid();
                dragons.add(id);
                trails.computeIfAbsent(id, key -> new TrailHistory()).add(time,
                        entity.getX(), entity.getY(), entity.getZ(), dragonStyle.duration());
            }
        }
        if (dragons != null) trails.keySet().retainAll(dragons);
        if (trackedPigs != null) piglinTrails.keySet().retainAll(trackedPigs);
        else piglinTrails.clear();
        playerTrails.keySet().retainAll(players);
        Entity origin = client.getCameraEntity();
        if (origin != null) mobs.sort(Comparator.<Entity>comparingDouble(entity -> entity.squaredDistanceTo(origin))
                .thenComparingInt(Entity::getEntityId));
        Set<UUID> trackedMobs = new HashSet<>();
        for (Entity entity : mobs) {
            UUID id = entity.getUuid();
            if (trails.containsKey(id) || piglinTrails.containsKey(id)) continue;
            if (trackedMobs.size() == MAX_MOB_TRAILS) break;
            trackedMobs.add(id);
            mobPaths.computeIfAbsent(id, key -> new TrailHistory()).add(time,
                    entity.getX(), entity.getY(), entity.getZ(), mobStyle.duration());
        }
        mobPaths.keySet().retainAll(trackedMobs);
        counterAvailable = pigs != null;
        piglins = pigs == null ? 0 : PiglinClusters.largest(pigs);
        historyEntities.clear();
        historyEntities.addAll(trails.keySet());
        historyEntities.addAll(piglinTrails.keySet());
        historyEntities.addAll(playerTrails.keySet());
        historyEntities.addAll(mobPaths.keySet());
        historyDuration = Math.max(Math.max(dragonTrail ? dragonStyle.duration() : 0, piglinTrail ? piglinStyle.duration() : 0),
                Math.max(playerTrail ? playerStyle.duration() : 0, mobTrails.active() ? mobStyle.duration() : 0));
        restoreHistory(handler, time);
    }

    private void restoreHistory(ReplayHandler handler, int time) {
        if (!trailsEnabled()) return;
        if (trailIndex == null) trailIndex = new TrailIndex(handler.getReplayFile());
        trailIndex.request(time, historyEntities, Math.max(2000, historyDuration));
        int options = optionsKey();
        if (trailIndex.revision() == appliedRevision && time / TrailHistory.INTERVAL == appliedTime
                && options == appliedOptions) return;
        TrailIndex.Snapshot snapshot = trailIndex.snapshot(time, historyEntities, Math.max(2000, historyDuration));
        if (snapshot == null) return;
        appliedRevision = snapshot.revision;
        appliedTime = time / TrailHistory.INTERVAL;
        appliedOptions = options;
        restore(trails, snapshot.paths, time, dragonStyle);
        restore(piglinTrails, snapshot.paths, time, piglinStyle);
        restore(playerTrails, snapshot.paths, time, playerStyle);
        restore(mobPaths, snapshot.paths, time, mobStyle);
    }

    private static void restore(Map<UUID, TrailHistory> histories, Map<UUID, List<TrailPoint>> source,
                                int time, TrailStyle style) {
        for (Map.Entry<UUID, TrailHistory> entry : histories.entrySet()) {
            List<TrailPoint> samples = source.get(entry.getKey());
            if (samples != null) entry.getValue().replace(samples, time, style.duration());
        }
    }

    private boolean trailsEnabled() { return dragonTrail || piglinTrail || playerTrail || mobTrails.active(); }

    private int optionsKey() {
        int flags = (dragonTrail ? 1 : 0) | (piglinTrail ? 2 : 0) | (playerTrail ? 4 : 0)
                | (mobTrails.active() ? 8 : 0) | (piglinCounter ? 16 : 0);
        int key = 31 * flags + mobTrails.revision();
        key = 31 * key + dragonStyle.duration();
        key = 31 * key + piglinStyle.duration();
        key = 31 * key + playerStyle.duration();
        return 31 * key + mobStyle.duration();
    }

    String trailStatus() {
        return !trailsEnabled() ? "" : trailIndex == null ? "Preparing trail history..." : trailIndex.status;
    }

    void close() {
        clear();
        if (trailIndex != null) trailIndex.close();
        trailIndex = null;
    }

    void render(MatrixStack matrices, Camera camera) {
        if (dragonTrail) render(matrices, camera, trails, dragonStyle, 0);
        if (piglinTrail) render(matrices, camera, piglinTrails, piglinStyle, 0.15);
        if (playerTrail) render(matrices, camera, playerTrails, playerStyle, 0.15);
        if (mobTrails.active()) render(matrices, camera, mobPaths, mobStyle, 0.15);
    }

    private void render(MatrixStack matrices, Camera camera, Map<UUID, TrailHistory> histories, TrailStyle style, double height) {
        if (histories.isEmpty()) return;
        VertexConsumerProvider.Immediate buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = LINES[style.lineWidth() - 1];
        VertexConsumer vertices = buffers.getBuffer(layer);
        Matrix4f transform = matrices.peek().getModel();
        Vec3d origin = camera.getPos();
        for (TrailHistory trail : histories.values()) {
            TrailPoint previous = null;
            for (TrailPoint point : trail.points) {
                if (point.time > lastTime || (long) lastTime - point.time > style.duration()) continue;
                if (previous != null && !point.breakBefore) {
                    vertex(vertices, transform, origin, previous, style, lastTime, height);
                    vertex(vertices, transform, origin, point, style, lastTime, height);
                }
                previous = point;
            }
        }
        buffers.draw(layer);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3d origin, TrailPoint point, TrailStyle style, int time, double height) {
        int rgb = style.color.rgb;
        vertices.vertex(matrix, (float) (point.x - origin.x), (float) (point.y + height - origin.y), (float) (point.z - origin.z))
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, style.alpha(time, point.time)).next();
    }
}
