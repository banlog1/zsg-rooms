// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MobHighlightScreen extends Screen {
    private final Screen parent;
    private final MobHighlights highlights;
    private final MobSelection selection;
    private final TrailStyle trailStyle;
    private final List<EntityType<?>> types = new ArrayList<>();
    private final List<MobCheckbox> rows = new ArrayList<>();
    private final Map<ButtonWidget, TrailStyle.Color> swatches = new LinkedHashMap<>();
    private TextFieldWidget search;
    private ButtonWidget previous;
    private ButtonWidget next;
    private String query = "";
    private int page;
    private int pages;
    private int matches;
    private int listTop;
    private int listBottom;

    MobHighlightScreen(Screen parent, MobHighlights highlights) {
        super(new LiteralText("Mob Highlights"));
        this.parent = parent;
        this.highlights = highlights;
        this.selection = highlights;
        this.trailStyle = null;
    }

    MobHighlightScreen(Screen parent, MobSelection selection, TrailStyle style) {
        super(new LiteralText("Mob Trails"));
        this.parent = parent;
        this.highlights = null;
        this.selection = selection;
        this.trailStyle = style;
    }

    @Override protected void init() {
        rows.clear();
        swatches.clear();
        types.clear();
        Set<EntityType<?>> available = new LinkedHashSet<>();
        // Vanilla's non-spawning mobs are MISC; do not instantiate entity factories to list them.
        for (EntityType<?> type : Registry.ENTITY_TYPE) {
            if (type.getSpawnGroup() != SpawnGroup.MISC || type == EntityType.VILLAGER
                    || type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM
                    || selection.selected(Registry.ENTITY_TYPE.getId(type))) available.add(type);
        }
        if (client.world != null) for (Entity entity : client.world.getEntities()) {
            if (entity instanceof MobEntity) available.add(entity.getType());
        }
        types.addAll(available);
        types.sort(Comparator.comparing(type -> type.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        int panelWidth = Math.min(320, width - 24);
        int x = (width - panelWidth) / 2;
        search = new TextFieldWidget(textRenderer, x, 30, panelWidth, 20, new LiteralText("Search mobs"));
        search.setMaxLength(100);
        search.setText(query);
        children.add(search);
        addButton(new ReplaySettingsCheckbox(x, 56, trailStyle == null ? panelWidth : panelWidth - 84,
                trailStyle == null ? "Highlight selected mobs" : "Track selected mobs",
                () -> selection.enabled, value -> selection.enabled = value));
        if (trailStyle != null) addButton(flatButton(x + panelWidth - 80, 56, 80, "Customize",
                () -> client.openScreen(new TrailSettingsScreen(this, "Mob Trails", trailStyle))));
        int index = 0;
        int spacing = panelWidth / TrailStyle.Color.values().length;
        for (TrailStyle.Color color : TrailStyle.Color.values()) {
            ButtonWidget swatch = addButton(new ButtonWidget(x + index++ * spacing, 82, spacing - 4, 20,
                    new LiteralText(color.label), button -> setColor(color)) {
                @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                    fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height,
                            color() == color ? 0xFFFFFFFF : isHovered() || isFocused() ? 0xFFAAAAAA : 0xFF333333);
                    fill(matrices, this.x + 2, this.y + 2, this.x + this.width - 2, this.y + this.height - 2, 0xFF000000 | color.rgb);
                }
            });
            swatches.put(swatch, color);
        }
        listTop = 110;
        int rowCount = Math.max(1, (height - 174) / 22);
        listBottom = listTop + rowCount * 22;
        for (int i = 0; i < rowCount; i++) {
            rows.add(addButton(new MobCheckbox(x, listTop + i * 22, panelWidth)));
        }
        previous = addButton(flatButton(x, height - 58, 24, "<", () -> changePage(-1)));
        next = addButton(flatButton(x + panelWidth - 24, height - 58, 24, ">", () -> changePage(1)));
        addButton(flatButton(x, height - 30, (panelWidth - 8) / 2, "Clear", () -> {
            selection.clear();
            refresh();
        }));
        addButton(flatButton(x + (panelWidth + 8) / 2, height - 30, (panelWidth - 8) / 2, "Done", this::onClose));
        search.setChangedListener(value -> { query = value; page = 0; refresh(); });
        refresh();
    }

    private List<EntityType<?>> filtered() {
        String value = query.trim().toLowerCase(Locale.ROOT);
        List<EntityType<?>> filtered = new ArrayList<>();
        for (EntityType<?> type : types) {
            if (type.getName().getString().toLowerCase(Locale.ROOT).contains(value)
                    || Registry.ENTITY_TYPE.getId(type).toString().toLowerCase(Locale.ROOT).contains(value)) filtered.add(type);
        }
        return filtered;
    }

    private void refresh() {
        List<EntityType<?>> filtered = filtered();
        matches = filtered.size();
        pages = Math.max(1, (filtered.size() + rows.size() - 1) / rows.size());
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < rows.size(); i++) {
            int position = page * rows.size() + i;
            rows.get(i).bind(position < filtered.size() ? filtered.get(position) : null);
        }
        previous.active = page > 0;
        next.active = page + 1 < pages;
    }

    private final class MobCheckbox extends CheckboxWidget {
        private EntityType<?> type;
        MobCheckbox(int x, int y, int width) { super(x, y, width, 20, LiteralText.EMPTY, false); }
        void bind(EntityType<?> value) {
            type = value;
            active = visible = value != null;
            if (!visible) return;
            setMessage(new LiteralText(textRenderer.trimToWidth(type.getName().getString(), getWidth() - 30)));
            if (isChecked() != selection.selected(Registry.ENTITY_TYPE.getId(type))) super.onPress();
        }
        @Override public void onPress() {
            if (!visible || type == null) return;
            super.onPress();
            selection.set(Registry.ENTITY_TYPE.getId(type), isChecked());
        }
    }

    private void changePage(int direction) { page += direction; refresh(); }
    @Override public void tick() { search.tick(); }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (y >= listTop && y <= listBottom && amount != 0) { changePage(amount > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        fill(matrices, 0, 0, width, height, 0xA0000000);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, 10, 0xFFFFFF);
        search.render(matrices, mouseX, mouseY, delta);
        if (query.isEmpty() && !search.isFocused()) textRenderer.drawWithShadow(matrices, "Search mobs", search.x + 4, search.y + 6, 0x888888);
        if (matches == 0) drawCenteredString(matrices, textRenderer, "No matching mobs", width / 2, listTop + 6, 0xAAAAAA);
        drawCenteredString(matrices, textRenderer, (page + 1) + " / " + pages + " | " + selection.size() + " selected",
                width / 2, height - 52, 0xCCCCCC);
        super.render(matrices, mouseX, mouseY, delta);
        for (Map.Entry<ButtonWidget, TrailStyle.Color> entry : swatches.entrySet()) {
            if (entry.getKey().isHovered()) renderTooltip(matrices, new LiteralText(entry.getValue().label), mouseX, mouseY);
        }
        if (previous.isHovered()) renderTooltip(matrices, new LiteralText("Previous page"), mouseX, mouseY);
        else if (next.isHovered()) renderTooltip(matrices, new LiteralText("Next page"), mouseX, mouseY);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { client.openScreen(parent); }

    private TrailStyle.Color color() { return trailStyle == null ? highlights.color : trailStyle.color; }
    private void setColor(TrailStyle.Color color) {
        if (trailStyle == null) highlights.color = color;
        else trailStyle.color = color;
    }

    private ButtonWidget flatButton(int x, int y, int width, String label, Runnable action) {
        return new ButtonWidget(x, y, width, 20, new LiteralText(label), button -> action.run()) {
            @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                if (isHovered() || isFocused()) fill(matrices, this.x, this.y, this.x + this.width, this.y + height, 0x40334444);
                drawCenteredString(matrices, textRenderer, getMessage().getString(), this.x + this.width / 2, this.y + 6,
                        active ? 0xE8EEED : 0x777777);
                fill(matrices, this.x, this.y + height - 1, this.x + this.width, this.y + height, 0x60777777);
            }
        };
    }
}
