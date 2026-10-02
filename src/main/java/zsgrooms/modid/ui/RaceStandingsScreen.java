package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.InGame;
import zsgrooms.modid.RaceSequence;
import zsgrooms.modid.ZsgRooms;

public final class RaceStandingsScreen extends Screen {
    private final Screen parent;
    private final String room;
    private int scroll;

    public RaceStandingsScreen(Screen parent, String room) {
        super(new LiteralText("Standings"));
        this.parent = parent;
        this.room = room;
    }

    @Override protected void init() {
        addButton(new ButtonWidget(width / 2 - 70, height - 28, 140, 20, new LiteralText("Back"), b -> onClose()));
    }
    @Override public void onClose() { client.openScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        scroll = Math.max(0, scroll + (amount > 0 ? -1 : 1));
        return true;
    }
    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        InGame game = ZsgRooms.getGame(room);
        RaceSequence race = game == null ? null : game.getSequence();
        drawCenteredString(matrices, textRenderer, race != null && race.complete() ? "Final Standings" : "Provisional Standings",
                width / 2, 12, 0xFFFFFF);
        if (race != null) {
            int left = Math.max(8, (width - 540) / 2);
            int right = width - left;
            int rows = Math.max(1, (height - 78) / 32);
            java.util.List<RaceSequence.Runner> runners = race.standings();
            scroll = Math.min(scroll, Math.max(0, runners.size() - rows));
            for (int i = scroll; i < Math.min(runners.size(), scroll + rows); i++) {
                RaceSequence.Runner runner = runners.get(i);
                int y = 38 + (i - scroll) * 32;
                String place = runner.placed() ? "#" + race.place(runner.name) : runner.dnf ? "DNF" : runner.stopped ? "-" : "...";
                String name = place + "  " + runner.name;
                String total = runner.wonByForfeit ? "Win by forfeit" : runner.stopped ? "Unplaced"
                        : runner.finished ? RaceSequence.time(runner.adjustedNanos()) : runner.dnf ? "" : "Racing";
                int timeWidth = textRenderer.getWidth(total);
                textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(name, Math.max(1, right - left - timeWidth - 12)), left, y, 0xFFFFFF);
                textRenderer.drawWithShadow(matrices, total, right - timeWidth, y, 0xFFDF77);
                String detail = runner.stage + "/" + race.goal + " seeds"
                        + (runner.skipped == 0 ? "" : " | " + runner.skipped + " skipped | +" + RaceSequence.time(runner.penaltyNanos()));
                textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(detail, right - left), left, y + 12, 0xB8B8B8);
                fill(matrices, left, y + 26, right, y + 27, 0x55777777);
            }
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
