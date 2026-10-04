package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import zsgrooms.modid.*;
import java.util.*;

public final class TournamentStandingsScreen extends Screen {
    private final Screen parent;
    private final String roomName;
    private final Tournament preview;
    private int scroll, drag;
    private double zoom = 1, panX, panY;
    private boolean fitted;
    private TournamentBracketLayout layout;
    private final TournamentViewCache viewCache = new TournamentViewCache();
    private final Map<ButtonWidget, String> help = new LinkedHashMap<>();

    public TournamentStandingsScreen(Screen parent, String roomName, Tournament preview) {
        super(new LiteralText("Tournament Standings"));
        this.parent = parent; this.roomName = roomName; this.preview = preview;
    }

    private Tournament tournament() {
        InGame game = ZsgRooms.getGame(roomName);
        return preview == null && game != null ? game.getTournament() : preview;
    }

    @Override protected void init() {
        help.clear();
        drag = 0;
        Tournament tournament = tournament();
        layout = tournament != null && tournament.settings.bracket ? new TournamentBracketLayout(tournament.settings.order.size()) : null;
        if (layout != null) {
            if (!fitted) {
                fit();
                if (layout.firstRoundMatches > 4) zoom = Math.max(.75, zoom);
                fitted = true;
            }
            tool(width - 112, "-", "Zoom out", () -> changeZoom(zoom / 1.2));
            tool(width - 88, "+", "Zoom in", () -> changeZoom(zoom * 1.2));
            ButtonWidget fit = addButton(new ButtonWidget(width - 64, 28, 48, 20, new LiteralText("Fit"), b -> fit()));
            help.put(fit, "Fit the whole bracket. Drag to pan, scroll vertically, or hold Shift to scroll horizontally. Ctrl-scroll zooms. Arrow keys pan; Home fits the bracket.");
        }
        int w = Math.min(380, width - 24), button = (w - 12) / 3, left = (width - w) / 2;
        addButton(new ButtonWidget(left, height - 28, button, 20, new LiteralText("Setup"),
                b -> client.openScreen(preview == null ? new TournamentScreen(this, roomName) : parent)));
        ButtonWidget race = addButton(new ButtonWidget(left + button + 6, height - 28, button, 20, new LiteralText("Last Race"), b -> client.openScreen(new RaceStandingsScreen(this, roomName))));
        InGame game = ZsgRooms.getGame(roomName);
        race.active = game != null && game.getSequence() != null;
        addButton(new ButtonWidget(left + (button + 6) * 2, height - 28, button, 20, new LiteralText("Back"), b -> onClose()));
        clampPan();
    }

