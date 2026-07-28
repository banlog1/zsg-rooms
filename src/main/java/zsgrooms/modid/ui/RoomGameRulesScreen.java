package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class RoomGameRulesScreen extends Screen {
    private static final int HELP_WIDTH = 18;
    private static final int STATE_WIDTH = 46;
    private static final int CONTROL_GAP = 4;
    private static final int PRESET_ARROW_WIDTH = 24;
    private static final int PRESET_LABEL_WIDTH = 280;

    private final RoomSetupScreen parent;
    private final List<RuleRow> ruleRows = new ArrayList<RuleRow>();
    private RoomRulePreset preset;
    private ButtonWidget presetButton;
    private ButtonWidget presetHelpButton;
    private boolean twoColumnLayout;
    private int firstGroupHeaderY;
    private int secondGroupHeaderY;
    private int firstGroupX;
    private int secondGroupX;
    private int groupWidth;

    private boolean allowCheats;
    private boolean rngStandardization;
    private boolean boostedBarters;
    private boolean minimumBastionIron;
    private boolean removeBastionZombifiedPiglins;
    private boolean removeNaturalStriderJockeys;
    private boolean spawnNearFilterStructure;
    private boolean minimumNearbyAnimals;
    private boolean netherEntryWarmup;
    private boolean disablePauseWorldSaves;

    public RoomGameRulesScreen(RoomSetupScreen parent, boolean allowCheats, boolean rngStandardization,
            boolean boostedBarters, boolean minimumBastionIron, boolean removeBastionZombifiedPiglins,
            boolean removeNaturalStriderJockeys, boolean spawnNearFilterStructure,
            boolean minimumNearbyAnimals, boolean netherEntryWarmup,
            boolean disablePauseWorldSaves, RoomRulePreset preset) {
        super(new LiteralText("Room Game Rules"));
        this.parent = parent;
        this.allowCheats = allowCheats;
        this.rngStandardization = rngStandardization;
        this.boostedBarters = boostedBarters;
        this.minimumBastionIron = minimumBastionIron;
        this.removeBastionZombifiedPiglins = removeBastionZombifiedPiglins;
        this.removeNaturalStriderJockeys = removeNaturalStriderJockeys;
        this.spawnNearFilterStructure = spawnNearFilterStructure;
        this.minimumNearbyAnimals = minimumNearbyAnimals;
        this.netherEntryWarmup = netherEntryWarmup;
        this.disablePauseWorldSaves = disablePauseWorldSaves;
        this.preset = preset == null ? RoomRulePreset.CUSTOM : preset;
    }

    @Override
    protected void init() {
        this.ruleRows.clear();

        int panelX = panelX();
        int contentX = panelX + 16;
        int contentWidth = panelWidth() - 32;
        int buttonHeight = controlHeight();
        int presetY = panelY() + 35;
        int presetLabelWidth = Math.min(
                PRESET_LABEL_WIDTH,
                contentWidth - PRESET_ARROW_WIDTH * 2 - HELP_WIDTH - CONTROL_GAP * 3);
        int presetControlWidth =
                PRESET_ARROW_WIDTH * 2 + presetLabelWidth + HELP_WIDTH + CONTROL_GAP * 3;
        int presetX = this.width / 2 - presetControlWidth / 2;

        this.addButton(new ButtonWidget(
                presetX,
                presetY,
                PRESET_ARROW_WIDTH,
                buttonHeight,
                new LiteralText("<"),
                button -> cyclePreset(-1)));
        this.presetButton = new ButtonWidget(
                presetX + PRESET_ARROW_WIDTH + CONTROL_GAP,
                presetY,
                presetLabelWidth,
                buttonHeight,
                presetText(),
                button -> cyclePreset(1));
        this.addButton(this.presetButton);
        int nextPresetX =
                presetX + PRESET_ARROW_WIDTH + CONTROL_GAP + presetLabelWidth + CONTROL_GAP;
        this.addButton(new ButtonWidget(
                nextPresetX,
                presetY,
                PRESET_ARROW_WIDTH,
                buttonHeight,
                new LiteralText(">"),
                button -> cyclePreset(1)));
        this.presetHelpButton = new ButtonWidget(
                nextPresetX + PRESET_ARROW_WIDTH + CONTROL_GAP,
                presetY,
                HELP_WIDTH,
                buttonHeight,
                new LiteralText("?"),
                button -> {
                });
        this.addButton(this.presetHelpButton);

        this.twoColumnLayout = useTwoColumns(panelWidth(), this.height);
        int groupTop = presetY + buttonHeight + (isCompact() ? 5 : 10);
        this.firstGroupHeaderY = groupTop;
        this.firstGroupX = contentX;

        if (this.twoColumnLayout) {
            int columnGap = 12;
            this.groupWidth = (contentWidth - columnGap) / 2;
            this.secondGroupX = contentX + this.groupWidth + columnGap;
            this.secondGroupHeaderY = groupTop;
            int rowTop = groupTop + 13;
            int rowGap = twoColumnRowGap(rowTop, buttonHeight);
            addRaceRules(this.firstGroupX, rowTop, this.groupWidth, buttonHeight, rowGap);
            addWorldRules(this.secondGroupX, rowTop, this.groupWidth, buttonHeight, rowGap);
        } else {
            this.groupWidth = contentWidth;
            this.secondGroupX = contentX;
            int rowTop = groupTop + 11;
            int rowGap = singleColumnRowGap(rowTop, buttonHeight);
            addRaceRules(this.firstGroupX, rowTop, this.groupWidth, buttonHeight, rowGap);
            this.secondGroupHeaderY = rowTop + rowGap * 5;
            addWorldRules(
                    this.secondGroupX,
                    this.secondGroupHeaderY + 10,
                    this.groupWidth,
                    buttonHeight,
                    rowGap);
        }

        int doneWidth = Math.min(180, contentWidth);
        this.addButton(new ButtonWidget(
                this.width / 2 - doneWidth / 2,
                actionY(),
                doneWidth,
                buttonHeight,
                new LiteralText("Done"),
                button -> saveAndClose()));
    }

    private void addRaceRules(int x, int y, int width, int height, int rowGap) {
        addRuleRow(
                "Allow Cheats",
                "Allows commands and LAN cheats in each runner's local world. "
                        + "Cheat-enabled completions are not added to recent run history.",
                x, y, width, height,
                () -> this.allowCheats,
                () -> this.allowCheats = !this.allowCheats,
                false);
        addRuleRow(
                "Standardize Race RNG",
                "Makes supported mob drops, piglin barters, Eye of Ender break rolls, "
                        + "Blaze spawners, and world spawn deterministic from the shared seed.",
                x, y + rowGap, width, height,
                () -> this.rngStandardization,
                () -> this.rngStandardization = !this.rngStandardization,
                false);
        addRuleRow(
                "Increase Piglin Barter Rates",
                "Uses the ZSG Rooms barter table with increased pearl, string, and obsidian rates. "
                        + "This is independent from RNG standardization.",
                x, y + rowGap * 2, width, height,
                () -> this.boostedBarters,
                () -> this.boostedBarters = !this.boostedBarters,
                false);
        addRuleRow(
                "Spawn Near Filter Structure",
                "If the selected route structure is farther than 140 blocks away, moves world spawn "
                        + "to safe terrain 70-128 blocks from it.",
                x, y + rowGap * 3, width, height,
                () -> this.spawnNearFilterStructure,
                () -> this.spawnNearFilterStructure = !this.spawnNearFilterStructure,
                false);
        addRuleRow(
                "Guarantee 3 Animals Near Structure",
                "Ensures at least three eligible land animals exist within 70 blocks of the selected "
                        + "filter structure when suitable natural spawn locations are available.",
                x, y + rowGap * 4, width, height,
                () -> this.minimumNearbyAnimals,
                () -> this.minimumNearbyAnimals = !this.minimumNearbyAnimals,
                false);
    }

    private void addWorldRules(int x, int y, int width, int height, int rowGap) {
        addRuleRow(
                "Guarantee 3 Iron in Bastion",
                "Tops up the first qualifying bastion chest to exactly 27 iron units when its "
                        + "generated loot contains less than three ingots' worth.",
                x, y, width, height,
                () -> this.minimumBastionIron,
                () -> this.minimumBastionIron = !this.minimumBastionIron,
                false);
        addRuleRow(
                "Remove Zombified Piglins from Bastions",
                "Removes only zombified piglins inside generated bastion piece bounds. "
                        + "Other bastion mobs and spawning rules remain unchanged.",
                x, y + rowGap, width, height,
                () -> this.removeBastionZombifiedPiglins,
                () -> this.removeBastionZombifiedPiglins = !this.removeBastionZombifiedPiglins,
                false);
        addRuleRow(
                "Remove Natural Strider Jockeys",
                "Prevents naturally initialized striders from creating zombified-piglin or baby-strider "
                        + "riders. Player-created passengers are unaffected. This performance override "
                        + "does not change the selected preset.",
                x, y + rowGap * 2, width, height,
                () -> this.removeNaturalStriderJockeys,
                () -> this.removeNaturalStriderJockeys = !this.removeNaturalStriderJockeys,
                true);
        addRuleRow(
                "Preload Nether Entry Chunk",
                "Warms one projected Nether destination chunk during portal charge when the server "
                        + "has enough time. Vanilla dimension transfer still performs the actual entry.",
                x, y + rowGap * 3, width, height,
                () -> this.netherEntryWarmup,
                () -> this.netherEntryWarmup = !this.netherEntryWarmup,
                false);
        addRuleRow(
                "Disable Pause World Saves During Races",
                "Skips only the integrated-server world save caused by entering the pause menu during "
                        + "an active race. Player data, periodic autosaves, shutdown saves, and chunk "
                        + "unloading remain enabled. This performance override does not change the "
                        + "selected preset.",
                x, y + rowGap * 4, width, height,
                () -> this.disablePauseWorldSaves,
                () -> this.disablePauseWorldSaves = !this.disablePauseWorldSaves,
                true);
    }

    private void addRuleRow(
            String label,
            String help,
            int x,
            int y,
            int width,
            int height,
            BooleanSupplier enabled,
            Runnable toggle,
            boolean performanceOverride
    ) {
        int helpX = x + width - HELP_WIDTH;
        int stateX = helpX - CONTROL_GAP - STATE_WIDTH;
        ButtonWidget stateButton = new ButtonWidget(
                stateX, y, STATE_WIDTH, height, stateText(enabled.getAsBoolean()), button -> {
            updatePresetForRuleToggle(performanceOverride);
            toggle.run();
            button.setMessage(stateText(enabled.getAsBoolean()));
        });
        ButtonWidget helpButton = new ButtonWidget(
                helpX, y, HELP_WIDTH, height, new LiteralText("?"), button -> {
                });
        this.addButton(stateButton);
        this.addButton(helpButton);
        this.ruleRows.add(new RuleRow(
                label, help, x, y, width, height, stateX, stateButton, helpButton, enabled));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);

        int panelX = panelX();
        int panelY = panelY();
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + panelHeight(), 0xE0080808);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 29, 0xCC1A120C);
        fill(matrices, panelX, panelY + 29, panelX + panelWidth(), panelY + 30, 0xFF000000);
        drawCenteredString(
                matrices, this.textRenderer, "Room Game Rules", this.width / 2, panelY + 10, 0xFFFFFF);

        drawGroupHeader(matrices, "Race Rules", this.firstGroupX, this.firstGroupHeaderY, this.groupWidth);
        drawGroupHeader(
                matrices,
                "World & Performance",
                this.secondGroupX,
                this.secondGroupHeaderY,
                this.groupWidth);
        for (RuleRow row : this.ruleRows) {
            int background = row.helpButton.isHovered() || row.stateButton.isHovered()
                    ? 0x66303A40
                    : 0x44171717;
            fill(matrices, row.x, row.y, row.x + row.width, row.y + row.height, background);
            drawRuleLabel(matrices, row);
        }

        super.render(matrices, mouseX, mouseY, delta);
        renderHoveredHelp(matrices, mouseX, mouseY);
    }

    private void drawGroupHeader(MatrixStack matrices, String title, int x, int y, int width) {
        this.textRenderer.drawWithShadow(matrices, title, x + 2, y + 1, 0x6FC8FF);
        int lineX = x + this.textRenderer.getWidth(title) + 8;
        if (lineX < x + width) {
            fill(matrices, lineX, y + 5, x + width, y + 6, 0xFF344047);
        }
    }

    private void drawRuleLabel(MatrixStack matrices, RuleRow row) {
        int labelX = row.x + 5;
        int availableWidth = Math.max(0, row.stateX - labelX - 5);
        String label = trimWithEllipsis(row.label, availableWidth);
        int textY = row.y + Math.max(1, (row.height - 8) / 2);
        this.textRenderer.drawWithShadow(matrices, label, labelX, textY, 0xE4E4E4);
    }

    private String trimWithEllipsis(String value, int maximumWidth) {
        if (this.textRenderer.getWidth(value) <= maximumWidth) {
            return value;
        }
        int ellipsisWidth = this.textRenderer.getWidth("...");
        return this.textRenderer.trimToWidth(value, Math.max(0, maximumWidth - ellipsisWidth)) + "...";
    }

    private void renderHoveredHelp(MatrixStack matrices, int mouseX, int mouseY) {
        if (this.presetHelpButton != null && this.presetHelpButton.isHovered()) {
            renderHelpTooltip(
                    matrices,
                    "Rule Presets",
                    "Presets choose a complete starting ruleset. Changing any individual rule switches "
                            + "the selection to Custom.",
                    mouseX,
                    mouseY);
            return;
        }
        for (RuleRow row : this.ruleRows) {
            if (row.helpButton.isHovered()) {
                renderHelpTooltip(matrices, row.label, row.help, mouseX, mouseY);
                return;
            }
        }
    }

    private void renderHelpTooltip(
            MatrixStack matrices,
            String title,
            String description,
            int mouseX,
            int mouseY
    ) {
        int wrapWidth = Math.max(120, Math.min(280, this.width - 40));
        List<StringRenderable> lines = new ArrayList<StringRenderable>();
        lines.add(new LiteralText(title).formatted(Formatting.AQUA));
        lines.addAll(this.textRenderer.wrapLines(new LiteralText(description), wrapWidth));
        this.renderTooltip(matrices, lines, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    private void saveAndClose() {
        this.parent.setGameRules(this.allowCheats, this.rngStandardization, this.boostedBarters,
                this.minimumBastionIron, this.removeBastionZombifiedPiglins,
                this.removeNaturalStriderJockeys, this.spawnNearFilterStructure,
                this.minimumNearbyAnimals, this.netherEntryWarmup,
                this.disablePauseWorldSaves, this.preset);
        this.client.openScreen(this.parent);
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
        this.removeNaturalStriderJockeys = this.preset.removesNaturalStriderJockeys();
        this.spawnNearFilterStructure = this.preset.spawnsNearFilterStructure();
        this.minimumNearbyAnimals = this.preset.guaranteesNearbyAnimals();
        this.netherEntryWarmup = this.preset.warmsNetherEntry();
        this.disablePauseWorldSaves = this.preset.disablesPauseWorldSaves();
    }

    private void cyclePreset(int direction) {
        RoomRulePreset[] presets = RoomRulePreset.values();
        int index = (this.preset.ordinal() + direction + presets.length) % presets.length;
        this.preset = presets[index];
        applyPreset();
        refreshButtonLabels();
    }

    private void updatePresetForRuleToggle(boolean performanceOverride) {
        this.preset = presetAfterRuleToggle(this.preset, performanceOverride);
        if (this.presetButton != null) {
            this.presetButton.setMessage(presetText());
        }
    }

    private void refreshButtonLabels() {
        this.presetButton.setMessage(presetText());
        for (RuleRow row : this.ruleRows) {
            row.stateButton.setMessage(stateText(row.enabled.getAsBoolean()));
        }
    }

    private LiteralText presetText() {
        return new LiteralText("Preset: " + this.preset.getLabel());
    }

    private Text stateText(boolean enabled) {
        return new LiteralText(enabled ? "On" : "Off")
                .formatted(enabled ? Formatting.GREEN : Formatting.GRAY);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(700, this.width - 16);
    }

    private int panelHeight() {
        return Math.min(300, this.height - 12);
    }

    private int panelY() {
        return Math.max(6, (this.height - panelHeight()) / 2);
    }

    private int actionY() {
        return panelY() + panelHeight() - controlHeight() - 8;
    }

    private int controlHeight() {
        return this.height < 200 ? 10 : this.height < 260 ? 12 : isCompact() ? 16 : 20;
    }

    private int twoColumnRowGap(int rowTop, int buttonHeight) {
        int available = actionY() - rowTop - buttonHeight - 5;
        return Math.max(buttonHeight + 1, Math.min(28, available / 4));
    }

    private int singleColumnRowGap(int rowTop, int buttonHeight) {
        int available = actionY() - rowTop - 10 - buttonHeight;
        return Math.max(8, Math.min(24, available / 9));
    }

    private boolean isCompact() {
        return this.height < 300;
    }

    static boolean useTwoColumns(int panelWidth, int screenHeight) {
        return panelWidth >= 540 || screenHeight < 210;
    }

    static RoomRulePreset presetAfterRuleToggle(
            RoomRulePreset currentPreset,
            boolean performanceOverride
    ) {
        boolean standardPreset = currentPreset == RoomRulePreset.STANDARD_ZSG_ROOMS
                || currentPreset == RoomRulePreset.STANDARD_ZSG_VANILLA_BARTERS;
        return performanceOverride && standardPreset ? currentPreset : RoomRulePreset.CUSTOM;
    }

    private static final class RuleRow {
        private final String label;
        private final String help;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int stateX;
        private final ButtonWidget stateButton;
        private final ButtonWidget helpButton;
        private final BooleanSupplier enabled;

        private RuleRow(
                String label,
                String help,
                int x,
                int y,
                int width,
                int height,
                int stateX,
                ButtonWidget stateButton,
                ButtonWidget helpButton,
                BooleanSupplier enabled
        ) {
            this.label = label;
            this.help = help;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.stateX = stateX;
            this.stateButton = stateButton;
            this.helpButton = helpButton;
            this.enabled = enabled;
        }
    }
}
