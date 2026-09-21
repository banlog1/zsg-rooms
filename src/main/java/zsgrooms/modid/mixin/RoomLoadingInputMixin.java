package zsgrooms.modid.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.ui.RoomLoadingArtwork;

@Mixin(Screen.class)
public abstract class RoomLoadingInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$blockHiddenPreviewControls(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof RoomLoadingArtwork.ScreenState
                && ((RoomLoadingArtwork.ScreenState) (Object) this).zsgRooms$hasLoadingArtwork()) {
            cir.setReturnValue(true);
        }
    }
}
