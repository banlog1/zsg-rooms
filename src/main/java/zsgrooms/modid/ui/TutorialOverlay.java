package zsgrooms.modid.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;

import java.util.Collections;
import java.util.List;

/** Anchored tutorial presentation; the screen owns navigation and input. */
final class TutorialOverlay {
    public static final class Box {
        public final int x, y, width, height;

        public Box(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    // ReplaySplitsScreen / ReplayBrowserScreen: charcoal, mint selection, quiet rules.
    private static final int DIM = 0xB5101316;
    private static final int BACKGROUND = 0xED202324;
    private static final int ACCENT = 0xFF8FE2C8;
    private static final int RULE = 0x704F6666;
    private static final int TITLE = 0xEDF4F1;
    private static final int BODY = 0xD5DDDB;
    private static final int MUTED = 0x9BA6A7;
    private static final int PAD = 6, MARGIN = 8, GAP = 6;
    private static final int FOOTER = 30;
    private static final int MIN_WIDTH = 152;

    private TutorialOverlay() { }

    public static Box panel(int screenWidth, int screenHeight, Box target,
                            TextRenderer text, String title, String body) {
        return choosePanel(screenWidth, screenHeight, target, text, title, body);
    }

    public static boolean fitsText(TextRenderer text, Box panel, String title, String body) {
        return panel.width >= MIN_WIDTH && panel.height >= 64
                && layout(text, panel, title, body).fits(text, panel);
    }

    private static Box choosePanel(int screenWidth, int screenHeight, Box target,
                                   TextRenderer text, String title, String body) {
        int width = Math.max(0, screenWidth), height = Math.max(0, screenHeight);
        int margin = Math.min(MARGIN, Math.min(width, height) / 4);
        int gap = GAP;
        int right = width - margin, bottom = height - margin;
        Box hole = spotlight(width, height, target);
        int hx = clamp(hole.x - gap, margin, right);
        int hy = clamp(hole.y - gap, margin, bottom);
        int hr = clamp(hole.x + hole.width + gap, margin, right);
        int hb = clamp(hole.y + hole.height + gap, margin, bottom);
        // These four maximal strips cover all possible axis-aligned placements.
        Box[] areas = {
                new Box(hr, margin, right - hr, bottom - margin),
                new Box(margin, margin, hx - margin, bottom - margin),
                new Box(margin, hb, right - margin, bottom - hb),
                new Box(margin, margin, right - margin, hy - margin)
        };
        Box best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int preferredWidth = 248;
        int preferredHeight = 156;
        for (int i = 0; i < areas.length; i++) {
            Box area = areas[i];
            if (area.width < MIN_WIDTH || area.height < 64) continue;
            // Prefer a compact callout; widen only when wrapping benefits.
            for (int variant = 0; variant < 2; variant++) {
                int w = variant == 0 ? Math.min(preferredWidth, area.width) : area.width;
                if (variant == 1 && w == Math.min(preferredWidth, area.width)) continue;
                int required = layout(text, new Box(0, 0, w, area.height), title, body).requiredHeight;
                int h = Math.min(area.height, Math.max(preferredHeight, required));
                Box candidate = new Box(0, 0, w, h);
                boolean fits = fitsText(text, candidate, title, body);
                double coverage = Math.min(1.0, h / (double) Math.max(1, required));
                double preference = i == 0 ? 40 : i == 1 ? 30 : 20;
                double score = (fits ? 1000000 : coverage * 100000) + preference
                        + Math.min(1.0, w / 220.0) * 100 - Math.abs(w - preferredWidth) * 0.1;
                if (score <= bestScore) continue;
                int x = clamp(target.x, area.x, area.x + area.width - w);
                int y = clamp(target.y, area.y, area.y + area.height - h);
                if (i == 0) x = area.x;
                if (i == 1) x = area.x + area.width - w;
                if (i == 2) y = area.y;
                if (i == 3) y = area.y + area.height - h;
                best = new Box(x, y, w, h);
                bestScore = score;
            }
        }
        if (best == null) throw new IllegalArgumentException("No tutorial panel >=152x64 fits "
                + width + "x" + height + " around target " + geometry(target));
        return best;
    }

    /** Step is one-based. Render after underlying content and before parent-owned controls. */
    public static void render(MatrixStack matrices, TextRenderer text,
                              int screenWidth, int screenHeight, Box target, Box panel,
                              String title, String body, int step, int steps) {
        TextLayout content = layout(text, panel, title, body);
        if (!fitsText(text, panel, title, body)) {
            throw new IllegalArgumentException("Tutorial text does not fit panel "
                    + geometry(panel) + "; needs height " + content.requiredHeight
                    + " (title lines " + content.title.size() + ", body lines " + content.body.size()
                    + "). Shorten copy or adjust the highlight.");
        }
        int width = Math.max(0, screenWidth), height = Math.max(0, screenHeight);
        Box hole = spotlight(width, height, target);
        int hr = hole.x + hole.width, hb = hole.y + hole.height;
        rect(matrices, 0, 0, width, hole.y, DIM);
        rect(matrices, 0, hb, width, height, DIM);
        rect(matrices, 0, hole.y, hole.x, hb, DIM);
        rect(matrices, hr, hole.y, width, hb, DIM);
        // The frame sits outside the clear aperture, never over the highlighted control.
        rect(matrices, Math.max(0, hole.x - 1), Math.max(0, hole.y - 1),
                Math.min(width, hr + 1), hole.y, ACCENT);
        rect(matrices, Math.max(0, hole.x - 1), hb, Math.min(width, hr + 1), Math.min(height, hb + 1), ACCENT);
        rect(matrices, Math.max(0, hole.x - 1), hole.y, hole.x, hb, ACCENT);
        rect(matrices, hr, hole.y, Math.min(width, hr + 1), hb, ACCENT);

        if (panel.width <= 0 || panel.height <= 0) return;
        connector(matrices, hole, panel);
        int x = panel.x, y = panel.y, right = x + panel.width, bottom = y + panel.height;
        rect(matrices, x, y, right, bottom, BACKGROUND);
        rect(matrices, x, y, right, y + 1, RULE);
        rect(matrices, x, bottom - 1, right, bottom, RULE);
        rect(matrices, x, y, x + 1, bottom, RULE);
        rect(matrices, right - 1, y, right, bottom, RULE);

        int footerTop = bottom - FOOTER;
        if (footerTop > y + 30) rect(matrices, x + 10, footerTop - 1, right - 10, footerTop, RULE);
        int count = Math.max(1, steps), current = clamp(step, 1, count);
        // Parent: Back/Next at bottom-26; close at right-24, top+4 (20x20).
        String counter = (content.compact ? "" : count > TutorialScreen.QUICK_STEPS ? "FULL TOUR  " : "QUICK TOUR  ")
                + current + " / " + count;
        int counterWidth = content.compact ? panel.width - 160 : panel.width - 40;
        if (text.getWidth(counter) > counterWidth) {
            throw new IllegalArgumentException("Tutorial counter does not fit panel " + geometry(panel));
        }
        if (content.compact) {
            int center = x + panel.width / 2;
            text.drawWithShadow(matrices, counter, center - text.getWidth(counter) / 2, bottom - 20, MUTED);
        } else {
            text.drawWithShadow(matrices, counter, x + 12, y + 10, MUTED);
        }
        drawLines(matrices, text, content.title, x + content.offset, y + content.titleTop, content.lineHeight, TITLE);
        drawLines(matrices, text, content.body, x + content.offset, y + content.bodyTop, content.lineHeight, BODY);
    }

    private static Box spotlight(int width, int height, Box target) {
        int x = clamp(target.x - PAD, 0, width), y = clamp(target.y - PAD, 0, height);
        int right = clamp(target.x + Math.max(0, target.width) + PAD, 0, width);
        int bottom = clamp(target.y + Math.max(0, target.height) + PAD, 0, height);
        return new Box(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private static void connector(MatrixStack matrices, Box hole, Box panel) {
        int right = hole.x + hole.width, bottom = hole.y + hole.height;
        if (panel.x >= right || panel.x + panel.width <= hole.x) {
            int y = clamp(hole.y + hole.height / 2, panel.y + 4, panel.y + panel.height - 4);
            if (y < hole.y || y >= bottom) return;
            int from = panel.x >= right ? right + 1 : panel.x + panel.width;
            int to = panel.x >= right ? panel.x : hole.x - 1;
            rect(matrices, from, y, to, y + 1, ACCENT);
        } else {
            int x = clamp(hole.x + hole.width / 2, panel.x + 4, panel.x + panel.width - 4);
            if (x < hole.x || x >= right) return;
            int from = panel.y >= bottom ? bottom + 1 : panel.y + panel.height;
            int to = panel.y >= bottom ? panel.y : hole.y - 1;
            rect(matrices, x, from, x + 1, to, ACCENT);
        }
    }

    private static TextLayout layout(TextRenderer text, Box panel, String title, String body) {
        TextLayout regular = new TextLayout(text, panel.width, title, body, false);
        if (regular.fits(text, panel)) return regular;
        if (panel.width >= 220) {
            TextLayout compact = new TextLayout(text, panel.width, title, body, true);
            if (compact.requiredHeight < regular.requiredHeight) return compact;
        }
        return regular;
    }

    private static final class TextLayout {
        final boolean compact;
        final int offset, titleWidth, bodyWidth, titleTop, bodyTop, lineHeight, requiredHeight;
        final List<StringRenderable> title, body;

        TextLayout(TextRenderer text, int width, String title, String body, boolean compact) {
            this.compact = compact;
            offset = 12;
            bodyWidth = Math.max(1, width - offset - 12);
            titleWidth = Math.max(1, width - offset - (compact ? 28 : 12));
            lineHeight = Math.max(compact ? 10 : 11,
                    text.fontHeight + (compact ? 1 : 2));
            titleTop = compact ? 8 : 30;
            this.title = lines(text, title, titleWidth);
            this.body = lines(text, body, bodyWidth);
            int gap = this.title.isEmpty() || this.body.isEmpty() ? 0 : 6;
            bodyTop = Math.max(compact ? 24 : titleTop, titleTop + this.title.size() * lineHeight + gap);
            requiredHeight = Math.max(titleTop + this.title.size() * lineHeight,
                    bodyTop + this.body.size() * lineHeight) + FOOTER + 5;
        }

        boolean fits(TextRenderer text, Box panel) {
            if (panel.width < MIN_WIDTH || panel.height < requiredHeight) return false;
            for (StringRenderable line : title) if (text.getWidth(line.getString()) > titleWidth) return false;
            for (StringRenderable line : body) if (text.getWidth(line.getString()) > bodyWidth) return false;
            return true;
        }
    }

    private static List<StringRenderable> lines(TextRenderer text, String value, int width) {
        return value == null || value.isEmpty() ? Collections.emptyList() : text.wrapLines(new LiteralText(value), width);
    }

    private static void drawLines(MatrixStack matrices, TextRenderer text, List<StringRenderable> lines,
                                  int x, int y, int lineHeight, int color) {
        for (int i = 0; i < lines.size(); i++) {
            text.drawWithShadow(matrices, lines.get(i).getString(), x, y + i * lineHeight, color);
        }
    }

    private static String geometry(Box box) {
        return "[" + box.x + "," + box.y + " " + box.width + "x" + box.height + "]";
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static void rect(MatrixStack matrices, int x, int y, int right, int bottom, int color) {
        if (right > x && bottom > y) DrawableHelper.fill(matrices, x, y, right, bottom, color);
    }
}
