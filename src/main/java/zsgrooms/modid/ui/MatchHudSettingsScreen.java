package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MatchHudSettingsScreen extends Screen {
    private final Screen parent;
    private final MatchHudPreferences settings = RoomUiPreferences.getMatchHud();
    private final Map<String, Integer> previewProgress = new HashMap<String, Integer>();
    private final Map<String, String> previewLabels = new HashMap<String, String>();
    private final long previewStart = System.nanoTime();
    private Player[] previewPlayers;
    private boolean playersTab;
    private boolean previewTab;
    private int offset;
    private int left;
    private int controlsWidth;

    public MatchHudSettingsScreen(Screen parent) {
        super(new LiteralText("Match HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.controlsWidth = Math.min(224, this.width - 32);
        this.left = hasPreview(this.width) ? (this.width - 480) / 2 : (this.width - this.controlsWidth) / 2;
        if (hasPreview(this.width)) {
            previewTab = false;
        }
        offset = Math.min(offset, Math.max(0, optionCount() - capacity(this.height)));
        int tabWidth = hasPreview(this.width) ? (this.controlsWidth - 4) / 2 : (this.controlsWidth - 8) / 3;
        ButtonWidget appearance = this.addButton(new ButtonWidget(left, 30, tabWidth, 20,
                new LiteralText("Appearance"), button -> selectTab(false)));
        ButtonWidget players = this.addButton(new ButtonWidget(left + tabWidth + 4, 30, tabWidth, 20,
                new LiteralText("Players"), button -> selectTab(true)));
        appearance.active = previewTab || playersTab;
        players.active = previewTab || !playersTab;
        if (!hasPreview(this.width)) {
            ButtonWidget preview = this.addButton(new ButtonWidget(left + (tabWidth + 4) * 2, 30, tabWidth, 20,
                    new LiteralText("Preview"), button -> {
                        previewTab = true;
                        offset = 0;
                        refresh();
                    }));
            preview.active = !previewTab;
        }
        if (!previewTab && playersTab) {
            slider(0, "Visible players", settings.pinSelf ? 2 : 1, 4, 1, settings.rows,
                    "", value -> settings.rows = value);
            slider(1, "Rotate every", 2, 10, 1, settings.rotationSeconds,
                    "s", value -> settings.rotationSeconds = value);
            checkbox(2, "Keep my row visible", () -> settings.pinSelf, value -> {
                settings.pinSelf = value;
                if (value) {
                    settings.rows = Math.max(2, settings.rows);
                }
                refresh();
            });
            position(3, RoomUiPreferences.HudPosition.TOP_LEFT, RoomUiPreferences.HudPosition.TOP_RIGHT);
            position(4, RoomUiPreferences.HudPosition.BOTTOM_LEFT, RoomUiPreferences.HudPosition.BOTTOM_RIGHT);
        } else if (!previewTab) {
            checkbox(0, "Show match HUD", () -> settings.visible, value -> settings.visible = value);
            checkbox(1, "Show header", () -> settings.header, value -> settings.header = value);
            checkbox(2, "Show player heads", () -> settings.heads, value -> settings.heads = value);
            checkbox(3, "Show progress numbers", () -> settings.progressNumbers, value -> settings.progressNumbers = value);
            slider(4, "Background", 0, 100, 5, settings.opacity, "%", value -> settings.opacity = value);
            slider(5, "Size", 75, 150, 5, settings.scale, "%", value -> settings.scale = value);
        }
        this.addButton(new ButtonWidget(this.width / 2 - 60, this.height - 28, 120, 20,
                new LiteralText("Done"), button -> onClose()));
        if (optionCount() > capacity(this.height)) {
            ButtonWidget previous = this.addButton(new ButtonWidget(12, this.height - 28, 20, 20,
                    new LiteralText("<"), button -> scroll(-1)));
            ButtonWidget next = this.addButton(new ButtonWidget(this.width - 32, this.height - 28, 20, 20,
                    new LiteralText(">"), button -> scroll(1)));
            previous.active = offset > 0;
            next.active = offset + capacity(this.height) < optionCount();
        }
        if (previewPlayers == null) {
            String local = this.client.getSession().getUsername();
            previewPlayers = new Player[] {
                    new Player(local, this.client.getSession().getUuid(), true, true),
                    new Player("Runner 2", true, false), new Player("Runner 3", true, false),
                    new Player("Runner 4", true, false), new Player("Runner 5", true, false)
            };
            String[] labels = {"Entered Nether", "Entered Bastion", "Found Fortress", "Found Stronghold", "Entered End"};
            int[] stages = {4, 5, 5, 6, 7};
            for (int i = 0; i < previewPlayers.length; i++) {
                previewProgress.put(previewPlayers[i].getName(), stages[i]);
                previewLabels.put(previewPlayers[i].getName(), labels[i]);
            }
        }
    }

    static boolean hasPreview(int width) {
        return width >= 500;
    }

    static int capacity(int height) {
        return Math.max(1, (height - 88) / 24);
    }

    private int optionCount() {
        return previewTab ? 0 : playersTab ? 5 : 6;
    }

    private boolean shown(int index) {
        return index >= offset && index < offset + capacity(this.height);
    }

    private int optionY(int index) {
        return 58 + (index - offset) * 24;
    }

    private void checkbox(int index, String text, BooleanSupplier getter, Consumer<Boolean> setter) {
        if (!shown(index)) {
            return;
        }
        this.addButton(new CheckboxWidget(left, optionY(index), controlsWidth, 20,
                new LiteralText(text), getter.getAsBoolean()) {
            @Override
            public void onPress() {
                super.onPress();
                setter.accept(isChecked());
            }
        });
    }

    private void slider(int index, String label, int min, int max, int step, int current,
                        String suffix, IntConsumer setter) {
        if (!shown(index)) {
            return;
        }
        this.addButton(new SliderWidget(left, optionY(index), controlsWidth, 20,
                new LiteralText(label + ": " + current + suffix), (current - min) / (double) (max - min)) {
            private int selected() {
                return min + (int) Math.round(this.value * (max - min) / step) * step;
            }

            @Override
            protected void updateMessage() {
                setMessage(new LiteralText(label + ": " + selected() + suffix));
            }

            @Override
            protected void applyValue() {
                setter.accept(selected());
            }
        });
    }

    private void position(int index, RoomUiPreferences.HudPosition first, RoomUiPreferences.HudPosition second) {
        if (!shown(index)) {
            return;
        }
        int width = (controlsWidth - 4) / 2;
        positionButton(left, optionY(index), width, first);
        positionButton(left + width + 4, optionY(index), width, second);
    }

    private void positionButton(int x, int y, int width, RoomUiPreferences.HudPosition position) {
        ButtonWidget button = this.addButton(new ButtonWidget(x, y, width, 20,
                new LiteralText(position.getLabel()), pressed -> {
                    RoomUiPreferences.setHudPosition(position);
                    refresh();
                }));
        button.active = RoomUiPreferences.getHudPosition() != position;
    }

    private void selectTab(boolean players) {
        previewTab = false;
        playersTab = players;
        offset = 0;
        refresh();
    }

    private void refresh() {
        this.init(this.client, this.width, this.height);
    }

    private void scroll(int direction) {
        offset = Math.max(0, Math.min(Math.max(0, optionCount() - capacity(this.height)),
                offset + direction * capacity(this.height)));
        refresh();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount != 0 && optionCount() > capacity(this.height)) {
            scroll(amount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredString(matrices, this.textRenderer, "Match HUD", this.width / 2, 10, 0xFFFFFF);
        if (hasPreview(this.width) || previewTab) {
            int x = previewTab ? 16 : left + controlsWidth + 24;
            int width = previewTab ? this.width - 32 : 480 - controlsWidth - 24;
            if (!previewTab) {
                drawCenteredString(matrices, this.textRenderer, "Preview", x + width / 2, 36, 0xA8D8FF);
            }
            if (settings.visible) {
                int height = MatchHud.panelHeight(settings, settings.rows);
                float scale = MatchHud.fitScale(settings.scale, width, this.height - 100, height);
                MatchHud.drawPanel(matrices, this.client, settings, previewPlayers, previewProgress, previewLabels,
                        this.client.getSession().getUsername(), x + (width - 166 * scale) / 2, 64, scale,
                        (System.nanoTime() - previewStart) / 1_000_000L);
            } else {
                drawCenteredString(matrices, this.textRenderer, "Hidden", x + width / 2, 80, 0xAAAAAA);
            }
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        RoomUiPreferences.saveMatchHud();
    }

    @Override
    public void onClose() {
        this.client.openScreen(this.parent);
    }
}
