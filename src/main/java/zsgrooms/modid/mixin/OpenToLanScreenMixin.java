package zsgrooms.modid.mixin;

import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ui.ZsgInGameActions;

@Mixin(OpenToLanScreen.class)
public abstract class OpenToLanScreenMixin extends Screen {
    @Shadow private ButtonWidget buttonAllowCommands;
    @Shadow private boolean allowCommands;

    protected OpenToLanScreenMixin() {
        super(new LiteralText(""));
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void zsgRooms$disableCheatsForRoom(CallbackInfo ci) {
        enforceRoomCheatRule();
        if (ZsgInGameActions.activeRoomForbidsCheats() && this.buttonAllowCommands != null) {
            this.buttonAllowCommands.active = false;
        }
    }

    @Inject(method = "method_19851", at = @At("HEAD"))
    private void zsgRooms$enforceCheatsWhenOpeningLan(ButtonWidget button, CallbackInfo ci) {
        enforceRoomCheatRule();
    }

    private void enforceRoomCheatRule() {
        if (ZsgInGameActions.activeRoomForbidsCheats()) {
            this.allowCommands = false;
        }
    }
}
