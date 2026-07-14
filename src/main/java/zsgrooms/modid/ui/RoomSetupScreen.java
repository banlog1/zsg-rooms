package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;
import zsgrooms.modid.net.RoomWebSocketTransport;

import java.security.SecureRandom;

public class RoomSetupScreen extends Screen {
    private static final String[] SEED_TYPES = new String[]{
            "zsg",
            "zsgop",
            "zsgvillage",
            "zsgvillageop",
            "zsgshipwreck",
            "zsgshipwreckop",
            "zsgtemple",
            "zsgtempleop",
            "zsgjungletemple",
            "zsgjungletempleop",
            "rpseedbank",
            "random",
            "room",
            "manual"
    };
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ROOM_CODE_RANDOM = new SecureRandom();

    private final Screen parent;
    private final boolean createMode;
    private TextFieldWidget roomCodeField;
    private TextFieldWidget serverAddressField;
    private TextFieldWidget maxPlayersField;
    private TextFieldWidget finishGoalField;
    private TextFieldWidget manualSeedField;
    private ButtonWidget seedTypeButton;
    private ButtonWidget gameRulesButton;
    private boolean allowCheats;
    private boolean rngStandardization;
    private boolean boostedBarters;
    private boolean minimumBastionIron;
    private boolean removeBastionZombifiedPiglins;
    private boolean helpVisible;
    private int selectedSeedTypeIndex;
    private String statusText;

    public RoomSetupScreen(Screen parent, boolean createMode) {
        super(new LiteralText(createMode ? "Create Room" : "Join Room"));
        this.parent = parent;
        this.createMode = createMode;
        this.helpVisible = false;
        this.selectedSeedTypeIndex = 0;
        this.statusText = "";
        this.allowCheats = false;
        this.rngStandardization = false;
        this.boostedBarters = false;
        this.minimumBastionIron = false;
        this.removeBastionZombifiedPiglins = false;
    }

    @Override
    protected void init() {
        String roomCode = fieldText(this.roomCodeField, this.createMode ? randomRoomCode() : "");
        String serverAddress = fieldText(this.serverAddressField, RoomWebSocketTransport.getRelayUrl());
        String maxPlayersText = fieldText(this.maxPlayersField, "10");
        String finishGoalText = fieldText(this.finishGoalField, "1");
        String manualSeed = fieldText(this.manualSeedField, "");

        int panelX = panelX();
        int fieldX = panelX + labelWidth();
        int fieldWidth = panelWidth() - labelWidth() - 16;
        int copyWidth = 44;
        int copyGap = 5;
        int y = formTop();
        int rowGap = rowGap();

        this.roomCodeField = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth - copyWidth - copyGap, 20, new LiteralText("Room Code"));
        this.roomCodeField.setText(roomCode);
        this.addButton(this.roomCodeField);
        this.addButton(new ButtonWidget(fieldX + fieldWidth - copyWidth, y, copyWidth, 20, new LiteralText("Copy"), button -> {
            String code = this.roomCodeField.getText().trim();
            if (code.isEmpty()) {
                button.setMessage(new LiteralText("Empty"));
                return;
            }
            this.client.keyboard.setClipboard(code);
            button.setMessage(new LiteralText("Copied"));
        }));

        this.serverAddressField = new TextFieldWidget(this.textRenderer, fieldX, y + rowGap, fieldWidth, 20, new LiteralText("Relay URL"));
        this.serverAddressField.setMaxLength(200);
        this.serverAddressField.setText(serverAddress);
        updateSuggestion(this.serverAddressField, "https://your-relay.workers.dev");
        this.addButton(this.serverAddressField);

        this.maxPlayersField = new TextFieldWidget(this.textRenderer, fieldX, y + rowGap * 2, fieldWidth, 20, new LiteralText("Max Players"));
        this.maxPlayersField.setText(maxPlayersText);
        this.addButton(this.maxPlayersField);

        this.finishGoalField = new TextFieldWidget(this.textRenderer, fieldX, y + rowGap * 3, fieldWidth, 20, new LiteralText("Series Goal"));
        this.finishGoalField.setText(finishGoalText);
        this.addButton(this.finishGoalField);

