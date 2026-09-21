// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
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
    boolean dragonTrail;
    boolean piglinTrail;
    boolean piglinCounter;
    final TrailStyle dragonStyle = new TrailStyle(TrailStyle.Color.CYAN);
    final TrailStyle piglinStyle = new TrailStyle(TrailStyle.Color.GOLD);
    int piglins;
    boolean counterAvailable;
    private ClientWorld world;
    private int lastTime = -1;
    private long lastSample = Long.MIN_VALUE;
    final Map<UUID, TrailHistory> trails = new HashMap<>();
    final Map<UUID, TrailHistory> piglinTrails = new HashMap<>();

    ReplayAnalysis() { super("zsg_replay_analysis", () -> {}, () -> {}); }

    void clear() {
        trails.clear();
        piglinTrails.clear();
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
        if (!piglinCounter) { piglins = 0; counterAvailable = false; }
        if (world == null || !dragonTrail && !piglinTrail && !piglinCounter) return;
        if (!handler.getReplaySender().isAsyncMode()
                || handler.getReplaySender() instanceof FullReplaySender && ((FullReplaySender) handler.getReplaySender()).isHurrying()) {
            clear();
            return;
        }
        int time = handler.getReplaySender().currentTimeStamp();
        if (lastTime >= 0 && (time < lastTime || (long) time - lastTime > 1000)) clear();
        lastTime = time;
        if (lastSample != Long.MIN_VALUE && time - lastSample < TrailHistory.INTERVAL) return;
        lastSample = time;
        PlayerEntity player = FarFollowController.recordedPlayer(client);
        List<Vec3d> pigs = piglinCounter && player != null ? new ArrayList<>() : null;
        Set<UUID> dragons = dragonTrail ? new HashSet<>() : null;
        Set<UUID> trackedPigs = piglinTrail && player != null ? new HashSet<>() : null;
        for (Entity entity : world.getEntities()) {
            if (!entity.isAlive()) continue;
            if ((pigs != null || trackedPigs != null) && entity instanceof PiglinEntity && entity.squaredDistanceTo(player) <= 4096) {
                if (pigs != null) pigs.add(entity.getPos());
                UUID id = entity.getUuid();
                if (trackedPigs != null && (piglinTrails.containsKey(id) || piglinTrails.size() < MAX_PIGLIN_TRAILS)) {
                    trackedPigs.add(id);
                    piglinTrails.computeIfAbsent(id, key -> new TrailHistory()).add(time,
                            entity.getX(), entity.getY() + 0.15, entity.getZ(), piglinStyle.duration());
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
        counterAvailable = pigs != null;
        piglins = pigs == null ? 0 : PiglinClusters.largest(pigs);
    }

    void render(MatrixStack matrices, Camera camera) {
        if (dragonTrail) render(matrices, camera, trails, dragonStyle);
        if (piglinTrail) render(matrices, camera, piglinTrails, piglinStyle);
    }

    private void render(MatrixStack matrices, Camera camera, Map<UUID, TrailHistory> histories, TrailStyle style) {
        if (histories.isEmpty()) return;
        VertexConsumerProvider.Immediate buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = LINES[style.lineWidth() - 1];
        VertexConsumer vertices = buffers.getBuffer(layer);
        Matrix4f transform = matrices.peek().getModel();
        Vec3d origin = camera.getPos();
        for (TrailHistory trail : histories.values()) {
            TrailHistory.Point previous = null;
            for (TrailHistory.Point point : trail.points) {
                if ((long) lastTime - point.time > style.duration()) continue;
                if (previous != null) {
                    vertex(vertices, transform, origin, previous, style, lastTime);
                    vertex(vertices, transform, origin, point, style, lastTime);
                }
                previous = point;
            }
        }
        buffers.draw(layer);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3d origin, TrailHistory.Point point, TrailStyle style, int time) {
        int rgb = style.color.rgb;
        vertices.vertex(matrix, (float) (point.x - origin.x), (float) (point.y - origin.y), (float) (point.z - origin.z))
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, style.alpha(time, point.time)).next();
    }
}
