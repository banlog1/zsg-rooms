package zsgrooms.loadingtest.mixin;

import zsgrooms.modid.ui.SeedPresentationSmoke;

import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelLoadingScreen.class, priority = 500)
public abstract class LoadingCaptureMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void capture(MatrixStack matrices, int x, int y, float delta, CallbackInfo ci) {
        SeedPresentationSmoke.loadingFrame((LevelLoadingScreen) (Object) this);
    }
}