        this.seedTypeButton = new ButtonWidget(fieldX, y + rowGap * 4, fieldWidth, 20, seedTypeText(), button -> {
            this.selectedSeedTypeIndex = (this.selectedSeedTypeIndex + 1) % SEED_TYPES.length;
            button.setMessage(seedTypeText());
            updateManualSeedState();
        });
        this.addButton(this.seedTypeButton);

        this.manualSeedField = new TextFieldWidget(this.textRenderer, fieldX, y + rowGap * 5, fieldWidth, 20, new LiteralText("Manual Seed"));
        this.manualSeedField.setText(manualSeed);
        this.addButton(this.manualSeedField);
        updateManualSeedState();

        this.gameRulesButton = new ButtonWidget(fieldX, y + rowGap * 6, fieldWidth, 20,
                new LiteralText(this.createMode ? "Game Rules..." : "Game Rules: Set by Host"), button -> {
            this.client.openScreen(new RoomGameRulesScreen(this, this.allowCheats, this.rngStandardization,
                    this.boostedBarters, this.minimumBastionIron, this.removeBastionZombifiedPiglins));
        });
        this.gameRulesButton.active = this.createMode;
        this.addButton(this.gameRulesButton);

        int actionY = actionY();
        int helpWidth = 28;
        int actionGap = 6;
        int availableWidth = fieldWidth - helpWidth - actionGap * 2;
        int backWidth = Math.min(88, Math.max(68, availableWidth / 3));
        int primaryWidth = availableWidth - backWidth;

        this.addButton(new ButtonWidget(fieldX, actionY, primaryWidth, 20, new LiteralText(this.createMode ? "Create Room" : "Join Room"), button -> {
            String selectedRoomCode = this.roomCodeField.getText().trim();
            int maxPlayers = parseInt(this.maxPlayersField.getText(), 10);
            int finishGoal = parseInt(this.finishGoalField.getText(), 1);
            String seedType = selectedSeedTypeValue();
            if (!ZsgSeedBridge.isValidManualSeedSpecification(seedType)) {
                this.statusText = "Enter a manual Minecraft seed";
                return;
            }
            String playerName = ZsgRoomsClient.localPlayerName(this.client);
            String playerUuid = ZsgRoomsClient.localPlayerUuid(this.client);
            String relayUrl = this.serverAddressField.getText().trim();

            if (this.createMode) {
                ZsgRooms.createRoom(selectedRoomCode, maxPlayers, finishGoal, seedType, playerName,
                        this.allowCheats, this.rngStandardization, this.boostedBarters, this.minimumBastionIron,
                        this.removeBastionZombifiedPiglins);
                ZsgRooms.setPlayerUuid(selectedRoomCode, playerName, playerUuid);
                boolean hosted = RoomWebSocketTransport.host(relayUrl, selectedRoomCode, playerName);
                if (!hosted) {
                    this.statusText = RoomWebSocketTransport.getStatus();
                    return;
                }
                ZsgRooms.shareChat(selectedRoomCode, "Room connected through the secure relay");
            } else {
                boolean connected = RoomWebSocketTransport.connect(relayUrl, selectedRoomCode, playerName);
                if (!connected) {
                    this.statusText = RoomWebSocketTransport.getStatus();
                    return;
                }
                ZsgRooms.applyRoomAction("join_room", selectedRoomCode, playerName, seedType);
                ZsgRoomsClient.sendRoomAction("profile", selectedRoomCode, playerUuid);
            }

            Room room = ZsgRooms.getRoom(selectedRoomCode);
            if (room != null) {
                this.client.openScreen(new RoomLobbyScreen(this.parent, selectedRoomCode));
            } else {
                this.statusText = RoomWebSocketTransport.getStatus();
            }
        }));

        this.addButton(new ButtonWidget(fieldX + primaryWidth + actionGap, actionY, backWidth, 20, new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));

