package zsgrooms.modid.mixin;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
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

        AbstractButtonWidget quitSlot = removeVanillaQuitButton();
        int availableWidth = Math.max(80, this.width - 20);
        int totalWidth = Math.min(quitSlot == null ? 204 : quitSlot.getWidth(), availableWidth);
        int left = (this.width - totalWidth) / 2;
        int y = quitSlot == null ? Math.min(this.height - 24, this.height / 4 + 104) : quitSlot.y;
        int gap = totalWidth < 180 ? 4 : 8;
        int buttonWidth = (totalWidth - gap) / 2;
        boolean narrow = buttonWidth < 86;

        if (ZsgInGameActions.isSoloRoom()) {
            this.addButton(new ButtonWidget(left, y, buttonWidth, 20,
                    new LiteralText(narrow ? "Room" : "Return to Room"), button -> {
                ZsgInGameActions.returnToRoom(this.client);
            }));
            this.addButton(new ButtonWidget(left + buttonWidth + gap, y, buttonWidth, 20,
                    new LiteralText(narrow ? "New Seed" : "Seed Change"), button -> {
                ZsgInGameActions.requestSeedChange(this.client);
            }));
        } else {
            this.addButton(new ButtonWidget(left, y, buttonWidth, 20, new LiteralText("Forfeit"), button -> {
                ZsgInGameActions.forfeit(this.client);
            }));
            this.addButton(new ButtonWidget(left + buttonWidth + gap, y, buttonWidth, 20,
                    new LiteralText(narrow ? "New Seed" : "Seed Change"), button -> {
                ZsgInGameActions.requestSeedChange(this.client);
            }));
        }
    }

    private AbstractButtonWidget removeVanillaQuitButton() {
        Iterator<AbstractButtonWidget> buttonIterator = this.buttons.iterator();
        while (buttonIterator.hasNext()) {
            AbstractButtonWidget button = buttonIterator.next();
            Text message = button.getMessage();
            String label = message.getString();
            boolean returnToMenu = message instanceof TranslatableText
                    && "menu.returnToMenu".equals(((TranslatableText) message).getKey());
            if (returnToMenu || label.contains("Save and Quit") || label.contains("Disconnect")) {
                buttonIterator.remove();
                this.children.remove((Element) button);
                return button;
            }
        }
        return null;
    }
}
