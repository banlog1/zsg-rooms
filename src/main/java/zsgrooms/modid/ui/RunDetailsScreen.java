package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.SpeedRunIgtBridge;
import zsgrooms.modid.history.CompletedRun;
import zsgrooms.modid.history.RunSplit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RunDetailsScreen extends Screen {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final int MIN_SEGMENT_WIDTH = 104;

    private final Screen parent;
    private final CompletedRun run;
    private int firstVisibleSplit;

    public RunDetailsScreen(Screen parent, CompletedRun run) {
        super(new LiteralText("Run Splits"));
        this.parent = parent;
        this.run = run;
    }

    @Override
    protected void init() {
        int panelY = panelY();
        int footerY = panelY + panelHeight() - 30;
        int centerX = this.width / 2;
        this.addButton(new ButtonWidget(centerX - 80, footerY, 160, 20, new LiteralText("Back"),
                button -> this.client.openScreen(this.parent)));
        if (run.getSplits().size() > visibleSplitCount()) {
            this.addButton(new ButtonWidget(panelX() + 16, footerY, 24, 20, new LiteralText("<"), button -> scroll(-1)));
            this.addButton(new ButtonWidget(panelX() + panelWidth() - 40, footerY, 24, 20, new LiteralText(">"), button -> scroll(1)));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (run.getSplits().size() > visibleSplitCount()) {
            scroll(amount < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void scroll(int amount) {
        int max = Math.max(0, run.getSplits().size() - visibleSplitCount());
        this.firstVisibleSplit = Math.max(0, Math.min(max, this.firstVisibleSplit + amount));
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

        drawCenteredString(matrices, this.textRenderer, run.getFilterLabel(), this.width / 2, panelY + 10, 0xFFFFFF);
        String finalTime = run.getIgtMilliseconds() > 0L
                ? SpeedRunIgtBridge.formatMilliseconds(run.getIgtMilliseconds()) : "IGT unavailable";
        drawCenteredString(matrices, this.textRenderer,
                DATE_FORMAT.format(new Date(run.getCompletedAt())) + "  |  Final IGT: " + finalTime,
                this.width / 2, panelY + 36, 0xA8D8FF);
        renderTimeline(matrices, panelX + 16, panelY + 82, panelWidth() - 32);
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void renderTimeline(MatrixStack matrices, int x, int y, int width) {
        List<RunSplit> splits = run.getSplits();
        if (splits.isEmpty()) {
            drawCenteredString(matrices, this.textRenderer, "No split data was recorded", this.width / 2, y + 24, 0xAAAAAA);
            return;
        }
        int visible = Math.min(visibleSplitCount(), splits.size() - firstVisibleSplit);
        boolean proportional = splits.size() <= visibleSplitCount();
        int segmentWidth = Math.max(MIN_SEGMENT_WIDTH, width / Math.max(1, visible));
        int minimumProportionalWidth = proportional ? Math.min(72, width / visible) : segmentWidth;
        int flexibleWidth = proportional ? Math.max(0, width - minimumProportionalWidth * visible) : 0;
        long visibleStart = firstVisibleSplit == 0 ? 0L : splits.get(firstVisibleSplit - 1).getIgtMilliseconds();
        long visibleDuration = Math.max(1L,
                splits.get(firstVisibleSplit + visible - 1).getIgtMilliseconds() - visibleStart);
        int cursor = x;
        long previous = firstVisibleSplit == 0 ? 0L : splits.get(firstVisibleSplit - 1).getIgtMilliseconds();
        for (int offset = 0; offset < visible; offset++) {
            RunSplit split = splits.get(firstVisibleSplit + offset);
            long duration = Math.max(0L, split.getIgtMilliseconds() - previous);
            previous = split.getIgtMilliseconds();
            int currentWidth = proportional
                    ? minimumProportionalWidth + (int) (flexibleWidth * duration / visibleDuration)
                    : segmentWidth;
            int left = cursor;
            int right = offset == visible - 1 ? x + width : Math.min(x + width, left + currentWidth);
            cursor = right;

            fill(matrices, left, y, right, y + 46, splitColor(split.getLabel()));
            fill(matrices, left, y, left + 1, y + 46, 0xFF111111);
            String label = this.textRenderer.trimToWidth(split.getLabel(), Math.max(8, right - left - 8));
            String interval = SpeedRunIgtBridge.formatMilliseconds(duration);
            String cumulative = SpeedRunIgtBridge.formatMilliseconds(split.getIgtMilliseconds());
            drawCenteredString(matrices, this.textRenderer, label, (left + right) / 2, y - 16, 0x6DE8E8);
            drawCenteredString(matrices, this.textRenderer, interval, (left + right) / 2, y + 19, 0xFFFFFF);
            drawCenteredString(matrices, this.textRenderer, cumulative, (left + right) / 2, y + 53, 0xAAAAAA);
        }
        fill(matrices, x, y + 46, x + width, y + 47, 0xFF000000);
        drawCenteredString(matrices, this.textRenderer, "Segment time", this.width / 2, y + 72, 0x777777);
    }

    private int visibleSplitCount() {
        return Math.max(1, (panelWidth() - 32) / MIN_SEGMENT_WIDTH);
    }

    private int splitColor(String label) {
        if (label.contains("Nether")) return 0xFF426536;
        if (label.contains("Bastion") || label.contains("Fortress")) return 0xFF73352F;
        if (label.contains("Stronghold")) return 0xFF4C4F57;
        if (label.contains("End")) return 0xFF603A78;
        return 0xFF8A8132;
    }

    @Override
    public void onClose() {
        this.client.openScreen(this.parent);
    }

    private int panelWidth() {
        return Math.min(760, this.width - 20);
    }

    private int panelHeight() {
        return Math.min(250, this.height - 20);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(10, (this.height - panelHeight()) / 2);
    }
}
