package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgSeedBridge;
import zsgrooms.modid.ZsgRoomsSeedMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FilterPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelected;
    private final String selected;
    private final List<FilterButton> tiles = new ArrayList<FilterButton>();
    private FilterCatalog.Group group;
    private FilterPickerLayout layout;
    private int page;
    private int pageCount;
    private boolean initialized;
    private boolean gallery;

    public FilterPickerScreen(Screen parent, String selected, Consumer<String> onSelected) {
        super(new LiteralText("Select Filter"));
        this.parent = parent;
        this.selected = selected;
        this.onSelected = onSelected;
        this.group = FilterCatalog.Group.ROOMS;
    }

    @Override
    protected void init() {
        this.tiles.clear();
        if (!this.initialized) {
            this.gallery = FilterCatalog.ENTRIES.stream().anyMatch(entry -> entry.image != null
                    && this.client.getResourceManager().containsResource(previewId(entry.image)));
            this.initialized = true;
        }
        this.layout = new FilterPickerLayout(this.width, this.height, this.gallery);
        List<FilterCatalog.Entry> entries = FilterCatalog.entries(this.group);
        this.pageCount = this.layout.pages(entries.size());
        this.page = Math.min(this.page, this.pageCount - 1);
        int contentWidth = this.width - this.layout.left * 2;
        int tabWidth = (contentWidth - 8) / 3;
        for (FilterCatalog.Group tab : FilterCatalog.Group.values()) {
            ButtonWidget button = new ButtonWidget(this.layout.left + tab.ordinal() * (tabWidth + 4), 34,
                    tabWidth, 20, new LiteralText(tab.label), pressed -> {
                this.group = tab;
                this.page = 0;
                refresh();
            });
            button.active = tab != this.group;
            this.addButton(button);
        }
        int start = this.page * this.layout.pageSize();
        for (int i = start; i < Math.min(entries.size(), start + this.layout.pageSize()); i++) {
            int slot = i - start;
            FilterButton tile = new FilterButton(entries.get(i),
                    this.layout.left + slot % this.layout.columns * (this.layout.cellWidth + FilterPickerLayout.GAP),
                    FilterPickerLayout.TOP + slot / this.layout.columns * (this.layout.cellHeight + FilterPickerLayout.GAP));
            this.tiles.add(tile);
            this.addButton(tile);
        }
        int footer = this.height - 28;
        ButtonWidget compact = this.addButton(new ButtonWidget(this.layout.left, footer, 56, 20,
                new LiteralText("Compact"), button -> setGallery(false)));
        compact.active = this.gallery;
        ButtonWidget gallery = this.addButton(new ButtonWidget(this.layout.left + 58, footer, 54, 20,
                new LiteralText("Gallery"), button -> setGallery(true)));
        gallery.active = !this.gallery;
        this.addButton(new ButtonWidget(Math.max(this.layout.left + 116, this.width / 2 - 34), footer, 68, 20,
                new LiteralText("Back"), button -> onClose()));
        ButtonWidget previous = this.addButton(new ButtonWidget(this.width - this.layout.left - 76, footer, 20, 20,
                new LiteralText("<"), button -> changePage(-1)));
        previous.active = this.page > 0;
        ButtonWidget next = this.addButton(new ButtonWidget(this.width - this.layout.left - 20, footer, 20, 20,
                new LiteralText(">"), button -> changePage(1)));
        next.active = this.page + 1 < this.pageCount;
    }

    private void setGallery(boolean gallery) {
        this.gallery = gallery;
        this.page = 0;
        refresh();
    }

    private void refresh() { this.init(this.client, this.width, this.height); }

    private static Identifier previewId(String image) {
        return new Identifier("zsg-rooms", "textures/gui/filters/" + image + ".png");
    }

    private void changePage(int direction) {
        int next = Math.max(0, Math.min(this.pageCount - 1, this.page + direction));
        if (next != this.page) { this.page = next; refresh(); }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount != 0) changePage(amount < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            changePage(keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { this.client.openScreen(this.parent); }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x99000000);
        drawCenteredString(matrices, this.textRenderer, this.title.getString(), this.width / 2, 13, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
        drawCenteredString(matrices, this.textRenderer, (this.page + 1) + "/" + this.pageCount,
                this.width - this.layout.left - 38, this.height - 22, 0xCCCCCC);
        for (FilterButton tile : this.tiles) {
            if (tile.isHovered()) {
                if (tile.mixedMode) {
                    this.renderTooltip(matrices, this.textRenderer.wrapLines(new LiteralText(ZsgRoomsSeedMode.DESCRIPTION),
                            Math.min(260, this.width - 24)), mouseX, mouseY);
                } else this.renderTooltip(matrices, tile.getMessage(), mouseX, mouseY);
            }
        }
    }

    private final class FilterButton extends ButtonWidget {
        private final ItemStack icon;
        private final Identifier preview;
        private final List<StringRenderable> lines;
        private final boolean chosen;
        private final boolean mixedMode;

        FilterButton(FilterCatalog.Entry entry, int x, int y) {
            super(x, y, layout.cellWidth, layout.cellHeight,
                    new LiteralText(ZsgSeedBridge.seedTypeLabel(entry.id)), button -> {
                onSelected.accept(entry.id);
                onClose();
            });
            this.chosen = entry.id.equals(selected);
            this.mixedMode = ZsgRoomsSeedMode.SPECIFICATION.equals(entry.id);
            this.icon = new ItemStack(iconFor(entry));
            Identifier image = entry.image == null ? null : previewId(entry.image);
            this.preview = layout.gallery && image != null && client.getResourceManager().containsResource(image) ? image : null;
            this.lines = textRenderer.wrapLines(this.getMessage(), this.width - (layout.gallery ? 12 : 40));
        }

        @Override
        public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            int border = this.chosen ? 0xFF80D7AA : this.isHovered() ? 0xFFFFFFFF : 0xFF666666;
            fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, border);
            fill(matrices, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, 0xFF202020);
            int textX = this.x + 34;
            int textY = this.y + (this.height - this.lines.size() * 10) / 2;
            if (layout.gallery) {
                int imageHeight = this.height - 32;
                if (this.preview != null) {
                    RenderSystem.color4f(1, 1, 1, 1);
                    client.getTextureManager().bindTexture(this.preview);
                    // UVs span one image; the destination keeps the original 16:9 ratio.
                    drawTexture(matrices, this.x + 1, this.y + 1, this.width - 2, imageHeight,
                            0, 0, 480, 270, 480, 270);
                } else {
                    client.getItemRenderer().renderInGui(this.icon, this.x + this.width / 2 - 8, this.y + imageHeight / 2 - 8);
                }
                textX = this.x + 6;
                textY = this.y + this.height - 26;
            } else {
                client.getItemRenderer().renderInGui(this.icon, this.x + 9, this.y + this.height / 2 - 8);
            }
            for (StringRenderable line : this.lines) {
                textRenderer.drawWithShadow(matrices, line.getString(), textX, textY, this.chosen ? 0xA5F0C9 : 0xFFFFFF);
                textY += 10;
            }
        }
    }

    private static Item iconFor(FilterCatalog.Entry entry) {
        return SeedTypeIcon.item(SeedVisualType.forFilter(entry.id));
    }
}
