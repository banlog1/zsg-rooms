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
import zsgrooms.modid.net.RoomSocketTransport;
import zsgrooms.modid.net.RoomWebSocketTransport;

import java.util.List;

public class RoomLobbyScreen extends Screen {
    private final Screen parent;
    private final String roomName;
    private TextFieldWidget chatField;
    private ButtonWidget optionsButton;
    private ButtonWidget startButton;
    private ButtonWidget copyCodeButton;
    private int chatScrollOffset;

    public RoomLobbyScreen(Screen parent, String roomName) {
        super(new LiteralText("Room Lobby"));
        this.parent = parent;
        this.roomName = roomName;
        this.chatScrollOffset = 0;
    }

    @Override
    protected void init() {
        boolean compact = isCompact();
        int footerTop = footerTop();
        int chatX = compact ? 8 : 16;
        int chatWidth = compact ? this.width - 16 : Math.max(160, Math.min(this.width - 360, 330));
        int chatY = compact ? footerTop + 4 : footerTop - 28;

        int copyWidth = 46;
        int copyX = this.width - copyWidth - 8;
        this.copyCodeButton = new ButtonWidget(copyX, compact ? 16 : 11, copyWidth, 20, new LiteralText("Copy"), button -> {
            this.client.keyboard.setClipboard(this.roomName);
            button.setMessage(new LiteralText("Copied"));
        });
        this.addButton(this.copyCodeButton);

        this.chatField = new TextFieldWidget(this.textRenderer, chatX, chatY, chatWidth, 20, new LiteralText("Chat"));
        this.chatField.setMaxLength(120);
        this.chatField.setFocusUnlocked(true);
        updateChatSuggestion();
        this.addButton(this.chatField);

        if (compact) {
            int gap = 4;
            int buttonY = footerTop + 30;
            int buttonWidth = (this.width - 16 - gap * 3) / 4;
            addLobbyButton(8, buttonY, buttonWidth, "Leave", "leave");
            this.optionsButton = addLobbyButton(8 + (buttonWidth + gap), buttonY, buttonWidth, "Options", "options");
            this.startButton = addLobbyButton(8 + (buttonWidth + gap) * 2, buttonY, buttonWidth, "Start", "start");
            addLobbyButton(8 + (buttonWidth + gap) * 3, buttonY, buttonWidth, "Share", "share");
        } else {
            int bottomY = footerTop + 16;
            addLobbyButton(16, bottomY, 112, "Leave Room", "leave");
            this.optionsButton = addLobbyButton(this.width - 332, bottomY, 100, "Options", "options");
            this.startButton = addLobbyButton(this.width - 224, bottomY, 100, "Start Race", "start");
            addLobbyButton(this.width - 116, bottomY, 100, "Share Seed", "share");
        }
        updateHostControls();
    }

