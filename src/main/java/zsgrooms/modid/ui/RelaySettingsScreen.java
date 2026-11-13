package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.net.RoomWebSocketTransport;

import java.util.function.Consumer;

public final class RelaySettingsScreen extends Screen {
    private static final String GUIDE = "https://github.com/banlog1/zsg-rooms/blob/main/relay/README.md";
    private final Screen parent;
    private final Consumer<String> selected;
    private String draft, error = "";
    private TextFieldWidget address;
    private int left, top, panelWidth;

    public RelaySettingsScreen(Screen parent, String initial, Consumer<String> selected) {
        super(new LiteralText("Connection settings"));
        this.parent = parent; this.draft = initial; this.selected = selected;
    }

    @Override protected void init() {
        if (address != null) draft = address.getText();
        panelWidth = Math.min(360, width - 24); left = (width - panelWidth) / 2; top = Math.max(8, (height - 208) / 2);
        address = addButton(new TextFieldWidget(textRenderer, left + 14, top + 66, panelWidth - 28, 22, new LiteralText("Relay address")));
        address.setMaxLength(200); address.setText(draft);
        addButton(new RoomEntryButton(left + 14, top + 98, 110, 22, "Use default", RoomEntryButton.Icon.NONE, false,
                () -> address.setText(RoomWebSocketTransport.DEFAULT_RELAY_URL)));
        addButton(new RoomEntryButton(left + panelWidth - 124, top + 98, 110, 22, "Setup guide", RoomEntryButton.Icon.NONE, false,
                () -> client.openScreen(new ConfirmChatLinkScreen(accepted -> {
                    if (accepted) Util.getOperatingSystem().open(GUIDE);
                    client.openScreen(this);
                }, GUIDE, true))));
        addButton(new RoomEntryButton(left + 14, top + 172, 80, 22, "Back", RoomEntryButton.Icon.NONE, false, this::onClose));
        addButton(new RoomEntryButton(left + panelWidth - 94, top + 172, 80, 22, "Apply", RoomEntryButton.Icon.NONE, true, this::apply));
        setInitialFocus(address);
    }

    private void apply() {
        try {
            RoomWebSocketTransport.configureRelayUrl(address.getText());
            selected.accept(RoomWebSocketTransport.getRelayUrl());
            client.openScreen(parent);
        } catch (IllegalArgumentException invalid) { error = "Enter a valid relay hostname or URL."; }
    }

    @Override public void tick() { address.tick(); }
    @Override public void onClose() { client.openScreen(parent); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { apply(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public void render(MatrixStack m, int mx, int my, float delta) {
        renderBackground(m); fill(m, 0, 0, width, height, 0xB5101010);
        textRenderer.drawWithShadow(m, title, left + 14, top + 14, 0xF0F0F0);
        textRenderer.drawWithShadow(m, "Relay address", left + 14, top + 50, 0xB8B8B8);
        String message = error.isEmpty() ? "The default works for normal play. A custom relay must match your host's." : error;
        int y = top + 130;
        for (StringRenderable line : textRenderer.wrapLines(new LiteralText(message), panelWidth - 28)) {
            textRenderer.drawWithShadow(m, line, left + 14, y, error.isEmpty() ? 0xB8B8B8 : 0xFFB3A8); y += 10;
        }
        super.render(m, mx, my, delta);
    }
}
