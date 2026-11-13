package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.RoomRuleSettings;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;

import java.util.function.BiConsumer;

public class RoomOptionsScreen extends Screen {

    private final Screen parent;
    private final String roomName;
    private TextFieldWidget manualSeedField;
    private ButtonWidget seedTypeButton;
    private TextFieldWidget capacityField;
    private ButtonWidget capacityMinus;
    private ButtonWidget capacityPlus;
    private ButtonWidget raceFormatButton;
    private ButtonWidget applyButton;
    private String initialCapacity = "8";
    private String validationError = "";
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
            this.initialCapacity = Integer.toString(room.maxPlayers);
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
        if (this.manualSeedField != null) this.initialManualSeed = this.manualSeedField.getText();
        this.manualSeedField = null;
        String capacity = this.capacityField == null ? this.initialCapacity : this.capacityField.getText();
        int panelX = panelX();
        int contentX = panelX + 16;
        int contentWidth = panelWidth() - 32;
        int y = panelY() + rowY(this.height, 0);

        this.seedTypeButton = new ButtonWidget(contentX, y, contentWidth, 20, seedTypeText(), button -> {
            this.client.openScreen(new FilterPickerScreen(this, currentSeedType(), value -> this.selectedSeedType = value));
        });
        this.addButton(this.seedTypeButton);

        if ("manual".equals(currentSeedType())) {
            this.manualSeedField = new TextFieldWidget(this.textRenderer, contentX, panelY() + rowY(this.height, 1), contentWidth, 20, new LiteralText("Manual Seed"));
            this.manualSeedField.setText(this.initialManualSeed);
            this.addButton(this.manualSeedField);
            updateManualSeedState();
            updateManualSeedSuggestion();
        }

        int nextRow = hasFilterDetails(currentSeedType()) ? 2 : 1;
        this.raceFormatButton = addButton(new ButtonWidget(contentX, panelY() + rowY(this.height, nextRow++), contentWidth, 20,
                RaceFormatScreen.summary(this.seedCount, this.finisherLimit), button -> {
            Room room = ZsgRooms.getRoom(this.roomName);
            this.client.openScreen(new RaceFormatScreen(this, this.seedCount, this.finisherLimit,
                    room == null ? 64 : room.maxPlayers, (seeds, finishers) -> {
                this.seedCount = seeds;
                this.finisherLimit = finishers;
            }));
        }));

        addButton(new ButtonWidget(contentX, panelY() + rowY(this.height, nextRow++), contentWidth, 20, new LiteralText("Tournament"),
                button -> this.client.openScreen(new TournamentScreen(this, this.roomName))));

        int capacityY = panelY() + rowY(this.height, nextRow);
        int right = contentX + contentWidth;
        this.capacityField = new TextFieldWidget(this.textRenderer, right - 62, capacityY, 38, 20, new LiteralText("Player capacity (2-64)"));
        this.capacityField.setMaxLength(2);
        this.capacityField.setTextPredicate(text -> text.matches("[0-9]{0,2}"));
        this.capacityField.setText(capacity);
        this.capacityField.setChangedListener(text -> this.validationError = "");
        addButton(this.capacityField);
        this.capacityMinus = addButton(new ButtonWidget(right - 86, capacityY, 20, 20, new LiteralText("-"),
                button -> stepCapacity(-1)));
        this.capacityPlus = addButton(new ButtonWidget(right - 20, capacityY, 20, 20, new LiteralText("+"),
                button -> stepCapacity(1)));

        int buttonWidth = (contentWidth - 8) / 2;
        int footerY = panelY() + panelHeight() - 28;
        this.applyButton = this.addButton(new ButtonWidget(contentX, footerY, buttonWidth, 20, new LiteralText("Apply"), button -> {
            this.validationError = applyOptions(this.roomName, ZsgRoomsClient.localPlayerName(this.client),
                    this.capacityField.getText(), selectedSeedTypeValue(), this.seedCount, this.finisherLimit,
                    (action, value) -> ZsgRoomsClient.sendRoomAction(action, this.roomName, value));
            if (this.validationError.isEmpty()) {
                this.client.openScreen(this.parent);
            }
        }));