    @Override
    public void tick() {
        super.tick();
        updateHostControls();
        if (this.chatField != null) {
            this.chatField.tick();
            updateChatSuggestion();
        }
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (this.chatField != null && this.chatField.isFocused() && this.chatField.charTyped(chr, keyCode)) {
            return true;
        }
        return super.charTyped(chr, keyCode);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.chatField != null) {
            boolean clickedChat = this.chatField.mouseClicked(mouseX, mouseY, button);
            this.chatField.setSelected(clickedChat);
            if (clickedChat) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isMouseOverChat(mouseX, mouseY)) {
            Room room = ZsgRooms.getRoom(this.roomName);
            int maxOffset = maxChatScrollOffset(room);
            if (amount > 0.0D) {
                this.chatScrollOffset = Math.min(maxOffset, this.chatScrollOffset + 1);
            } else if (amount < 0.0D) {
                this.chatScrollOffset = Math.max(0, this.chatScrollOffset - 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.chatField != null && this.chatField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                sendChatMessage();
                return true;
            }
            if (this.chatField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderRoomBackground(matrices);
        drawCenteredString(matrices, this.textRenderer, "Match Room - Private Mode", this.width / 2, isCompact() ? 7 : 18, 0xFFFFFF);
        drawRoomCode(matrices);
        drawSocketStatus(matrices);

        int top = headerHeight();
        int playerPanelBottom = chatTop();
        fill(matrices, 0, top, this.width, playerPanelBottom, 0xB8060606);
        fill(matrices, 0, playerPanelBottom, this.width, playerPanelBottom + 1, 0xFF000000);

        Room room = ZsgRooms.getRoom(this.roomName);
        if (room != null) {
            String seed = room.getSeed();
            int detailsHeight = isCompact() ? 18 : 44;
            drawPlayerRows(matrices, room, top + (isCompact() ? 5 : 12), playerPanelBottom - detailsHeight);
            drawRoomDetails(matrices, seed, playerPanelBottom - detailsHeight);
        }

        drawChatAndStatus(matrices, room, playerPanelBottom);
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void drawRoomCode(MatrixStack matrices) {
        String code = "Code: " + this.roomName;
        if (isCompact()) {
            int availableWidth = Math.max(80, this.width - 72);
            drawCenteredString(matrices, this.textRenderer, trimToWidth(code, availableWidth), availableWidth / 2 + 4, 20, 0xA8D8FF);
            return;
        }
        int right = this.copyCodeButton == null ? this.width - 8 : this.copyCodeButton.x - 5;
        code = trimToWidth(code, Math.max(110, this.width / 3));
        int textWidth = this.textRenderer.getWidth(code);
        int x = Math.max(8, right - textWidth - 6);
        fill(matrices, x - 6, 11, right, 31, 0x66000000);
        this.textRenderer.drawWithShadow(matrices, code, x, 17, 0xA8D8FF);
    }

    private void drawSocketStatus(MatrixStack matrices) {
        String status = RoomSocketTransport.isHosting() || RoomSocketTransport.isConnected()
                ? RoomSocketTransport.getStatus()
                : RoomWebSocketTransport.getStatus();
        if (status == null || status.isEmpty()) {
            return;
        }
        if (isCompact()) {
            drawCenteredString(matrices, this.textRenderer, trimToWidth(status, this.width - 20), this.width / 2, 33, 0x88FF88);
            return;
        }
        status = trimToWidth(status, Math.max(150, this.width / 2));
        int textWidth = this.textRenderer.getWidth(status);
        int x = Math.max(8, this.width - textWidth - 14);
        this.textRenderer.drawWithShadow(matrices, status, x, 34, 0x88FF88);
    }

    private void renderRoomBackground(MatrixStack matrices) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x55000000);
        fill(matrices, 0, 0, this.width, headerHeight(), 0xAA120C08);
        fill(matrices, 0, headerHeight(), this.width, headerHeight() + 1, 0xFF000000);
        fill(matrices, 0, footerTop(), this.width, this.height, 0xAA120C08);
        fill(matrices, 0, footerTop() - 1, this.width, footerTop(), 0xFF000000);
    }

    private void drawPlayerRows(MatrixStack matrices, Room room, int startY, int maxY) {
        List<String> names = room.getPlayerNames();
        boolean compact = isCompact();
        int columns = this.width >= 480 ? 2 : 1;
        int outerMargin = compact ? 12 : 24;
        int columnGap = 8;
        int rowHeight = compact ? 20 : 34;
        int rowGap = compact ? 3 : 4;
        int rowWidth = (this.width - outerMargin * 2 - columnGap * (columns - 1)) / columns;
        int rowsAvailable = Math.max(0, (maxY - startY) / (rowHeight + rowGap));
        int capacity = rowsAvailable * columns;
        int visibleCount = Math.min(names.size(), capacity);

        for (int i = 0; i < visibleCount; i++) {
            String name = names.get(i);
            int column = i % columns;
            int row = i / columns;
            int rowX = outerMargin + column * (rowWidth + columnGap);
            int y = startY + row * (rowHeight + rowGap);
            fill(matrices, rowX, y, rowX + rowWidth, y + rowHeight, 0x55000000);

            int avatarSize = compact ? 14 : 24;
            int avatarX = rowX + 6;
            int avatarY = y + (rowHeight - avatarSize) / 2;
            fill(matrices, avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, avatarColor(name));
            fill(matrices, avatarX + 2, avatarY + 2, avatarX + avatarSize - 2, avatarY + 5, 0x55FFFFFF);

            int nameColor = i == 0 ? 0xFFFFFF : 0xFFD85A;
            int textX = avatarX + avatarSize + 8;
            if (compact) {
                String label = name + (i == 0 ? " - Host" : " - Ready");
                this.textRenderer.drawWithShadow(matrices, trimToWidth(label, rowWidth - (textX - rowX) - 8), textX, y + 6, nameColor);
            } else {
                this.textRenderer.drawWithShadow(matrices, trimToWidth(name, rowWidth - (textX - rowX) - 58), textX, y + 5, nameColor);
                this.textRenderer.drawWithShadow(matrices, i == 0 ? "Host - Ready" : "Runner - Ready", textX, y + 19, 0xB8B8B8);
                fill(matrices, rowX + rowWidth - 46, y + 8, rowX + rowWidth - 7, y + 27, 0xFF5A5A5A);
                drawCenteredString(matrices, this.textRenderer, "Mute", rowX + rowWidth - 26, y + 14, 0xFFFFFF);
            }
        }

        if (names.isEmpty()) {
            drawCenteredString(matrices, this.textRenderer, "Waiting for players...", this.width / 2, startY + 8, 0xAAAAAA);
        } else if (visibleCount < names.size()) {
            String more = "+" + (names.size() - visibleCount) + " more players";
            this.textRenderer.drawWithShadow(matrices, more, this.width - this.textRenderer.getWidth(more) - 12, maxY - 10, 0xAAAAAA);
        }
    }

    private void drawRoomDetails(MatrixStack matrices, String seed, int y) {
        String filter = "Filter: " + ZsgSeedBridge.seedTypeLabel(ZsgSeedBridge.resolveStructure(seed));
        if (isCompact()) {
            drawCenteredString(matrices, this.textRenderer, trimToWidth(filter, this.width - 20), this.width / 2, y + 4, 0x88CCFF);
            return;
        }
        int panelX = Math.max(20, this.width / 2 - 190);
        int panelW = Math.min(380, this.width - 40);
        fill(matrices, panelX, y, panelX + panelW, y + 40, 0x66000000);
        drawCenteredString(matrices, this.textRenderer, trimToWidth(filter, panelW - 16), this.width / 2, y + 6, 0x88CCFF);
        drawCenteredString(matrices, this.textRenderer, trimToWidth("Seed Source: " + ZsgSeedBridge.getLastSeedSource(), panelW - 16), this.width / 2, y + 21, 0x88FF88);
    }

    private void drawChatAndStatus(MatrixStack matrices, Room room, int top) {
        int chatTop = top + 1;
        int chatBottom = chatBottom();
        fill(matrices, 0, chatTop, this.width, footerTop(), 0x88000000);
        drawCenteredString(matrices, this.textRenderer, "Waiting for host to start...", this.width / 2, chatTop + 7, 0xFFFFFF);

        int chatX = 14;
        int chatPanelTop = chatTop + 25;
        int chatPanelRight = Math.min(this.width - 10, 520);
        fill(matrices, 10, chatPanelTop - 4, chatPanelRight, chatBottom + 2, 0x55000000);
        this.textRenderer.drawWithShadow(matrices, "Room Chat", chatX, chatPanelTop - 14, 0xA8D8FF);

        if (room != null) {
            List<String> messages = room.getRoomMessages();
            int visibleLines = Math.max(1, (chatBottom - chatPanelTop) / 11);
            this.chatScrollOffset = Math.min(this.chatScrollOffset, maxChatScrollOffset(room));
            int end = Math.max(0, messages.size() - this.chatScrollOffset);
            int start = Math.max(0, end - visibleLines);
            int chatY = chatPanelTop;

            for (int i = start; i < end; i++) {
                this.textRenderer.drawWithShadow(matrices, trimToWidth(messages.get(i), chatPanelRight - chatX - 16), chatX, chatY, 0xD8D8D8);
                chatY += 11;
            }
            if (this.chatScrollOffset > 0) {
                this.textRenderer.drawWithShadow(matrices, "v", chatPanelRight - 16, chatBottom - 10, 0x88FF88);
            }
            if (start > 0) {
                this.textRenderer.drawWithShadow(matrices, "^", chatPanelRight - 16, chatPanelTop, 0x88FF88);
            }
        }
    }

    private void sendChatMessage() {
        String message = this.chatField.getText().trim();
        if (!message.isEmpty()) {
            ZsgRoomsClient.sendRoomAction("chat", this.roomName, message);
            this.chatField.setText("");
            this.chatScrollOffset = 0;
        }
    }

    private boolean isMouseOverChat(double mouseX, double mouseY) {
        return mouseX >= 10 && mouseX <= Math.min(this.width - 10, 520) && mouseY >= chatTop() + 20 && mouseY <= chatBottom();
    }

    private int maxChatScrollOffset(Room room) {
        if (room == null) {
            return 0;
        }
        int visibleLines = Math.max(1, (chatBottom() - (chatTop() + 26)) / 11);
        return Math.max(0, room.getRoomMessages().size() - visibleLines);
    }

    private ButtonWidget addLobbyButton(int x, int y, int width, String label, String action) {
        ButtonWidget widget = new ButtonWidget(x, y, width, 20, new LiteralText(label), button -> {
            if ("leave".equals(action)) {
                ZsgRoomsClient.sendRoomAction("leave_room", this.roomName, "");
                RoomWebSocketTransport.stop();
                RoomSocketTransport.stop();
                ZsgRooms.leaveRoomLocally(this.roomName);
                this.client.openScreen(this.parent);
            } else if ("options".equals(action)) {
                this.client.openScreen(new RoomOptionsScreen(this, this.roomName));
            } else if ("start".equals(action)) {
                ZsgRoomsClient.sendRoomAction("start", this.roomName, "");
            } else if ("share".equals(action)) {
                ZsgRoomsClient.sendRoomAction("share_seed", this.roomName, "");
            }
        });
        this.addButton(widget);
        return widget;
    }

    private void updateHostControls() {
        Room room = ZsgRooms.getRoom(this.roomName);
        String localName = ZsgRoomsClient.localPlayerName(this.client);
        boolean isHost = room != null && room.host != null && localName.equals(room.host.getName());
        if (this.optionsButton != null) {
            this.optionsButton.active = isHost;
        }
        if (this.startButton != null) {
            this.startButton.active = isHost;
        }
    }

    private boolean isCompact() {
        return this.width < 560 || this.height < 360;
    }

    private int headerHeight() {
        return isCompact() ? 47 : 44;
    }

    private int footerTop() {
        return this.height - (isCompact() ? 58 : 48);
    }

    private int chatTop() {
        int chatHeight = isCompact() ? Math.min(72, Math.max(58, this.height / 3)) : Math.min(126, Math.max(90, this.height / 3));
        return Math.max(headerHeight() + (isCompact() ? 56 : 90), footerTop() - chatHeight);
    }

    private int chatBottom() {
        return isCompact() ? footerTop() - 5 : footerTop() - 32;
    }

    private String trimToWidth(String text, int width) {
        if (text == null) {
            return "";
        }
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = text;
        while (trimmed.length() > 0 && this.textRenderer.getWidth(trimmed + ellipsis) > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private int avatarColor(String name) {
        int hash = name == null ? 0 : name.hashCode();
        int red = 80 + Math.abs(hash & 0x7F);
        int green = 80 + Math.abs((hash >> 8) & 0x7F);
        int blue = 80 + Math.abs((hash >> 16) & 0x7F);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private void updateChatSuggestion() {
        if (this.chatField != null) {
            this.chatField.setSuggestion(this.chatField.getText().isEmpty() ? "Type a message..." : "");
        }
    }
}
