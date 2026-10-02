package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import zsgrooms.modid.*;
import java.util.*;

public final class TournamentScreen extends Screen {
    private final Screen parent;
    private final String roomName;
    private final TournamentSettings draft;
    private TextFieldWidget rounds, points;
    private final Map<AbstractButtonWidget, String> help = new LinkedHashMap<>();
    private String error = "";
    private String roundsText, pointsText;

    public TournamentScreen(Screen parent, String roomName) {
        super(new LiteralText("Tournament"));
        this.parent = parent;
        this.roomName = roomName;
        draft = ZsgRooms.getGame(roomName).getTournamentSettings().copy();
        roundsText = Integer.toString(draft.rounds);
        pointsText = draft.points.toString().replace("[", "").replace("]", "").replace(" ", "");
        List<String> roster = ZsgRooms.getRoom(roomName).getPlayerNames();
        if (!ZsgRooms.getGame(roomName).tournamentLocked()) {
            draft.order.retainAll(roster);
            for (String name : roster) if (!draft.order.contains(name)) draft.order.add(name);
            draft.byes.retainAll(draft.order);
        }
    }

    private boolean editable() {
        InGame game = ZsgRooms.getGame(roomName);
        return RoomRuleSettings.canEdit(ZsgRooms.getRoom(roomName), game, ZsgRoomsClient.localPlayerName(client)) && !game.tournamentLocked();
    }

    @Override protected void init() {
        readFields();
        help.clear();
        int left = (width - contentWidth()) / 2, y = top();
        CheckboxWidget enabled = addButton(new CheckboxWidget(left, y + 30, contentWidth() / 2, 20,
                new LiteralText("Tournament"), draft.enabled) {
            @Override public void onPress() { super.onPress(); draft.enabled = isChecked(); }
        });
        enabled.active = editable();
        help.put(enabled, "Off by default. The host starts each race. Settings and the starting roster lock when the tournament starts. "
                + "Return to Room stays in the tournament; Leave Room withdraws from all remaining rounds. New arrivals wait until the next tournament.");
        int half = (contentWidth() - 6) / 2;
        ButtonWidget bracket = button(left, y + 60, half, "Bracket", () -> { readFields(); draft.bracket = true; client.openScreen(this); });
        ButtonWidget scored = button(left + half + 6, y + 60, half, "Points", () -> { readFields(); draft.bracket = false; client.openScreen(this); });
        bracket.active = editable() && !draft.bracket;
        scored.active = editable() && draft.bracket;
        help.put(bracket, "Single elimination, one match at a time. Only the paired players load a world; everyone else waits in the room. "
                + "Each race uses the room's seed count, with one qualifying finisher. Leaving a race is a race loss; leaving the room forfeits the whole match.");
        help.put(scored, "Everyone races each round. Placement points are added after the race ends. The room's finisher limit applies. "
                + "DNF and unplaced runners score zero. Exact race ties receive the same points for their place, skipping the following places. "
                + "Equal final totals share a tournament place.");
        rounds = null;
        points = null;
        int controlX = left + 110, controlWidth = contentWidth() - 110;
        if (draft.bracket) {
            ButtonWidget best = button(controlX, y + 96, controlWidth, "Best of " + draft.bestOf,
                    () -> { draft.bestOf = draft.bestOf == 7 ? 1 : draft.bestOf + 2; client.openScreen(this); });
            best.active = editable();
            help.put(best, "Best of 1, 3, 5 or 7. First to " + (draft.bestOf / 2 + 1) + " race wins advances. "
                    + "A race may contain multiple seeds; seeds are not match wins. Draws award no win and replay. Filtered seeds are refreshed; manual seeds stay fixed. "
                    + "Reset retries the current seed. Multi-seed skips add 30 minutes; single-seed forfeits add no penalty.");
        } else {
            rounds = field(left + contentWidth() - 74, y + 96, 48, roundsText, "Rounds", 3);
            rounds.setTextPredicate(s -> s.matches("[0-9]{0,3}"));
            ButtonWidget minus = button(left + contentWidth() - 100, y + 96, 20, "-", () -> stepRounds(-1));
            ButtonWidget plus = button(left + contentWidth() - 20, y + 96, 20, "+", () -> stepRounds(1));
            minus.active = plus.active = editable();
            help.put(minus, "Fewer rounds"); help.put(plus, "More rounds");
            points = field(controlX, y + 122, controlWidth, pointsText, "Placement points", 383);
            points.setTextPredicate(s -> s.matches("[0-9, ]*"));
            help.put(rounds, "Number of scored races: 1-100. Every race uses the configured seed count. The host starts the next round manually.");
            help.put(points, "Comma-separated points for first, second, third and so on. For example: 10,6,4,2,1. "
                    + "Use non-increasing whole numbers from 0 to 10000. Unlisted places, DNF and unplaced runners earn zero. "
                    + "Increase Finishers in Race Format to award more placements.");
        }
        ButtonWidget roster = button(left, y + 158, half, draft.bracket ? "Players and Byes" : "Players",
                () -> { readFields(); client.openScreen(new TournamentRosterScreen(this, draft, editable())); });
        help.put(roster, "Arrange the opening player order. For brackets, the host must assign exactly the required number of byes, "
                + "after agreeing with the players. No byes are assigned randomly. A bye advances one round without race wins.");
        button(left + half + 6, y + 158, half, "Standings / Bracket", () -> {
            readFields();
            Tournament tournament = ZsgRooms.getGame(roomName).getTournament();
            String issue = draft.startError(draft.order);
            if (tournament == null && !issue.isEmpty()) { error = issue; return; }
            client.openScreen(new TournamentStandingsScreen(this, roomName, tournament == null ? new Tournament(draft) : null));
        });
        ButtonWidget apply = button(left, height - 28, half, "Apply", this::apply);
        apply.active = editable();
        if (ZsgRooms.getGame(roomName).tournamentLocked()) {
            apply.setMessage(new LiteralText("End / Reset"));
            apply.active = RoomRuleSettings.canEdit(ZsgRooms.getRoom(roomName), ZsgRooms.getGame(roomName), ZsgRoomsClient.localPlayerName(client));
        }
        button(left + half + 6, height - 28, half, "Back", this::onClose);
    }