        this.addButton(new ButtonWidget(fieldX + primaryWidth + backWidth + actionGap * 2, actionY, helpWidth, 20, new LiteralText("?"), button -> {
            this.helpVisible = !this.helpVisible;
        }));
    }

    @Override
    public void tick() {
        super.tick();
        this.roomCodeField.tick();
        this.serverAddressField.tick();
        this.maxPlayersField.tick();
        this.finishGoalField.tick();
        this.manualSeedField.tick();
        updateSuggestion(this.serverAddressField, "https://your-relay.workers.dev");
        updateSuggestion(this.manualSeedField, "Enter a Minecraft seed");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.helpVisible) {
            this.helpVisible = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.helpVisible && keyCode == 256) {
            this.helpVisible = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderRoomBackground(matrices);
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelWidth();
        int panelH = panelHeight();
        fill(matrices, panelX, panelY, panelX + panelW, panelY + panelH, 0xDD070707);
        fill(matrices, panelX, panelY, panelX + panelW, panelY + 28, 0xAA1A120C);
        fill(matrices, panelX, panelY + 28, panelX + panelW, panelY + 29, 0xFF000000);

        drawCenteredString(matrices, this.textRenderer, this.title.asString(), this.width / 2, panelY + 10, 0xFFFFFF);
        if (!isCompact()) {
            drawCenteredString(matrices, this.textRenderer, this.createMode ? "Create a room through the secure relay" : "Join from anywhere using the same relay and code", this.width / 2, panelY + 34, 0xA8D8FF);
        }

        int labelX = panelX + 16;
        int y = formTop();
        int rowGap = rowGap();
        this.textRenderer.drawWithShadow(matrices, "Room", labelX, y + 6, 0xD0D0D0);
        this.textRenderer.drawWithShadow(matrices, "Relay", labelX, y + rowGap + 6, 0xD0D0D0);
        this.textRenderer.drawWithShadow(matrices, "Players", labelX, y + rowGap * 2 + 6, 0xD0D0D0);
        this.textRenderer.drawWithShadow(matrices, "Series Goal", labelX, y + rowGap * 3 + 6, 0xD0D0D0);
        this.textRenderer.drawWithShadow(matrices, "Filter", labelX, y + rowGap * 4 + 6, 0xD0D0D0);
        this.textRenderer.drawWithShadow(matrices, "Manual", labelX, y + rowGap * 5 + 6, 0xD0D0D0);
        this.textRenderer.drawWithShadow(matrices, "Rules", labelX, y + rowGap * 6 + 6, 0xD0D0D0);

        if (!isCompact() && !"manual".equals(currentSeedType()) && (this.statusText == null || this.statusText.isEmpty())) {
            String hint = "Both players use the same workers.dev relay URL";
            drawCenteredString(matrices, this.textRenderer, hint, this.width / 2, actionY() - 15, 0x777777);
        }
        if (this.statusText != null && !this.statusText.isEmpty()) {
            drawStatusText(matrices, this.statusText, panelW - 24);
        }
        super.render(matrices, mouseX, mouseY, delta);
        if (this.helpVisible) {
            renderHelp(matrices);
        }
    }

    private void renderHelp(MatrixStack matrices) {
        int boxW = Math.min(390, this.width - 20);
        int boxH = Math.min(166, this.height - 20);
        int x = this.width / 2 - boxW / 2;
        int y = this.height / 2 - boxH / 2;
        fill(matrices, x, y, x + boxW, y + boxH, 0xEE050505);
        fill(matrices, x, y, x + boxW, y + 22, 0xCC1A120C);
        fill(matrices, x, y + 22, x + boxW, y + 23, 0xFF000000);

        drawCenteredString(matrices, this.textRenderer, "Playing With Friends", this.width / 2, y + 7, 0xFFFFFF);
        int textX = x + 14;
        int textY = y + 34;
        int textWidth = boxW - 28;
        this.textRenderer.drawWithShadow(matrices, "Host", textX, textY, 0xA8D8FF);
        textY = drawWrappedText(matrices, "Enter the deployed relay URL, create a room, then share the short room code with your friend.", textX, textY + 13, textWidth, 0xD8D8D8);
        this.textRenderer.drawWithShadow(matrices, "Friend", textX, textY + 3, 0xA8D8FF);
        textY = drawWrappedText(matrices, "Open Join Room and enter the same relay URL and room code. No ports or tunnel programs are needed.", textX, textY + 16, textWidth, 0xD8D8D8);
        textY = drawWrappedText(matrices, "Series Goal is reserved for first-to-N match series; current races finish after one completed run.", textX, textY + 4, textWidth, 0xD8D8D8);
        drawWrappedText(matrices, "Worlds stay local; the relay only carries room and race state.", textX, textY + 4, textWidth, 0x88FF88);
        drawCenteredString(matrices, this.textRenderer, "Click anywhere or press Esc to close", this.width / 2, y + boxH - 15, 0x777777);
    }

    private void renderRoomBackground(MatrixStack matrices) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);
        fill(matrices, 0, 0, this.width, 42, 0xAA120C08);
        fill(matrices, 0, 42, this.width, 43, 0xFF000000);
    }

    private LiteralText seedTypeText() {
        return new LiteralText(ZsgSeedBridge.seedTypeLabel(currentSeedType()));
    }

    void setGameRules(boolean allowCheats, boolean rngStandardization, boolean boostedBarters,
            boolean minimumBastionIron, boolean removeBastionZombifiedPiglins) {
        this.allowCheats = allowCheats;
        this.rngStandardization = rngStandardization;
        this.boostedBarters = boostedBarters;
        this.minimumBastionIron = minimumBastionIron;
        this.removeBastionZombifiedPiglins = removeBastionZombifiedPiglins;
    }

    private String currentSeedType() {
        return SEED_TYPES[this.selectedSeedTypeIndex];
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

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean isCompact() {
        return this.width < 460 || this.height < 350;
    }

    private int panelWidth() {
        return Math.min(500, this.width - 16);
    }

    private int panelHeight() {
        return isCompact() ? Math.min(286, this.height - 12) : Math.min(316, this.height - 12);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(6, (this.height - panelHeight()) / 2);
    }

    private int labelWidth() {
        return isCompact() ? 72 : 100;
    }

    private int rowGap() {
        if (!isCompact()) {
            return this.height < 370 ? 26 : 30;
        }
        if (this.height < 250) {
            return 18;
        }
        if (this.height < 270) {
            return 19;
        }
        return this.height < 300 ? 21 : 25;
    }

    private int formTop() {
        return panelY() + (isCompact() ? 35 : 50);
    }

    private int actionY() {
        return panelY() + panelHeight() - 28;
    }

    private String fieldText(TextFieldWidget field, String fallback) {
        return field == null ? fallback : field.getText();
    }

    private String trimToWidth(String text, int width) {
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && this.textRenderer.getWidth(trimmed + ellipsis) > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private void drawStatusText(MatrixStack matrices, String text, int width) {
        int color = isErrorStatus(text) ? 0xFF7777 : 0x88FF88;
        if (panelHeight() < 248 || this.textRenderer.getWidth(text) <= width) {
            drawCenteredString(matrices, this.textRenderer, trimToWidth(text, width), this.width / 2, actionY() - 12, color);
            return;
        }

        int split = text.length();
        while (split > 0 && this.textRenderer.getWidth(text.substring(0, split)) > width) {
            split -= 1;
        }
        int wordBreak = text.lastIndexOf(' ', split);
        if (wordBreak > 0) {
            split = wordBreak;
        }
        String firstLine = text.substring(0, split).trim();
        String secondLine = text.substring(split).trim();
        drawCenteredString(matrices, this.textRenderer, trimToWidth(firstLine, width), this.width / 2, actionY() - 23, color);
        drawCenteredString(matrices, this.textRenderer, trimToWidth(secondLine, width), this.width / 2, actionY() - 12, color);
    }

    private boolean isErrorStatus(String text) {
        String lowered = text.toLowerCase();
        return lowered.contains("could not") || lowered.contains("failed") || lowered.contains("rejected") || lowered.contains("error");
    }

    private int drawWrappedText(MatrixStack matrices, String text, int x, int y, int width, int color) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && this.textRenderer.getWidth(candidate) > width) {
                this.textRenderer.drawWithShadow(matrices, line.toString(), x, y, color);
                y += 11;
                line.setLength(0);
                line.append(word);
            } else {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (line.length() > 0) {
            this.textRenderer.drawWithShadow(matrices, line.toString(), x, y, color);
            y += 11;
        }
        return y;
    }

    private String randomRoomCode() {
        StringBuilder code = new StringBuilder("ZSG-");
        for (int i = 0; i < 8; i++) {
            code.append(ROOM_CODE_ALPHABET.charAt(ROOM_CODE_RANDOM.nextInt(ROOM_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private void updateSuggestion(TextFieldWidget field, String placeholder) {
        if (field != null) {
            field.setSuggestion(field.getText().isEmpty() && field.active ? placeholder : "");
        }
    }
}
