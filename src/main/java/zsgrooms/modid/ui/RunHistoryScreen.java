package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.SpeedRunIgtBridge;
import zsgrooms.modid.history.CompletedRun;
import zsgrooms.modid.history.RunHistoryTracker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RunHistoryScreen extends Screen {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final Screen parent;
    private List<CompletedRun> runs;
    private int scrollOffset;

    public RunHistoryScreen(Screen parent) {
        super(new LiteralText("Recent Runs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.runs = RunHistoryTracker.getRecentRuns();
        int panelX = panelX();
        int panelY = panelY();
        int visibleRows = visibleRows();
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, Math.max(0, this.runs.size() - visibleRows)));

        int first = this.scrollOffset;
        int last = Math.min(this.runs.size(), first + visibleRows);
        for (int index = first; index < last; index++) {
            CompletedRun run = this.runs.get(index);
            int rowY = panelY + 48 + (index - first) * 38;
            this.addButton(new RunEntryButton(panelX + 16, rowY, panelWidth() - 32, run,
                    button -> this.client.openScreen(new RunDetailsScreen(this, run))));
        }

        this.addButton(new ButtonWidget(this.width / 2 - 80, panelY + panelHeight() - 30, 160, 20,
                new LiteralText("Back"), button -> this.client.openScreen(this.parent)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxOffset = Math.max(0, this.runs.size() - visibleRows());
        int next = Math.max(0, Math.min(maxOffset, this.scrollOffset + (amount < 0 ? 1 : -1)));
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            this.init(this.client, this.width, this.height);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);
        int panelX = panelX();
        int panelY = panelY();
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + panelHeight(), 0xDD090909);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 30, 0xCC1A120C);
        fill(matrices, panelX, panelY + 30, panelX + panelWidth(), panelY + 31, 0xFF000000);

        drawCenteredString(matrices, this.textRenderer, "Recent Completed Runs", this.width / 2, panelY + 11, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, "Cheat-free room completions", this.width / 2, panelY + 34, 0xA8D8FF);
        if (this.runs.isEmpty()) {
            drawCenteredString(matrices, this.textRenderer, "No completed runs yet", this.width / 2,
                    panelY + panelHeight() / 2, 0xAAAAAA);
        } else if (this.runs.size() > visibleRows()) {
            String page = (this.scrollOffset + 1) + "-"
                    + Math.min(this.runs.size(), this.scrollOffset + visibleRows()) + " of " + this.runs.size();
            this.textRenderer.drawWithShadow(matrices, page, panelX + panelWidth() - 16 - this.textRenderer.getWidth(page),
                    panelY + 35, 0x888888);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.client.openScreen(this.parent);
    }

    private int visibleRows() {
        return Math.max(1, (panelHeight() - 86) / 38);
    }

    private int panelWidth() {
        return Math.min(560, this.width - 20);
    }

    private int panelHeight() {
        return Math.min(326, this.height - 20);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(10, (this.height - panelHeight()) / 2);
    }

    private static final class RunEntryButton extends ButtonWidget {
        private final CompletedRun run;

        private RunEntryButton(int x, int y, int width, CompletedRun run, PressAction onPress) {
            super(x, y, width, 34, new LiteralText(""), onPress);
            this.run = run;
        }

        @Override
        public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            MinecraftClient client = MinecraftClient.getInstance();
            boolean hovered = this.isHovered();
            int borderColor = hovered ? 0xFFB8DFFF : 0xFF606060;
            int backgroundColor = hovered ? 0xEE242A2E : 0xEE171717;
            fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, borderColor);
            fill(matrices, this.x + 1, this.y + 1, this.x + this.width - 1,
                    this.y + this.height - 1, backgroundColor);
            fill(matrices, this.x + 1, this.y + 1, this.x + 3,
                    this.y + this.height - 1, hovered ? 0xFF6FC8FF : 0xFF477D9D);

            String igt = run.getIgtMilliseconds() > 0L
                    ? SpeedRunIgtBridge.formatMilliseconds(run.getIgtMilliseconds()) + " IGT"
                    : "IGT unavailable";
            int igtWidth = client.textRenderer.getWidth(igt);
            int textLeft = this.x + 10;
            int textRight = this.x + this.width - 10;
            String filter = client.textRenderer.trimToWidth(run.getFilterLabel(),
                    Math.max(20, this.width - 30 - igtWidth));
            int primaryColor = hovered ? 0xFFFFFF : 0xE2E2E2;
            client.textRenderer.drawWithShadow(matrices, filter, textLeft, this.y + 6, primaryColor);
            client.textRenderer.drawWithShadow(matrices, igt, textRight - igtWidth, this.y + 6, 0x8ED8FF);

            String splitCount = run.getSplits().size() == 1
                    ? "1 split" : run.getSplits().size() + " splits";
            int splitWidth = client.textRenderer.getWidth(splitCount);
            String completedAt = DATE_FORMAT.format(new Date(run.getCompletedAt()));
            completedAt = client.textRenderer.trimToWidth(completedAt,
                    Math.max(20, this.width - 30 - splitWidth));
            client.textRenderer.drawWithShadow(matrices, completedAt, textLeft, this.y + 19, 0x9A9A9A);
            client.textRenderer.drawWithShadow(matrices, splitCount,
                    textRight - splitWidth, this.y + 19, 0x8F8F8F);
        }
    }
}
