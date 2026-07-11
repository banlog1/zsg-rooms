package zsgrooms.modid.mixin;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ui.ZsgInGameActions;

import java.util.Iterator;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin() {
        super(new LiteralText(""));
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void addZsgRaceButtons(CallbackInfo ci) {
        if (!ZsgInGameActions.hasActiveRoom()) {
            return;
        }

        removeVanillaQuitButton();

        int centerX = this.width / 2;
        int y = Math.min(this.height - 28, this.height / 4 + 120);
        int totalWidth = Math.min(204, this.width - 20);

        if (ZsgInGameActions.isSoloRoom()) {
            this.addButton(new ButtonWidget(centerX - totalWidth / 2, y, totalWidth, 20, new LiteralText("Return to Room"), button -> {
                ZsgInGameActions.returnToRoom(this.client);
            }));
        } else {
            int buttonWidth = (totalWidth - 8) / 2;
            this.addButton(new ButtonWidget(centerX - totalWidth / 2, y, buttonWidth, 20, new LiteralText("Forfeit"), button -> {
                ZsgInGameActions.forfeit(this.client);
            }));
            this.addButton(new ButtonWidget(centerX - totalWidth / 2 + buttonWidth + 8, y, buttonWidth, 20, new LiteralText("Seed Change"), button -> {
                ZsgInGameActions.requestSeedChange(this.client);
            }));
        }
    }

    private void removeVanillaQuitButton() {
        Iterator<AbstractButtonWidget> buttonIterator = this.buttons.iterator();
        while (buttonIterator.hasNext()) {
            AbstractButtonWidget button = buttonIterator.next();
            String label = button.getMessage().asString();
            if (label.contains("Save and Quit") || label.contains("Disconnect") || label.contains("menu.returnToMenu")) {
                buttonIterator.remove();
                this.children.remove((Element) button);
                return;
            }
        }
    }
}
