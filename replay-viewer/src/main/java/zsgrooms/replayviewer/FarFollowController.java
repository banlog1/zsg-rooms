// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.camera.CameraController;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RayTraceContext;

final class FarFollowController implements CameraController {
    static final class Distance {
        double blocks = 8.0;
        void change(double delta) { blocks = Math.max(2.0, Math.min(32.0, blocks + delta)); }
    }

    private final MinecraftClient client;
    private final CameraEntity camera;
    private final Distance distance;

    FarFollowController(MinecraftClient client, CameraEntity camera, Distance distance) {
        this.client = client;
        this.camera = camera;
        this.distance = distance;
    }

    static PlayerEntity recordedPlayer(MinecraftClient client) {
        if (client.world == null) return null;
        if (ReplayViewer.recordedUuid() != null) return client.world.getPlayerByUuid(ReplayViewer.recordedUuid());
        PlayerEntity target = null;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player instanceof CameraEntity) continue;
            if (target != null) return null;
            target = player;
        }
        return target;
    }

    @Override
    public void update(float elapsed) {
        PlayerEntity target = recordedPlayer(client);
        if (target == null || camera.world != client.world) return;
        float delta = client.getTickDelta();
        Vec3d aim = target.getCameraPosVec(delta);
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(delta, target.prevYaw, target.yaw));
        Vec3d desired = aim.add(Math.sin(yaw) * distance.blocks, 3.0, -Math.cos(yaw) * distance.blocks);
        HitResult hit = client.world.rayTrace(new RayTraceContext(aim, desired, RayTraceContext.ShapeType.COLLIDER,
                RayTraceContext.FluidHandling.NONE, target));
        Vec3d position = hit.getType() == HitResult.Type.BLOCK
                ? hit.getPos().add(aim.subtract(hit.getPos()).normalize().multiply(0.3)) : desired;
        Vec3d direction = aim.subtract(position);
        camera.setCameraPosition(position.x, position.y, position.z);
        camera.setCameraRotation((float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z))), 0);
    }

    @Override public void increaseSpeed() { distance.change(1); }
    @Override public void decreaseSpeed() { distance.change(-1); }
}
