package zsgrooms.modid.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.RoomCommandPermissions;

@Mixin(PlayerManager.class)
public abstract class RoomCommandPermissionsMixin {
    @Shadow public abstract MinecraftServer getServer();

    @Inject(method = "isOperator", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$denyOperator(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (RoomCommandPermissions.forbidsCheats(getServer())) cir.setReturnValue(false);
    }

    @ModifyVariable(method = "setCheatsAllowed", at = @At("HEAD"), argsOnly = true)
    private boolean zsgRooms$roomCheatRule(boolean requested) {
        return RoomCommandPermissions.allowed(getServer(), requested);
    }
}
