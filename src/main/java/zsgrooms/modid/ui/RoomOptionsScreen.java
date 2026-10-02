package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;

public class RoomOptionsScreen extends Screen {

    private final Screen parent;
    private final String roomName;
    private TextFieldWidget manualSeedField;
    private ButtonWidget seedTypeButton;
    private String selectedSeedType = "zsg";
    private String initialManualSeed = "";
    private int seedCount = 1;
    private int finisherLimit = 1;

    public RoomOptionsScreen(Screen parent, String roomName) {
        super(new LiteralText("Room Options"));
        this.parent = parent;
        this.roomName = roomName;

        Room room = ZsgRooms.getRoom(roomName);
        if (room != null) {
            InGame game = ZsgRooms.getGame(roomName);
            if (game != null) {
                this.seedCount = game.getFinishGoal();
                this.finisherLimit = game.getFinisherLimit();
            }
            String specification = game == null ? ZsgSeedBridge.seedSpecificationFromSeed(room.getSeed()) : game.targetStructure;
            String current = ZsgSeedBridge.normalizeSeedType(specification);
            if ("manual".equals(current)) {
                String normalized = ZsgSeedBridge.normalizeSeedSpecification(specification);
                this.initialManualSeed = normalized.startsWith("manual:")
                        ? normalized.substring("manual:".length())
                        : "";
            }
            this.selectedSeedType = FilterCatalog.find(current).id;
        }
    }

    @Override
    protected void init() {
        String manualSeed = this.manualSeedField == null ? this.initialManualSeed : this.manualSeedField.getText();
        int panelX = panelX();
        int contentX = panelX + 16;
        int contentWidth = panelWidth() - 32;
        int y = panelY() + 54;

        this.seedTypeButton = new ButtonWidget(contentX, y, contentWidth, 20, seedTypeText(), button -> {
            this.client.openScreen(new FilterPickerScreen(this, currentSeedType(), value -> this.selectedSeedType = value));
        });
        this.addButton(this.seedTypeButton);

        this.manualSeedField = new TextFieldWidget(this.textRenderer, contentX, y + 30, contentWidth, 20, new LiteralText("Manual Seed"));
        this.manualSeedField.setText(manualSeed);
        this.manualSeedField.visible = !zsgrooms.modid.AaThunderless.isFilter(currentSeedType());
        updateManualSeedSuggestion();
        this.addButton(this.manualSeedField);
        updateManualSeedState();

        ButtonWidget raceFormat = addButton(new ButtonWidget(contentX, y + 60, contentWidth, 20,
                RaceFormatScreen.summary(this.seedCount, this.finisherLimit), button -> {
            Room room = ZsgRooms.getRoom(this.roomName);
            this.client.openScreen(new RaceFormatScreen(this, this.seedCount, this.finisherLimit,
                    room == null ? 64 : room.maxPlayers, (seeds, finishers) -> {
                this.seedCount = seeds;
                this.finisherLimit = finishers;
            }));
        }));

        addButton(new ButtonWidget(contentX, y + 86, contentWidth, 20, new LiteralText("Tournament"),
                button -> this.client.openScreen(new TournamentScreen(this, this.roomName))));
        InGame currentGame = ZsgRooms.getGame(this.roomName);
        boolean locked = currentGame != null && currentGame.tournamentLocked();
        raceFormat.active = !locked;
        this.seedTypeButton.active = !locked;
        if (locked) this.manualSeedField.active = false;

        int buttonWidth = (contentWidth - 8) / 2;
        this.addButton(new ButtonWidget(contentX, y + 116, buttonWidth, 20, new LiteralText("Apply"), button -> {
            String seedType = selectedSeedTypeValue();
            if (!ZsgSeedBridge.isValidManualSeedSpecification(seedType)) {
                button.setMessage(new LiteralText("Enter Seed"));
                return;
            }
            ZsgRoomsClient.sendRoomAction("filter", this.roomName, seedType);
            zsgrooms.modid.RoomRuleSettings rules = zsgrooms.modid.RoomRuleSettings.capture(ZsgRooms.getGame(this.roomName));
            rules.seedCount = this.seedCount;
            rules.finisherLimit = this.finisherLimit;
            ZsgRoomsClient.sendRoomAction("rules", this.roomName, rules.toJson());
            this.client.openScreen(this.parent);
        })).active = !locked;

        this.addButton(new ButtonWidget(contentX + buttonWidth + 8, y + 116, buttonWidth, 20, new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.manualSeedField != null) {
            this.manualSeedField.tick();
            updateManualSeedSuggestion();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x77000000);
        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 202, 0xCC070707);
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 28, 0xAA1A120C);
        fill(matrices, panelX, panelY + 28, panelX + panelWidth, panelY + 29, 0xFF000000);
        drawCenteredString(matrices, this.textRenderer, "Room Options", this.width / 2, panelY + 10, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, "Next race", this.width / 2, panelY + 36, 0xA8D8FF);
        super.render(matrices, mouseX, mouseY, delta);
        if (zsgrooms.modid.AaThunderless.isFilter(currentSeedType())) {
            drawCenteredString(matrices, this.textRenderer, "Win: All advancements except", this.width / 2, panelY + 85, 0xFFCC55);
            drawCenteredString(matrices, this.textRenderer, "Very Very Frightening", this.width / 2, panelY + 97, 0xFFCC55);
        }
    }

    private LiteralText seedTypeText() {
        return new LiteralText(ZsgSeedBridge.seedTypeLabel(currentSeedType()));
    }

    private String currentSeedType() {
        return this.selectedSeedType;
    }

    private String selectedSeedTypeValue() {
        String seedType = currentSeedType();
        if ("manual".equals(seedType)) {
            return "manual:" + this.manualSeedField.getText().trim();
        }
        return seedType;
    }

    private void updateManualSeedState() {
        if (this.manualSeedField != null) {
            boolean manual = "manual".equals(currentSeedType());
            this.manualSeedField.active = manual;
            this.manualSeedField.setEditable(manual);
        }
    }

    private int panelWidth() {
        return Math.min(284, this.width - 20);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(10, (this.height - 202) / 2);
    }

    private void updateManualSeedSuggestion() {
        if (this.manualSeedField != null) {
            this.manualSeedField.setSuggestion(
                    this.manualSeedField.getText().isEmpty() && this.manualSeedField.active ? "Manual seed" : ""
            );
        }
    }
}
