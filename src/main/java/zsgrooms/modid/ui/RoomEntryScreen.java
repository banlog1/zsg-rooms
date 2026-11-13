package zsgrooms.modid.ui;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.net.RoomWebSocketTransport;

/** Code-only entry with a cancellable relay handshake and no fabricated guest room. */
public final class RoomEntryScreen extends Screen {
    private enum State { CODE, STARTING, CONNECTING, ERROR }
    private final Screen parent;
    private final boolean creating, preview;
    private State state;
    private String endpoint = RoomWebSocketTransport.getRelayUrl(), code = "", validation = "";
    private boolean visibleCode, handedOff;
    private TextFieldWidget codeField;
    private RoomEntryButton joinButton;
    private RoomWebSocketTransport.ConnectionAttempt attempt;
    private RoomEntryError error;
    private long acceptedAt;
    int left, top, formWidth, formHeight;

    public RoomEntryScreen(Screen parent, boolean creating) { this(parent, creating, false); }
    private RoomEntryScreen(Screen parent, boolean creating, boolean preview) {
        super(new LiteralText(creating ? "Create room" : "Join a room"));
        this.parent = parent; this.creating = creating; this.preview = preview;
        state = creating ? State.STARTING : State.CODE;
    }

    static RoomEntryScreen forTutorial(Screen parent) { return new RoomEntryScreen(parent, false, true); }

    @Override protected void init() {
        codeField = null; joinButton = null;
        formWidth = Math.min(320, width - 24);
        formHeight = state == State.CODE ? 190 : 194;
        left = (width - formWidth) / 2; top = Math.max(8, (height - formHeight) / 2);
        int x = left + 16, w = formWidth - 32;
        if (state == State.CODE) {
            int inputWidth = w;
            codeField = addButton(new TextFieldWidget(textRenderer, x, top + 64, inputWidth - 56, 24, new LiteralText("Room Code")));
            codeField.setMaxLength(64); codeField.setText(code);
            codeField.setRenderTextProvider((value, offset) -> visibleCode ? value : value.replaceAll(".", "*"));
            codeField.setChangedListener(value -> { code = value; validation = ""; if (joinButton != null) joinButton.active = RoomEntryDefaults.validCode(value.trim()); });
            addButton(new RoomEntryButton(x + inputWidth - 52, top + 64, 24, 24, "Show / hide code", RoomEntryButton.Icon.EYE, false,
                    () -> visibleCode = !visibleCode));
            addButton(new RoomEntryButton(x + inputWidth - 24, top + 64, 24, 24, "Paste room code", RoomEntryButton.Icon.PASTE, false,
                    () -> { codeField.setText(client.keyboard.getClipboard().trim()); codeField.setCursorToEnd(); }));
            joinButton = addButton(new RoomEntryButton(x, top + 106, w, 24,
                    "Join", RoomEntryButton.Icon.NONE, true, this::start));
            joinButton.active = RoomEntryDefaults.validCode(code.trim());
            setInitialFocus(codeField);
        } else if (state == State.ERROR) {
            addButton(new RoomEntryButton(x, top + 118, w, 24, "Retry", RoomEntryButton.Icon.NONE, true, this::start));
        }
        int footer = top + formHeight - 36;
        addButton(new RoomEntryButton(x, footer, 28, 22, state == State.CONNECTING || state == State.STARTING ? "Cancel" : "Back",
                RoomEntryButton.Icon.BACK, false, this::onClose));
        if (state != State.CONNECTING && state != State.STARTING) {
            addButton(new RoomEntryButton(x + w - 150, footer, 150, 22, "Connection settings", RoomEntryButton.Icon.NONE, false,
                    () -> client.openScreen(new RelaySettingsScreen(this, endpoint, selected -> endpoint = selected))));
        }
    }

