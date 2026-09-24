package zsgrooms.modid.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.replay.ReplayPrototype;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ReplayChestInteractionMixin {
    @Inject(method = {"interactEntity", "interactEntityAtLocation", "interactItem"}, at = @At("HEAD"))
    private void zsgRooms$otherInteraction(CallbackInfoReturnable<ActionResult> cir) {
        ReplayPrototype.clearChestInteraction();
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void zsgRooms$chest(ClientPlayerEntity player, ClientWorld world, Hand hand, BlockHitResult hit,
                                CallbackInfoReturnable<ActionResult> cir) {
        ReplayPrototype.chestInteraction(world, hit.getBlockPos());
    }
}