        this.addButton(new ButtonWidget(contentX + buttonWidth + 8, footerY, buttonWidth, 20, new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));
        updateControls();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.manualSeedField != null) {
            this.manualSeedField.tick();
            updateManualSeedSuggestion();
        }
        if (this.capacityField != null) {
            this.capacityField.tick();
            updateControls();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x77000000);
        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int headerHeight = this.height < 240 ? 20 : 28;
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + panelHeight(), 0xCC070707);
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + headerHeight, 0xAA1A120C);
        fill(matrices, panelX, panelY + headerHeight, panelX + panelWidth, panelY + headerHeight + 1, 0xFF000000);
        drawCenteredString(matrices, this.textRenderer, "Room Options", this.width / 2, panelY + (this.height < 240 ? 6 : 10), 0xFFFFFF);
        if (this.height >= 240) {
            drawCenteredString(matrices, this.textRenderer, "Next race", this.width / 2, panelY + 34, 0xA8D8FF);
        }
        this.textRenderer.drawWithShadow(matrices, "Players (2-64)", panelX + 16,
                panelY + rowY(this.height, hasFilterDetails(currentSeedType()) ? 4 : 3) + 6, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, this.validationError, this.width / 2,
                panelY + panelHeight() - 40, 0xFF7777);
        super.render(matrices, mouseX, mouseY, delta);
        if (zsgrooms.modid.AaThunderless.isFilter(currentSeedType())) {
            int manualY = panelY + rowY(this.height, 1);
            drawCenteredString(matrices, this.textRenderer, "Win: All advancements except", this.width / 2, manualY + 1, 0xFFCC55);
            drawCenteredString(matrices, this.textRenderer, "Very Very Frightening", this.width / 2, manualY + 11, 0xFFCC55);
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
            boolean manual = "manual".equals(currentSeedType()) && canEdit();
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
        return Math.max(2, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() { return panelHeight(this.height, hasFilterDetails(currentSeedType())); }

    static boolean hasFilterDetails(String seedType) {
        return "manual".equals(seedType) || zsgrooms.modid.AaThunderless.isFilter(seedType);
    }

    static int panelHeight(int height, boolean details) {
        return (height < 240 ? 176 : 224) - (details ? 0 : height < 240 ? 22 : 26);
    }

    static int rowY(int height, int row) { return height < 240 ? 24 + row * 22 : 48 + row * 26; }

    private boolean canEdit() {
        InGame game = ZsgRooms.getGame(this.roomName);
        return RoomRuleSettings.canEdit(ZsgRooms.getRoom(this.roomName), game,
                ZsgRoomsClient.localPlayerName(this.client)) && !game.tournamentLocked();
    }

    private void updateControls() {
        boolean editable = canEdit();
        this.seedTypeButton.active = editable;
        this.raceFormatButton.active = editable;
        this.applyButton.active = editable;
        this.capacityField.active = editable;
        this.capacityField.setEditable(editable);
        Room room = ZsgRooms.getRoom(this.roomName);
        int minimum = room == null ? 2 : Math.max(2, room.getPlayerCount());
        int value = Room.parseCapacity(this.capacityField.getText());
        this.capacityMinus.active = editable && value > minimum;
        this.capacityPlus.active = editable && value < 64;
        updateManualSeedState();
    }

    private void stepCapacity(int step) {
        Room room = ZsgRooms.getRoom(this.roomName);
        int minimum = room == null ? 2 : Math.max(2, room.getPlayerCount());
        int value = Room.parseCapacity(this.capacityField.getText());
        this.capacityField.setText(Integer.toString(Math.max(minimum, Math.min(64, Math.max(2, value) + step))));
        updateControls();
    }

    static String capacityError(Room room, InGame game, String playerName, String value) {
        if (!RoomRuleSettings.canEdit(room, game, playerName) || game.tournamentLocked()) {
            return "Capacity needs an unlocked lobby host";
        }
        int capacity = Room.parseCapacity(value);
        if (capacity < 0) return "Capacity must be 2-64";
        if (capacity < room.getPlayerCount()) return "Capacity below roster (" + room.getPlayerCount() + ")";
        return "";
    }

    static String applyOptions(String roomName, String playerName, String capacityValue, String seedType,
            int seeds, int finishers, BiConsumer<String, String> sendAction) {
        Room room = ZsgRooms.getRoom(roomName);
        String error = capacityError(room, ZsgRooms.getGame(roomName), playerName, capacityValue);
        if (!error.isEmpty()) return error;
        if (!ZsgSeedBridge.isValidManualSeedSpecification(seedType)) return "Enter Seed";
        int capacity = Room.parseCapacity(capacityValue);
        if (room.maxPlayers != capacity) {
            sendAction.accept("capacity", Integer.toString(capacity));
            // Host actions are applied locally before returning; never apply other settings on rejection.
            room = ZsgRooms.getRoom(roomName);
            error = capacityError(room, ZsgRooms.getGame(roomName), playerName, capacityValue);
            if (!error.isEmpty()) return error;
            if (room.maxPlayers != capacity) return "Capacity not confirmed; retry";
        }
        RoomRuleSettings rules = RoomRuleSettings.capture(ZsgRooms.getGame(roomName));
        rules.seedCount = seeds;
        rules.finisherLimit = finishers;
        sendAction.accept("filter", seedType);
        sendAction.accept("rules", rules.toJson());
        return "";
    }

    private void updateManualSeedSuggestion() {
        if (this.manualSeedField != null) {
            this.manualSeedField.setSuggestion(
                    this.manualSeedField.getText().isEmpty() && this.manualSeedField.active ? "Manual seed" : ""
            );
        }
    }
}
