package zsgrooms.modid.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.StructureSpawnProximity;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerSpawnMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void zsgRooms$standardizeWorldSpawn(
            MinecraftServer server,
            ServerWorld world,
            GameProfile profile,
            ServerPlayerInteractionManager interactionManager,
            CallbackInfo ci) {
        if ((!RngStandardization.isEnabled() && !StructureSpawnProximity.hasPreparedSpawn())
                || !World.OVERWORLD.equals(world.getRegistryKey())) {
            return;
        }
        BlockPos spawn = world.getSpawnPos();
        ((ServerPlayerEntity) (Object) this).refreshPositionAndAngles(spawn, 0.0F, 0.0F);
    }
}