    private void start() {
        if (preview || state == State.CONNECTING) return;
        if (!creating && !RoomEntryDefaults.validCode(code.trim())) { validation = "Enter a valid room code."; return; }
        Room existing = ZsgRooms.getActiveRoom();
        if (existing != null) {
            client.openScreen(new RoomLobbyScreen(parent, existing.roomName));
            return;
        }
        if (creating) {
            code = RoomEntryDefaults.newCode();
            RoomEntryDefaults.create(code, ZsgRoomsClient.localPlayerName(client), ZsgRoomsClient.localPlayerUuid(client));
        } else code = code.trim();
        state = State.CONNECTING; error = null; acceptedAt = 0;
        attempt = RoomWebSocketTransport.beginConnection(endpoint, code, ZsgRoomsClient.localPlayerName(client), creating);
        init(client, width, height);
    }

    @Override public void tick() {
        if (codeField != null) codeField.tick();
        if (preview) return;
        if (state == State.STARTING) { start(); return; }
        if (state != State.CONNECTING || attempt == null || !attempt.isDone()) return;
        if (!attempt.isConnected()) { fail(attempt.error()); return; }
        if (acceptedAt == 0) acceptedAt = System.nanoTime();
        Room room = ZsgRooms.getRoom(code);
        String player = ZsgRoomsClient.localPlayerName(client);
        if (room != null && room.getPlayer(player) != null && attempt.activate()) {
            ZsgRoomsClient.sendRoomAction("profile", code, ZsgRoomsClient.localPlayerUuid(client));
            handedOff = true;
            client.openScreen(new RoomLobbyScreen(parent, code));
        } else if (System.nanoTime() - acceptedAt > 10_000_000_000L) fail("Room host did not send a room snapshot");
    }

    private void fail(String reason) {
        cleanup(); error = RoomEntryError.from(reason, creating); state = State.ERROR;
        init(client, width, height);
    }

    private void cleanup() {
        if (attempt != null) { attempt.cancel(); attempt = null; ZsgRooms.leaveRoomLocally(code); }
    }

    @Override public void removed() {
        if (!handedOff && state == State.CONNECTING) {
            cleanup();
            error = RoomEntryError.cancelled();
            state = State.ERROR;
        }
    }
    @Override public void onClose() {
        cleanup();
        if (!creating && state == State.ERROR) {
            state = State.CODE; error = null;
            init(client, width, height);
        } else client.openScreen(parent);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (state == State.CODE || state == State.ERROR) start();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void render(MatrixStack m, int mx, int my, float delta) {
        renderBackground(m); fill(m, 0, 0, width, height, 0xB5101010);
        int x = left + 16;
        textRenderer.drawWithShadow(m, "ZSG ROOMS", x, top + 14, 0xDDDDDD);
        String heading = state == State.ERROR ? error.title : state == State.CONNECTING || state == State.STARTING
                ? creating ? "Creating room..." : "Joining room..." : "Join a room";
        textRenderer.drawWithShadow(m, heading, x, top + 31, 0xF0F0F0);
        if (state == State.CODE) {
            textRenderer.drawWithShadow(m, "Room code", x, top + 51, 0xB8B8B8);
            if (!validation.isEmpty()) textRenderer.drawWithShadow(m, validation, x, top + 91, 0xFFB3A8);
        } else if (state == State.ERROR) {
            int y = top + 56;
            for (StringRenderable line : textRenderer.wrapLines(new LiteralText(error.detail), formWidth - 32)) {
                textRenderer.drawWithShadow(m, line, x, y, 0xB8B8B8); y += 11;
            }
        } else {
            textRenderer.drawWithShadow(m, creating ? "Connecting to the room service" : "Waiting for the host's room", x, top + 62, 0xB8B8B8);
            int phase = (int) (System.nanoTime() / 180_000_000L % 5);
            for (int i = 0; i < 5; i++) fill(m, x + i * 12, top + 90, x + i * 12 + 6, top + 96, i == phase ? 0xFFDDDDDD : 0xFF454545);
        }
        super.render(m, mx, my, delta);
        for (Element e : children()) if (e instanceof RoomEntryButton) {
            RoomEntryButton b = (RoomEntryButton) e;
            if (b.isHovered() && b.icon != RoomEntryButton.Icon.NONE) renderTooltip(m, b.getMessage(), mx, my);
        }
    }
}
