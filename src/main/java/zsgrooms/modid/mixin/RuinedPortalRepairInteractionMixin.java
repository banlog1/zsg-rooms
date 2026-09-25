package zsgrooms.modid.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.RuinedPortalChestRepair;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class RuinedPortalRepairInteractionMixin {
    @Shadow public ServerWorld world;

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
    private void zsgRooms$stopRepairOnMining(BlockPos pos, PlayerActionC2SPacket.Action action,
                                            Direction direction, int height, CallbackInfo ci) {
        RuinedPortalChestRepair.onPlayerInteraction(this.world, pos);
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void zsgRooms$stopRepairOnUse(ServerPlayerEntity player, World world, ItemStack stack,
                                         Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        RuinedPortalChestRepair.onPlayerInteraction(this.world, hit.getBlockPos());
    }
}
