package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import zsgrooms.modid.TournamentSettings;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

final class TournamentRosterScreen extends Screen {
    private final Screen parent;
    private final TournamentSettings settings;
    private final boolean editable;
    private int page;
    private final Map<ButtonWidget, String> help = new LinkedHashMap<>();

    TournamentRosterScreen(Screen parent, TournamentSettings settings, boolean editable) {
        super(new LiteralText("Players and Byes"));
        this.parent = parent; this.settings = settings; this.editable = editable;
    }

    private int rows() { return Math.max(1, (height - 116) / 26); }
    private int left() { return Math.max(12, (width - 480) / 2); }

    @Override protected void init() {
        help.clear();
        page = Math.min(page, Math.max(0, (settings.order.size() - 1) / rows()));
        int right = width - left();
        for (int i = page * rows(); i < Math.min(settings.order.size(), (page + 1) * rows()); i++) {
            final int index = i;
            String name = settings.order.get(i);
            int y = 68 + (i - page * rows()) * 26;
            ButtonWidget up = addButton(new ButtonWidget(right - 44, y, 20, 20, new LiteralText("^"), b -> {
                Collections.swap(settings.order, index, index - 1); client.openScreen(this);
            }));
            ButtonWidget down = addButton(new ButtonWidget(right - 20, y, 20, 20, new LiteralText("v"), b -> {
                Collections.swap(settings.order, index, index + 1); client.openScreen(this);
            }));
            up.active = editable && i > 0; down.active = editable && i + 1 < settings.order.size();
            help.put(up, "Move " + name + " earlier in the opening order");
            help.put(down, "Move " + name + " later in the opening order");
            if (settings.bracket) {
                CheckboxWidget bye = addButton(new CheckboxWidget(right - 103, y, 55, 20, new LiteralText("Bye"), settings.byes.contains(name)) {
                    @Override public void onPress() {
                        super.onPress();
                        if (isChecked()) settings.byes.add(name); else settings.byes.remove(name);
                        client.openScreen(TournamentRosterScreen.this);
                    }
                });
                bye.active = editable && (settings.byes.contains(name) || settings.byes.size() < TournamentSettings.byeCount(settings.order.size()));
            }
        }
        ButtonWidget prev = addButton(new ButtonWidget(left(), height - 28, 24, 20, new LiteralText("<"), b -> { page--; client.openScreen(this); }));
        ButtonWidget next = addButton(new ButtonWidget(width - left() - 24, height - 28, 24, 20, new LiteralText(">"), b -> { page++; client.openScreen(this); }));
        prev.active = page > 0; next.active = (page + 1) * rows() < settings.order.size();
        help.put(prev, "Previous players"); help.put(next, "Next players");
        addButton(new ButtonWidget(width / 2 - 65, height - 28, 130, 20, new LiteralText("Done"), b -> onClose()));
    }
    @Override public void onClose() { client.openScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        int next = Math.max(0, Math.min(Math.max(0, (settings.order.size() - 1) / rows()), page - (int) Math.signum(amount)));
        if (next != page) { page = next; client.openScreen(this); }
        return true;
    }
    @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
        renderBackground(matrices);
        fill(matrices, 0, 0, width, 45, 0xB0101010);
        fill(matrices, 0, height - 36, width, height, 0xB0101010);
        drawCenteredString(matrices, textRenderer, "Opening Order", width / 2, 12, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, settings.bracket ? "Assigned byes: " + settings.byes.size() + "/"
                + TournamentSettings.byeCount(settings.order.size()) : settings.order.size() + " players", width / 2, 30, 0xA8D8FF);
        textRenderer.drawWithShadow(matrices, "#", left() + 6, 53, 0x999999);
        textRenderer.drawWithShadow(matrices, "Player", left() + 30, 53, 0x999999);
        textRenderer.drawWithShadow(matrices, "Order", width - left() - 42, 53, 0x999999);
        if (settings.bracket) textRenderer.drawWithShadow(matrices, "Bye", width - left() - 103, 53, 0x999999);
        String hovered = null;
        for (int i = page * rows(); i < Math.min(settings.order.size(), (page + 1) * rows()); i++) {
            int y = 68 + (i - page * rows()) * 26;
            String name = settings.order.get(i);
            fill(matrices, left(), y - 2, width - left(), y + 22, (i & 1) == 0 ? 0xA0202020 : 0xA0121212);
            textRenderer.drawWithShadow(matrices, Integer.toString(i + 1), left() + 6, y + 6, 0x999999);
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(name,
                    width - left() * 2 - (settings.bracket ? 142 : 85)), left() + 30, y + 6, settings.byes.contains(name) ? 0xFFE36B : 0xFFFFFF);
            if (mx >= left() + 30 && mx < width - left() - 112 && my >= y && my < y + 20) hovered = name;
            if (settings.bracket && mx >= width - left() - 103 && mx < width - left() - 48 && my >= y && my < y + 20)
                hovered = "A bye advances " + name + " past the opening round without a race win. The host assigns the recipients agreed by the players.";
        }
        drawCenteredString(matrices, textRenderer, (page + 1) + " / " + Math.max(1, (settings.order.size() + rows() - 1) / rows()), width / 2, height - 43, 0xAAAAAA);
        super.render(matrices, mx, my, delta);
        for (Map.Entry<ButtonWidget, String> entry : help.entrySet()) if (entry.getKey().isMouseOver(mx, my)) hovered = entry.getValue();
        if (hovered != null) {
            List<StringRenderable> lines = new ArrayList<>(textRenderer.wrapLines(new LiteralText(hovered), Math.min(280, width - 36)));
            int w = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
            renderTooltip(matrices, lines, Math.max(0, Math.min(mx, width - w - 20)), my);
        }
    }
}
