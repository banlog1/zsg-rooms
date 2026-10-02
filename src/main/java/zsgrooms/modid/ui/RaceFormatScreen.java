package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Formatting;
import zsgrooms.modid.RaceSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class RaceFormatScreen extends Screen {
    private static final String SEED_HELP = "Range: 1-20. Default: 1 seed. Everyone plays the same ordered seeds and advances independently. "
            + "The race clock starts together and includes pauses, resets and later loading. Reset replays the current seed. "
            + "With 1 seed, Forfeit loses the race; there is no skip or time penalty. "
            + "With multiple seeds, Skip Seed advances with +30 minutes per skip, including the last seed. Leave Race is a DNF.";
    private static final String FINISHER_HELP = "Default: 1 finisher. This is separate from the number of seeds. "
            + "A finisher completes the whole sequence. Increase this for more placements; use the room's player count for everyone. "
            + "The limit is capped to the starting roster. Withdrawals do not count; the race also ends when nobody remains. "
            + "With 1 seed and 1 finisher, the last runner wins if all opponents forfeit. "
            + "Unfinished runners at the cutoff are unplaced. Close finishes may briefly settle. "
            + "In multi-seed races, play continues while someone can still beat a qualifying penalty-adjusted time. Exact totals tie.";

    private final Screen parent;
    private final BiConsumer<Integer, Integer> save;
    private int seeds;
    private int finishers;
    private final int maxFinishers;
    private TextFieldWidget seedField;
    private TextFieldWidget finisherField;
    private final List<ButtonWidget> arrows = new ArrayList<>();

    public RaceFormatScreen(Screen parent, int seeds, int finishers, int maxFinishers, BiConsumer<Integer, Integer> save) {
        super(new LiteralText("Race Format"));
        this.parent = parent;
        this.seeds = seeds;
        this.maxFinishers = Math.max(1, Math.min(64, maxFinishers));
        this.finishers = Math.min(finishers, this.maxFinishers);
        this.save = save;
    }

    public static LiteralText summary(int seeds, int finishers) {
        return new LiteralText(seeds + (seeds == 1 ? " seed, " : " seeds, ")
                + finishers + (finishers == 1 ? " finisher" : " finishers"));
    }

    @Override protected void init() {
        if (seedField != null) seeds = read(seedField, RaceSequence.MAX_SEEDS);
        if (finisherField != null) finishers = read(finisherField, maxFinishers);
        arrows.clear();
        seedField = row(top() + 40, seeds, RaceSequence.MAX_SEEDS, "Seeds per race");
        finisherField = row(top() + 78, finishers, maxFinishers, "Finishers");
        addButton(new ButtonWidget(width / 2 - 70, top() + 126, 140, 20, new LiteralText("Done"), b -> onClose()));
        updateArrows();
    }

    private TextFieldWidget row(int y, int value, int max, String label) {
        int right = width / 2 + panelWidth() / 2;
        TextFieldWidget field = new TextFieldWidget(textRenderer, right - 88, y, 38, 20, new LiteralText(label));
        field.setMaxLength(2);
        field.setTextPredicate(text -> text.isEmpty() || text.matches("[0-9]{1,2}")
                && Integer.parseInt(text) >= 1 && Integer.parseInt(text) <= max);
        field.setText(Integer.toString(value));
        addButton(field);
        ButtonWidget minus = new ButtonWidget(right - 114, y, 20, 20, new LiteralText("-"),
                b -> field.setText(Integer.toString(Math.max(1, read(field, max) - 1))));
        ButtonWidget plus = new ButtonWidget(right - 44, y, 20, 20, new LiteralText("+"),
                b -> field.setText(Integer.toString(Math.min(max, read(field, max) + 1))));
        arrows.add(minus);
        arrows.add(plus);
        addButton(minus);
        addButton(plus);
        addButton(new ButtonWidget(right - 18, y, 18, 20, new LiteralText("?"), b -> {}));
        return field;
    }

    private static int read(TextFieldWidget field, int max) {
        try { return Math.max(1, Math.min(max, Integer.parseInt(field.getText()))); }
        catch (NumberFormatException ignored) { return 1; }
    }

    @Override public void tick() {
        seedField.tick();
        finisherField.tick();
        updateArrows();
    }

    private void updateArrows() {
        arrows.get(0).active = read(seedField, RaceSequence.MAX_SEEDS) > 1;
        arrows.get(1).active = read(seedField, RaceSequence.MAX_SEEDS) < RaceSequence.MAX_SEEDS;
        arrows.get(2).active = read(finisherField, maxFinishers) > 1;
        arrows.get(3).active = read(finisherField, maxFinishers) < maxFinishers;
    }

    @Override public void onClose() {
        save.accept(read(seedField, RaceSequence.MAX_SEEDS), read(finisherField, maxFinishers));
        client.openScreen(parent);
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int left = (width - panelWidth()) / 2;
        drawCenteredString(matrices, textRenderer, title.asString(), width / 2, top() + 10, 0xFFFFFF);
        textRenderer.drawWithShadow(matrices, "Seeds per race", left, top() + 46, 0xFFFFFF);
        textRenderer.drawWithShadow(matrices, "Finishers", left, top() + 84, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
        boolean seedRow = mouseY >= top() + 40 && mouseY < top() + 60;
        boolean finisherRow = mouseY >= top() + 78 && mouseY < top() + 98;
        if (mouseX >= left && mouseX < left + panelWidth() && (seedRow || finisherRow)) {
            List<StringRenderable> lines = new ArrayList<>();
            lines.add(new LiteralText(seedRow ? "Seeds per race" : "Finishers before the race ends").formatted(Formatting.AQUA));
            lines.addAll(textRenderer.wrapLines(new LiteralText(seedRow ? SEED_HELP : FINISHER_HELP), Math.min(300, width - 36)));
            int tooltipWidth = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
            int anchorX = Math.max(0, Math.min(mouseX, width - tooltipWidth - 20));
            renderTooltip(matrices, lines, anchorX, mouseY);
        }
    }

    private int panelWidth() { return Math.min(300, width - 32); }
    private int top() { return Math.max(6, (height - 156) / 2); }
}
