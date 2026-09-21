package zsgrooms.modid.mixin;

import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ui.RoomLoadingArtwork;
import zsgrooms.modid.ui.LoadingProgressPosition;
import zsgrooms.modid.ui.RoomUiPreferences;

@Mixin(LevelLoadingScreen.class)
public abstract class RoomLoadingScreenMixin extends Screen implements RoomLoadingArtwork.ScreenState {
    @Shadow @Final private WorldGenerationProgressTracker progressProvider;
    @Unique private RoomLoadingArtwork zsgRooms$artwork;

    protected RoomLoadingScreenMixin(Text title) { super(title); }

    @Override
    public boolean zsgRooms$hasLoadingArtwork() {
        return zsgRooms$artwork != null && zsgRooms$artwork.available(client);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void zsgRooms$captureArtwork(WorldGenerationProgressTracker tracker, CallbackInfo ci) {
        zsgRooms$artwork = RoomLoadingArtwork.capture();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void zsgRooms$drawArtwork(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (zsgRooms$artwork == null || !zsgRooms$artwork.available(client)) return;
        // Run vanilla/WorldPreview first, preserving preview initialization and narrator updates.
        zsgRooms$artwork.render(matrices, client, width, height);
        LoadingProgressPosition position = RoomUiPreferences.getLoadingProgressPosition();
        int size = progressProvider.getSize() * 2;
        int x = position.mapX(width, size);
        LevelLoadingScreen.drawChunkMap(matrices, progressProvider, x, position.mapY(height, size), 2, 0);
        String progress = MathHelper.clamp(progressProvider.getProgressPercentage(), 0, 100) + "%";
        drawCenteredString(matrices, textRenderer, progress, x, position.textY(height, size), 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // WorldPreview's menu still exists, but must not receive clicks behind the artwork.
        if (zsgRooms$hasLoadingArtwork()) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
