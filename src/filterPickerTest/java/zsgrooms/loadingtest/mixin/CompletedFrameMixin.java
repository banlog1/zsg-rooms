package zsgrooms.loadingtest.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ui.SeedPresentationSmoke;

@Mixin(Window.class)
public abstract class CompletedFrameMixin {
    @Inject(method = "swapBuffers", at = @At("HEAD"))
    private void capture(CallbackInfo ci) {
        SeedPresentationSmoke.completedFrame();
    }
}