    private void apply() {
        InGame game = ZsgRooms.getGame(roomName);
        if (game.tournamentLocked()) {
            client.openScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    RoomRuleSettings rules = RoomRuleSettings.capture(ZsgRooms.getGame(roomName));
                    rules.resetTournament = true;
                    rules.tournament.enabled = false;
                    ZsgRoomsClient.sendRoomAction("rules", roomName, rules.toJson());
                    client.openScreen(parent);
                } else client.openScreen(this);
            }, new LiteralText("End this tournament?"), new LiteralText("Tournament scores and bracket progress will be cleared.")));
            return;
        }
        readFields();
        if (!draft.valid()) { error = "Check rounds and placement points"; return; }
        error = draft.enabled ? draft.startError(ZsgRooms.getRoom(roomName).getPlayerNames()) : "";
        if (!error.isEmpty()) return;
        RoomRuleSettings rules = RoomRuleSettings.capture(game);
        rules.tournament = draft.copy();
        ZsgRoomsClient.sendRoomAction("rules", roomName, rules.toJson());
        client.openScreen(parent);
    }

    private void readFields() {
        if (rounds == null || points == null) return;
        roundsText = rounds.getText();
        pointsText = points.getText();
        try { draft.rounds = Integer.parseInt(roundsText); }
        catch (NumberFormatException invalid) { draft.rounds = 0; }
        try {
            List<Integer> values = new ArrayList<>();
            for (String value : pointsText.split(",", -1)) values.add(Integer.parseInt(value.trim()));
            draft.points = values;
        } catch (NumberFormatException invalid) { draft.points = new ArrayList<>(); }
    }

    private void stepRounds(int step) {
        int current;
        try { current = Integer.parseInt(rounds.getText()); }
        catch (NumberFormatException invalid) { current = 1; }
        rounds.setText(Integer.toString(Math.max(1, Math.min(100, current + step))));
    }

    private TextFieldWidget field(int x, int y, int w, String value, String label, int length) {
        TextFieldWidget field = addButton(new TextFieldWidget(textRenderer, x, y, w, 20, new LiteralText(label)));
        field.setMaxLength(length);
        field.setText(value);
        field.active = editable();
        field.setEditable(editable());
        field.setChangedListener(s -> readFields());
        return field;
    }

    private ButtonWidget button(int x, int y, int w, String label, Runnable action) {
        return addButton(new ButtonWidget(x, y, w, 20, new LiteralText(label), b -> action.run()));
    }

    @Override public void onClose() { client.openScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { if (rounds != null) { rounds.tick(); points.tick(); } }
    @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
        renderBackground(matrices);
        fill(matrices, 0, top() + 25, width, top() + 188, 0x850F0F0F);
        fill(matrices, 0, height - 36, width, height, 0xB0101010);
        drawCenteredString(matrices, textRenderer, "Tournament", width / 2, top() + 8, 0xFFFFFF);
        int left = (width - contentWidth()) / 2;
        int half = (contentWidth() - 6) / 2;
        int selectedX = draft.bracket ? left : left + half + 6;
        fill(matrices, selectedX, top() + 81, selectedX + half, top() + 83, 0xFF83D9E7);
        fill(matrices, left, top() + 149, left + contentWidth(), top() + 150, 0xFF454545);
        InGame game = ZsgRooms.getGame(roomName);
        String status = game != null && game.tournamentLocked() ? "Setup locked" : !editable() ? "Host only" : "";
        textRenderer.drawWithShadow(matrices, status, left + contentWidth() - textRenderer.getWidth(status), top() + 36, 0xBBBBBB);
        if (!draft.bracket) {
            textRenderer.drawWithShadow(matrices, "Rounds", left, top() + 102, 0xDDDDDD);
            textRenderer.drawWithShadow(matrices, "Placement points", left, top() + 128, 0xDDDDDD);
        } else {
            textRenderer.drawWithShadow(matrices, "Series", left, top() + 102, 0xDDDDDD);
            textRenderer.drawWithShadow(matrices, "Opening round", left, top() + 128, 0xDDDDDD);
            String roster = draft.order.size() + " players  |  Byes " + draft.byes.size() + "/" + TournamentSettings.byeCount(draft.order.size());
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(roster, contentWidth() - 110), left + 110, top() + 128, 0xA8D8FF);
        }
        if (!error.isEmpty()) drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(error, width - 12), width / 2, height - 40, 0xFF7777);
        super.render(matrices, mx, my, delta);
        for (Map.Entry<AbstractButtonWidget, String> entry : help.entrySet()) {
            AbstractButtonWidget widget = entry.getKey();
            if (mx >= widget.x && mx < widget.x + widget.getWidth() && my >= widget.y && my < widget.y + widget.getHeight()) {
                List<StringRenderable> lines = new ArrayList<>(textRenderer.wrapLines(new LiteralText(entry.getValue()), Math.min(300, width - 36)));
                int max = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
                renderTooltip(matrices, lines, Math.max(0, Math.min(mx, width - max - 20)), my);
            }
        }
    }
    private int contentWidth() { return Math.min(420, width - 24); }
    private int top() { return Math.max(0, (height - 240) / 2); }
}
