package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class RoomGameRulesScreen extends Screen {
    private final RoomSetupScreen parent;
    private RoomRulePreset preset;
    private ButtonWidget presetButton;
    private ButtonWidget allowCheatsButton;
    private ButtonWidget rngStandardizationButton;
    private ButtonWidget boostedBartersButton;
    private ButtonWidget minimumBastionIronButton;
    private ButtonWidget removeBastionZombifiedPiglinsButton;
    private ButtonWidget spawnNearFilterStructureButton;
    private ButtonWidget minimumNearbyAnimalsButton;
    private boolean allowCheats;
    private boolean rngStandardization;
    private boolean boostedBarters;
    private boolean minimumBastionIron;
    private boolean removeBastionZombifiedPiglins;
    private boolean spawnNearFilterStructure;
    private boolean minimumNearbyAnimals;

    public RoomGameRulesScreen(RoomSetupScreen parent, boolean allowCheats, boolean rngStandardization,
            boolean boostedBarters, boolean minimumBastionIron, boolean removeBastionZombifiedPiglins,
            boolean spawnNearFilterStructure, boolean minimumNearbyAnimals, RoomRulePreset preset) {
        super(new LiteralText("Room Game Rules"));
        this.parent = parent;
        this.allowCheats = allowCheats;
        this.rngStandardization = rngStandardization;
        this.boostedBarters = boostedBarters;
        this.minimumBastionIron = minimumBastionIron;
        this.removeBastionZombifiedPiglins = removeBastionZombifiedPiglins;
        this.spawnNearFilterStructure = spawnNearFilterStructure;
        this.minimumNearbyAnimals = minimumNearbyAnimals;
        this.preset = preset == null ? RoomRulePreset.CUSTOM : preset;
        applyPreset();
    }

    @Override
    protected void init() {
        int panelX = (this.width - panelWidth()) / 2;
        int buttonX = panelX + 16;
        int buttonWidth = panelWidth() - 32;
        int y = listTop();
        int gap = rowGap();
        int buttonHeight = ruleButtonHeight();

        this.presetButton = new ButtonWidget(buttonX, y, buttonWidth, buttonHeight, presetText(), button -> {
            this.preset = this.preset.next();
            applyPreset();
            refreshButtonLabels();
        });
        this.addButton(this.presetButton);
        this.allowCheatsButton = new ButtonWidget(buttonX, y + gap, buttonWidth, buttonHeight, allowCheatsText(), button -> {
            markCustom();
            this.allowCheats = !this.allowCheats;
            button.setMessage(allowCheatsText());
        });
        this.addButton(this.allowCheatsButton);
        this.rngStandardizationButton = new ButtonWidget(buttonX, y + gap * 2, buttonWidth, buttonHeight, rngStandardizationText(), button -> {
            markCustom();
            this.rngStandardization = !this.rngStandardization;
            button.setMessage(rngStandardizationText());
        });
        this.addButton(this.rngStandardizationButton);
        this.boostedBartersButton = new ButtonWidget(buttonX, y + gap * 3, buttonWidth, buttonHeight, boostedBartersText(), button -> {
            markCustom();
            this.boostedBarters = !this.boostedBarters;
            button.setMessage(boostedBartersText());
        });
        this.addButton(this.boostedBartersButton);
        this.minimumBastionIronButton = new ButtonWidget(buttonX, y + gap * 4, buttonWidth, buttonHeight, minimumBastionIronText(), button -> {
            markCustom();
            this.minimumBastionIron = !this.minimumBastionIron;
            button.setMessage(minimumBastionIronText());
        });
        this.addButton(this.minimumBastionIronButton);
        this.removeBastionZombifiedPiglinsButton = new ButtonWidget(buttonX, y + gap * 5, buttonWidth, buttonHeight,
                removeBastionZombifiedPiglinsText(), button -> {
            markCustom();
            this.removeBastionZombifiedPiglins = !this.removeBastionZombifiedPiglins;
            button.setMessage(removeBastionZombifiedPiglinsText());
        });
        this.addButton(this.removeBastionZombifiedPiglinsButton);
        this.spawnNearFilterStructureButton = new ButtonWidget(buttonX, y + gap * 6, buttonWidth, buttonHeight,
                spawnNearFilterStructureText(), button -> {
            markCustom();
            this.spawnNearFilterStructure = !this.spawnNearFilterStructure;
            button.setMessage(spawnNearFilterStructureText());
        });
        this.addButton(this.spawnNearFilterStructureButton);
        this.minimumNearbyAnimalsButton = new ButtonWidget(buttonX, y + gap * 7, buttonWidth, buttonHeight,
                minimumNearbyAnimalsText(), button -> {
            markCustom();
            this.minimumNearbyAnimals = !this.minimumNearbyAnimals;
            button.setMessage(minimumNearbyAnimalsText());
        });
        this.addButton(this.minimumNearbyAnimalsButton);
        this.addButton(new ButtonWidget(buttonX, actionY(), buttonWidth, buttonHeight, new LiteralText("Done"), button -> {
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
                this.minimumBastionIron, this.removeBastionZombifiedPiglins, this.spawnNearFilterStructure,
                this.minimumNearbyAnimals, this.preset);
        this.client.openScreen(this.parent);
    }

    private int panelWidth() {
        return Math.min(420, this.width - 16);
    }

    private int panelHeight() {
        return Math.min(280, this.height - 12);
    }

    private int panelY() {
        return Math.max(6, (this.height - panelHeight()) / 2);
    }

    private int listTop() {
        return panelY() + (isCompact() ? 30 : 38);
    }

    private int actionY() {
        return panelY() + panelHeight() - (this.height < 200 ? 17 : isCompact() ? 20 : 28);
    }

    private int rowGap() {
        int buttonHeight = ruleButtonHeight();
        int available = actionY() - listTop() - buttonHeight;
        return Math.max(buttonHeight, Math.min(26, available / 7));
    }

    private int ruleButtonHeight() {
        return this.height < 200 ? 14 : isCompact() ? 16 : 20;
    }

    private boolean isCompact() {
        return this.height < 240;
    }

    private LiteralText allowCheatsText() {
        return toggleText("Allow Cheats", this.allowCheats);
    }

    private LiteralText presetText() {
        return new LiteralText("Preset: " + this.preset.getLabel());
    }

    private void applyPreset() {
        if (this.preset.isCustom()) {
            return;
        }
        this.allowCheats = this.preset.allowsCheats();
        this.rngStandardization = this.preset.standardizesRng();
        this.boostedBarters = this.preset.boostsBarters();
        this.minimumBastionIron = this.preset.guaranteesBastionIron();
        this.removeBastionZombifiedPiglins = this.preset.removesBastionZombifiedPiglins();
        this.spawnNearFilterStructure = this.preset.spawnsNearFilterStructure();
        this.minimumNearbyAnimals = this.preset.guaranteesNearbyAnimals();
    }

    private void markCustom() {
        this.preset = RoomRulePreset.CUSTOM;
        if (this.presetButton != null) {
            this.presetButton.setMessage(presetText());
        }
    }

    private void refreshButtonLabels() {
        this.presetButton.setMessage(presetText());
        this.allowCheatsButton.setMessage(allowCheatsText());
        this.rngStandardizationButton.setMessage(rngStandardizationText());
        this.boostedBartersButton.setMessage(boostedBartersText());
        this.minimumBastionIronButton.setMessage(minimumBastionIronText());
        this.removeBastionZombifiedPiglinsButton.setMessage(removeBastionZombifiedPiglinsText());
        this.spawnNearFilterStructureButton.setMessage(spawnNearFilterStructureText());
        this.minimumNearbyAnimalsButton.setMessage(minimumNearbyAnimalsText());
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

    private LiteralText minimumNearbyAnimalsText() {
        return toggleText("Guarantee 3 Animals Near Structure", this.minimumNearbyAnimals);
    }

    private LiteralText toggleText(String label, boolean enabled) {
        return new LiteralText(label + ": " + (enabled ? "On" : "Off"));
    }
}
