package zsgrooms.modid.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ui.RoomMenuScreen;
import zsgrooms.modid.ui.UpdateScreen;
import zsgrooms.modid.update.UpdateManager;
import zsgrooms.modid.update.UpdateRelease;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique private ButtonWidget zsgRooms$updateButton;
    @Unique private UpdateRelease zsgRooms$release;

    protected TitleScreenMixin() {
        super(new LiteralText(""));
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void addRoomButton(CallbackInfo ci) {
        int buttonWidth = Math.min(200, this.width - 20);
        int x = (this.width - buttonWidth) / 2;
        int y = Math.max(10, this.height / 4 + 24);
        this.addButton(new ButtonWidget(x, y, buttonWidth, 20, new LiteralText("ZSG Rooms"), button -> {
            this.client.openScreen(new RoomMenuScreen(this));
        }));

        int updateWidth = Math.min(170, this.width - 20);
        this.zsgRooms$updateButton = new ButtonWidget(this.width - updateWidth - 8, this.height - 28,
                updateWidth, 20, new LiteralText("ZSG Rooms update"), button -> {
            if (this.zsgRooms$release != null) {
                this.client.openScreen(new UpdateScreen(this, this.zsgRooms$release));
            }
        });
        this.zsgRooms$updateButton.visible = false;
        this.addButton(this.zsgRooms$updateButton);
        UpdateManager.checkForUpdates(release -> this.client.execute(() -> zsgRooms$showUpdate(release)));
    }

    @Unique
    private void zsgRooms$showUpdate(UpdateRelease release) {
        this.zsgRooms$release = release;
        if (this.zsgRooms$updateButton != null) {
            this.zsgRooms$updateButton.setMessage(new LiteralText("Update available: " + release.version));
            this.zsgRooms$updateButton.visible = true;
        }
    }
}
