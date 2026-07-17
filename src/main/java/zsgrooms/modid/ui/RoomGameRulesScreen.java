package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class RoomGameRulesScreen extends Screen {
    private final RoomSetupScreen parent;
    private boolean allowCheats;
    private boolean rngStandardization;
    private boolean boostedBarters;
    private boolean minimumBastionIron;
    private boolean removeBastionZombifiedPiglins;
    private boolean spawnNearFilterStructure;

    public RoomGameRulesScreen(RoomSetupScreen parent, boolean allowCheats, boolean rngStandardization,
            boolean boostedBarters, boolean minimumBastionIron, boolean removeBastionZombifiedPiglins,
            boolean spawnNearFilterStructure) {
        super(new LiteralText("Room Game Rules"));
        this.parent = parent;
        this.allowCheats = allowCheats;
        this.rngStandardization = rngStandardization;
        this.boostedBarters = boostedBarters;
        this.minimumBastionIron = minimumBastionIron;
        this.removeBastionZombifiedPiglins = removeBastionZombifiedPiglins;
        this.spawnNearFilterStructure = spawnNearFilterStructure;
    }

    @Override
    protected void init() {
        int panelX = (this.width - panelWidth()) / 2;
        int buttonX = panelX + 16;
        int buttonWidth = panelWidth() - 32;
        int y = listTop();
        int gap = rowGap();

        this.addButton(new ButtonWidget(buttonX, y, buttonWidth, 20, allowCheatsText(), button -> {
            this.allowCheats = !this.allowCheats;
            button.setMessage(allowCheatsText());
        }));
        this.addButton(new ButtonWidget(buttonX, y + gap, buttonWidth, 20, rngStandardizationText(), button -> {
            this.rngStandardization = !this.rngStandardization;
            button.setMessage(rngStandardizationText());
        }));
        this.addButton(new ButtonWidget(buttonX, y + gap * 2, buttonWidth, 20, boostedBartersText(), button -> {
            this.boostedBarters = !this.boostedBarters;
            button.setMessage(boostedBartersText());
        }));
        this.addButton(new ButtonWidget(buttonX, y + gap * 3, buttonWidth, 20, minimumBastionIronText(), button -> {
            this.minimumBastionIron = !this.minimumBastionIron;
            button.setMessage(minimumBastionIronText());
        }));
        this.addButton(new ButtonWidget(buttonX, y + gap * 4, buttonWidth, 20,
                removeBastionZombifiedPiglinsText(), button -> {
            this.removeBastionZombifiedPiglins = !this.removeBastionZombifiedPiglins;
            button.setMessage(removeBastionZombifiedPiglinsText());
        }));
        this.addButton(new ButtonWidget(buttonX, y + gap * 5, buttonWidth, 20,
                spawnNearFilterStructureText(), button -> {
            this.spawnNearFilterStructure = !this.spawnNearFilterStructure;
            button.setMessage(spawnNearFilterStructureText());
        }));
        this.addButton(new ButtonWidget(buttonX, actionY(), buttonWidth, 20, new LiteralText("Done"), button -> {
            saveAndClose();
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);
        int panelX = (this.width - panelWidth()) / 2;
        int panelY = panelY();
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + panelHeight(), 0xDD070707);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 28, 0xAA1A120C);
        fill(matrices, panelX, panelY + 28, panelX + panelWidth(), panelY + 29, 0xFF000000);
        drawCenteredString(matrices, this.textRenderer, "Room Game Rules", this.width / 2, panelY + 10, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    private void saveAndClose() {
        this.parent.setGameRules(this.allowCheats, this.rngStandardization, this.boostedBarters,
                this.minimumBastionIron, this.removeBastionZombifiedPiglins, this.spawnNearFilterStructure);
        this.client.openScreen(this.parent);
    }

    private int panelWidth() {
        return Math.min(420, this.width - 16);
    }

    private int panelHeight() {
        return Math.min(252, this.height - 12);
    }

    private int panelY() {
        return Math.max(6, (this.height - panelHeight()) / 2);
    }

    private int listTop() {
        return panelY() + 38;
    }

    private int actionY() {
        return panelY() + panelHeight() - 28;
    }

    private int rowGap() {
        int available = actionY() - listTop() - 20;
        return Math.max(18, Math.min(28, available / 5));
    }

    private LiteralText allowCheatsText() {
        return toggleText("Allow Cheats", this.allowCheats);
    }

    private LiteralText rngStandardizationText() {
        return toggleText("Standardize Race RNG", this.rngStandardization);
    }

    private LiteralText boostedBartersText() {
        return toggleText("Increase Piglin Barter Rates", this.boostedBarters);
    }

    private LiteralText minimumBastionIronText() {
        return toggleText("Guarantee 3 Iron in Bastion", this.minimumBastionIron);
    }

    private LiteralText removeBastionZombifiedPiglinsText() {
        return toggleText("Remove Zombified Piglins from Bastions", this.removeBastionZombifiedPiglins);
    }

    private LiteralText spawnNearFilterStructureText() {
        return toggleText("Spawn Near Filter Structure", this.spawnNearFilterStructure);
    }

    private LiteralText toggleText(String label, boolean enabled) {
        return new LiteralText(label + ": " + (enabled ? "On" : "Off"));
    }
}