    private void tool(int x, String label, String tip, Runnable action) {
        ButtonWidget button = addButton(new ButtonWidget(x, 28, 20, 20, new LiteralText(label), b -> action.run()));
        help.put(button, tip);
    }
    private int viewWidth() { return width - 32; }
    private int viewHeight() { return Math.max(1, height - 116); }
    private double maxX() { return layout == null ? 0 : Math.max(0, layout.width() * zoom - viewWidth()); }
    private double maxY() { return layout == null ? 0 : Math.max(0, layout.height() * zoom - viewHeight()); }
    private int horizontalThumb() { return Math.max(18, (int) (viewWidth() * viewWidth() / (layout.width() * zoom))); }
    private int verticalThumb() { return Math.max(18, (int) (viewHeight() * viewHeight() / (layout.height() * zoom))); }
    private void clampPan() { panX = Math.max(0, Math.min(maxX(), panX)); panY = Math.max(0, Math.min(maxY(), panY)); }
    private void fit() {
        zoom = Math.min(1, Math.min(viewWidth() / (double) layout.width(), viewHeight() / (double) layout.height()));
        panX = panY = 0;
    }
    private void changeZoom(double value) {
        double next = Math.max(.05, Math.min(1.5, value));
        panX = (panX + viewWidth() / 2.0) * next / zoom - viewWidth() / 2.0;
        panY = (panY + viewHeight() / 2.0) * next / zoom - viewHeight() / 2.0;
        zoom = next;
        clampPan();
    }
    private double originX() { return 12 + Math.max(0, (viewWidth() - layout.width() * zoom) / 2) - panX; }
    private double originY() { return 66 + Math.max(0, (viewHeight() - layout.height() * zoom) / 2) - panY; }
    private boolean inView(double x, double y) { return x >= 12 && x <= width - 12 && y >= 66 && y <= height - 42; }
    @Override public void onClose() { client.openScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (layout == null) scroll = Math.max(0, scroll - (int) Math.signum(amount));
        else if (inView(x, y)) {
            if (hasControlDown()) { changeZoom(zoom * Math.pow(1.2, amount)); return true; }
            if (hasShiftDown()) panX -= amount * 36; else panY -= amount * 36;
            clampPan();
        } else return super.mouseScrolled(x, y, amount);
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (layout == null || button != 0 || !inView(x, y)) return false;
        drag = y >= height - 49 && maxX() > 0 ? 2 : x >= width - 19 && maxY() > 0 ? 3 : 1;
        if (drag == 2) panX = (x - 12 - horizontalThumb() / 2.0) * maxX() / Math.max(1, viewWidth() - horizontalThumb());
        if (drag == 3) panY = (y - 66 - verticalThumb() / 2.0) * maxY() / Math.max(1, viewHeight() - verticalThumb());
        clampPan();
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (drag == 0) return super.mouseDragged(x, y, button, dx, dy);
        if (drag == 1) { panX -= dx; panY -= dy; }
        if (drag == 2) panX += dx * maxX() / Math.max(1, viewWidth() - horizontalThumb());
        if (drag == 3) panY += dy * maxY() / Math.max(1, viewHeight() - verticalThumb());
        clampPan();
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (drag != 0) { drag = 0; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (layout != null) {
            if (key == GLFW.GLFW_KEY_HOME) { fit(); return true; }
            if (key == GLFW.GLFW_KEY_LEFT) panX -= 36;
            else if (key == GLFW.GLFW_KEY_RIGHT) panX += 36;
            else if (key == GLFW.GLFW_KEY_UP) panY -= 36;
            else if (key == GLFW.GLFW_KEY_DOWN) panY += 36;
            else return super.keyPressed(key, scan, modifiers);
            clampPan(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
        renderBackground(matrices);
        fill(matrices, 0, 0, width, 50, 0xB0101010);
        fill(matrices, 0, height - 36, width, height, 0xB0101010);
        Tournament tournament = tournament();
        drawCenteredString(matrices, textRenderer, preview == null ? "Tournament Standings"
                : layout != null ? "Bracket Preview" : "Points Preview", width / 2, 10, 0xFFFFFF);
        List<String> tooltip = null;
        if (tournament != null) {
            String status = tournament.complete ? tournament.status() : (layout != null ? "Best of " + tournament.settings.bestOf
                    + "  |  " + tournament.settings.order.size() + " players" : tournament.status());
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(status, layout == null ? width - 32 : width - 140), 16, 34,
                    tournament.complete ? 0xFFE36B : 0xA8D8FF);
            tooltip = layout != null ? bracket(matrices, tournament, mx, my) : points(matrices, tournament, mx, my);
        }
        super.render(matrices, mx, my, delta);
        for (Map.Entry<ButtonWidget, String> entry : help.entrySet()) if (entry.getKey().isMouseOver(mx, my)) tooltip = Arrays.asList(entry.getValue());
        if (tooltip != null && drag == 0) {
            List<StringRenderable> lines = new ArrayList<>();
            for (String line : tooltip) lines.addAll(textRenderer.wrapLines(new LiteralText(line), Math.min(280, width - 36)));
            int w = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
            renderTooltip(matrices, lines, Math.max(0, Math.min(mx, width - w - 20)), my);
        }
    }

    private List<String> bracket(MatrixStack matrices, Tournament tournament, int mx, int my) {
        viewCache.updateBracket(tournament);
        List<String> tooltip = null;
        double ox = originX(), oy = originY();
        clip(12, 51, viewWidth(), 14);
        matrices.push();
        matrices.translate(ox, 53, 0); matrices.scale((float) zoom, (float) zoom, 1);
        for (int round = 0; round < layout.rounds; round++)
            drawCenteredString(matrices, textRenderer, layout.title(round), layout.x(round) + TournamentBracketLayout.MATCH_WIDTH / 2, 0, 0xD4D4D4);
        matrices.pop();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        clip(12, 66, viewWidth(), viewHeight());
        matrices.push();
        matrices.translate(ox, oy, 0); matrices.scale((float) zoom, (float) zoom, 1);
        Tournament.Match current = tournament.currentMatch();
        for (int round = 0; round < layout.rounds; round++) for (int index = 0; index < layout.count(round); index++) {
            int x = layout.x(round), cy = layout.centerY(round, index), y = cy - TournamentBracketLayout.MATCH_HEIGHT / 2;
            Tournament.Match match = viewCache.match(round, index);
            if (round + 1 < layout.rounds) {
                int end = layout.x(round + 1), mid = x + TournamentBracketLayout.MATCH_WIDTH + 18;
                int nextY = layout.centerY(round + 1, index / 2), color = match != null && match.complete && !match.winner.isEmpty() ? 0xFFAD9853 : 0xFF656565;
                fill(matrices, x + TournamentBracketLayout.MATCH_WIDTH, cy, mid, cy + 1, color);
                fill(matrices, mid, Math.min(cy, nextY), mid + 1, Math.max(cy, nextY) + 1, color);
                fill(matrices, mid, nextY, end, nextY + 1, color);
            }
            int border = match == current && match != null ? 0xFF83D9E7 : match != null && match.complete ? 0xFF8E804C : 0xFF606060;
            fill(matrices, x, y, x + 144, y + 38, border);
            fill(matrices, x + 1, y + 1, x + 143, y + 37, 0xFF1E1E1E);
            fill(matrices, x + 1, cy, x + 143, cy + 1, 0xFF444444);
            for (int side = 0; side < 2; side++) {
                String name = slot(tournament, round, index, side);
                boolean winner = match != null && match.complete && !match.winner.isEmpty() && match.winner.equals(name);
                boolean pending = match == null || name.equals("Bye") || name.equals("No player");
                int rowY = y + 1 + side * 18;
                if (winner) fill(matrices, x + 1, rowY, x + 143, rowY + 18, 0xFF393321);
                fill(matrices, x + 121, rowY, x + 143, rowY + 18, winner ? 0xFF514526 : 0xFF303030);
                textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(name, 111), x + 6, rowY + 5, winner ? 0xFFE36B : pending ? 0x999999 : 0xFFFFFF);
                String score = match == null ? "-" : Integer.toString(side == 0 ? match.leftWins : match.rightWins);
                drawCenteredString(matrices, textRenderer, score, x + 132, rowY + 5, winner ? 0xFFE36B : 0xCCCCCC);
            }
            double lx = (mx - ox) / zoom, ly = (my - oy) / zoom;
            if (inView(mx, my) && mx < width - 20 && my < height - 50 && lx >= x && lx < x + 144 && ly >= y && ly < y + 38) {
                String state = match == null ? "Awaiting earlier matches" : match.complete ? match.winner.isEmpty() ? "No advancing player" : "Advances: " + match.winner
                        : match == current ? tournament.activeRaceId.isEmpty() ? "Next match" : "Race in progress" : "Waiting";
                tooltip = Arrays.asList(layout.title(round), slot(tournament, round, index, 0) + " vs " + slot(tournament, round, index, 1), state);
            }
        }
        matrices.pop();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (maxX() > 0) {
            int track = viewWidth(), thumb = horizontalThumb();
            int start = 12 + (int) ((track - thumb) * panX / maxX());
            fill(matrices, 12, height - 47, 12 + track, height - 44, 0xFF373737);
            fill(matrices, start, height - 48, start + thumb, height - 43, 0xFFAAAAAA);
        }
        if (maxY() > 0) {
            int track = viewHeight(), thumb = verticalThumb();
            int start = 66 + (int) ((track - thumb) * panY / maxY());
            fill(matrices, width - 17, 66, width - 14, 66 + track, 0xFF373737);
            fill(matrices, width - 18, start, width - 13, start + thumb, 0xFFAAAAAA);
        }
        return tooltip;
    }

    private String slot(Tournament tournament, int round, int index, int side) {
        Tournament.Match match = viewCache.match(round, index);
        if (match != null) {
            String name = side == 0 ? match.left : match.right;
            return name.isEmpty() ? round == 0 ? "Bye" : "No player" : name;
        }
        Tournament.Match source = viewCache.match(round - 1, index * 2 + side);
        if (source != null && source.complete) return source.winner.isEmpty() ? "No player" : source.winner;
        return "Winner " + (index * 2 + side + 1);
    }
    private void clip(int x, int y, int w, int h) {
        double scale = client.getWindow().getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), client.getWindow().getFramebufferHeight() - (int) ((y + h) * scale), (int) (w * scale), (int) (h * scale));
    }

    private List<String> points(MatrixStack matrices, Tournament tournament, int mx, int my) {
        int w = Math.min(600, width - 32), left = (width - w) / 2, right = left + w;
        textRenderer.drawWithShadow(matrices, "#", left + 6, 57, 0x999999);
        textRenderer.drawWithShadow(matrices, "Player", left + 34, 57, 0x999999);
        textRenderer.drawWithShadow(matrices, "Points", right - 104, 57, 0x999999);
        textRenderer.drawWithShadow(matrices, "Status", right - 56, 57, 0x999999);
        List<String> names = viewCache.leaderboard(tournament);
        int rows = Math.max(1, (height - 114) / 26);
        scroll = Math.min(scroll, Math.max(0, names.size() - rows));
        List<String> tooltip = null;
        for (int i = scroll; i < Math.min(names.size(), scroll + rows); i++) {
            String name = names.get(i);
            int y = 74 + (i - scroll) * 26, place = viewCache.place(i);
            fill(matrices, left, y - 4, right, y + 20, (i & 1) == 0 ? 0xA0202020 : 0xA0121212);
            int color = place == 1 && tournament.scores.get(name) > 0 ? 0xFFE36B : 0xFFFFFF;
            textRenderer.drawWithShadow(matrices, Integer.toString(place), left + 6, y + 3, color);
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(name, w - 150), left + 34, y + 3, color);
            textRenderer.drawWithShadow(matrices, Integer.toString(tournament.scores.get(name)), right - 104, y + 3, color);
            String status = tournament.withdrawn.contains(name) ? "Out" : tournament.complete ? "Final" : "Playing";
            textRenderer.drawWithShadow(matrices, status, right - 56, y + 3, 0xAAAAAA);
            if (mx >= left && mx < right && my >= y - 4 && my < y + 20) tooltip = Arrays.asList(name,
                    tournament.withdrawn.contains(name) ? "Withdrawn" : status);
        }
        if (names.size() > rows) drawCenteredString(matrices, textRenderer, (scroll + 1) + "-" + Math.min(names.size(), scroll + rows) + " / " + names.size(), width / 2, height - 43, 0xAAAAAA);
        return tooltip;
    }
}
