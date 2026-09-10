package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.replay.ReplayPreferences;
import zsgrooms.modid.replay.ReplayPrototype;

import java.util.UUID;

public final class ReplayTestSettingsScreen extends Screen {
    private final Screen parent;
    private String draft = ReplayPrototype.getPreferences().soloTestGroup;
    private String status = "";
    private TextFieldWidget group;
    private ButtonWidget apply;
    private ButtonWidget disable;
    private ButtonWidget help;

    public ReplayTestSettingsScreen(Screen parent) {
        super(new LiteralText("Solo Replay Testing"));
        this.parent = parent;
    }

    @Override protected void init() {
        int width = Math.min(360, this.width - 32);
        int x = (this.width - width) / 2;
        group = this.addButton(new TextFieldWidget(textRenderer, x, 64, width, 20, new LiteralText("Test group ID")));
        group.setMaxLength(36);
        group.setText(draft);
        group.setChangedListener(value -> draft = value);
        int half = (width - 6) / 2;
        this.addButton(new ButtonWidget(x, 90, half, 20, new LiteralText("New Group"), b -> group.setText(UUID.randomUUID().toString())));
        this.addButton(new ButtonWidget(x + half + 6, 90, half, 20, new LiteralText("Copy ID"), b -> client.keyboard.setClipboard(draft)));
        apply = this.addButton(new ButtonWidget(x, 116, half, 20, new LiteralText("Apply"), b -> {
            status = ReplayPrototype.configureTestGroup(draft) ? "Test group saved" : "Could not save test group";
        }));
        disable = this.addButton(new ButtonWidget(x + half + 6, 116, half, 20, new LiteralText("Disable Testing"), b -> {
            if (ReplayPrototype.configureTestGroup("")) { group.setText(""); status = "Testing off"; }
            else status = "Could not save test group";
        }));
        help = this.addButton(new ButtonWidget(this.width / 2 + width / 2 - 20, 12, 20, 20, new LiteralText("?"), b -> {}));
        this.addButton(new ButtonWidget(this.width / 2 - 60, this.height - 28, 120, 20, new LiteralText("Done"), b -> onClose()));
    }

    @Override public void tick() { group.tick(); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), this.width / 2, 16, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, "Test group ID", this.width / 2, 48, 0xA8D8FF);
        boolean valid;
        try { ReplayPreferences.normalizeTestGroup(draft); valid = true; }
        catch (IllegalArgumentException error) { valid = false; }
        apply.active = valid && ReplayPrototype.canConfigure();
        disable.active = ReplayPrototype.canConfigure();
        String text = !valid ? "Enter a valid group UUID" : !status.isEmpty() ? status
                : ReplayPrototype.getPreferences().soloTestGroup.isEmpty() ? "Testing off" : "Solo test grouping active";
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(text, this.width - 32), this.width / 2, 148, 0xA8D8FF);
        super.render(matrices, mouseX, mouseY, delta);
        if (help.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Create a group, then Apply. Record separate attempts in a one-player room with the same manual seed. Return to the room to save each replay. Keep this ID for all takes. Open a replay and choose Players to compare them. This changes replay metadata only, never live race IDs or results. Multiplayer rooms ignore it."), Math.min(300, this.width - 32)), mouseX, mouseY);
    }

    @Override public void onClose() { client.openScreen(parent); }
}
