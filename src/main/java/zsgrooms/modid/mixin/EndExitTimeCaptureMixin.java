package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.EndExitTimeCapture;

@Mixin(ServerPlayerEntity.class)
public abstract class EndExitTimeCaptureMixin {
    @Inject(method = "changeDimension", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V",
            ordinal = 0))
    private void zsgRooms$captureExitTime(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.world.getRegistryKey() == World.END && destination.getRegistryKey() == World.OVERWORLD) {
            EndExitTimeCapture.capture(player);
        }
    }
}
