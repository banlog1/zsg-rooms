// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MilestoneGlyphsTest {
    private int[] pixels(Milestones.Kind kind, int width, int height) {
        int[] pixels = new int[12 * 12];
        MilestoneGlyphs.draw(kind, 31, 17, width, height, (x, y, w, h, color) -> {
            assertTrue(x >= 31 && y >= 17);
            assertTrue(w > 0 && h == 1);
            assertTrue(x + w <= 31 + Math.min(12, width));
            assertTrue(y + h <= 17 + Math.min(12, height));
            assertEquals(0xFF, color >>> 24);
            for (int column = x; column < x + w; column++) {
                int index = (y - 17) * 12 + column - 31;
                assertEquals(0, pixels[index], "Glyph runs must not double-paint pixels");
                pixels[index] = color;
            }
        });
        return pixels;
    }

    @Test void everyKindHasANonblankDistinctBoundedGlyph() {
        Set<String> silhouettes = new HashSet<>();
        for (Milestones.Kind kind : Milestones.Kind.values()) {
            int[] pixels = pixels(kind, 12, 12);
            assertTrue(Arrays.stream(pixels).filter(pixel -> pixel != 0).count() >= 30);
            StringBuilder silhouette = new StringBuilder();
            for (int pixel : pixels) silhouette.append(pixel == 0 ? '.' : '#');
            assertTrue(silhouettes.add(silhouette.toString()), "Kinds must differ by shape, not only color");
            assertArrayEquals(pixels, pixels(kind, 16, 16));
        }
    }

    @Test void allNarrowClipsMatchTheFullGlyphWithoutEscaping() {
        for (Milestones.Kind kind : Milestones.Kind.values()) {
            int[] full = pixels(kind, 12, 12);
            for (int width = 0; width <= 12; width++) {
                for (int height = 0; height <= 12; height++) {
                    int[] clipped = pixels(kind, width, height);
                    for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                        assertEquals(x < width && y < height ? full[y * 12 + x] : 0, clipped[y * 12 + x]);
                    }
                }
            }
            assertArrayEquals(new int[144], pixels(kind, -1, -1));
        }
    }

    @Test void portalInteriorAndFrameContrastAndFortressHasBattlements() {
        int[] portal = pixels(Milestones.Kind.NETHER, 12, 12);
        assertNotEquals(portal[2], portal[2 * 12 + 5]);
        assertNotEquals(0, portal[2 * 12 + 5]);
        int[] fortress = pixels(Milestones.Kind.FORTRESS, 12, 12);
        assertNotEquals(0, fortress[1]);
        assertEquals(0, fortress[3]);
        assertNotEquals(0, fortress[5]);
    }

    @Test void previewGlyphsOnDarkAndLightBackgrounds() throws Exception {
        int width = Milestones.Kind.values().length * 120;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            for (int row = 0; row < 2; row++) {
                graphics.setColor(new java.awt.Color(row == 0 ? 0x202625 : 0xD8DFDB));
                graphics.fillRect(0, row * 112, width, 112);
                for (Milestones.Kind kind : Milestones.Kind.values()) {
                    final int left = kind.ordinal() * 120 + 24, top = row * 112 + 10;
                    MilestoneGlyphs.draw(kind, 0, 0, MilestoneGlyphs.SIZE, MilestoneGlyphs.SIZE, (x, y, w, h, color) -> {
                        graphics.setColor(new java.awt.Color(color, true));
                        graphics.fillRect(left + x * 6, top + y * 6, w * 6, h * 6);
                    });
                    graphics.setColor(row == 0 ? java.awt.Color.WHITE : java.awt.Color.BLACK);
                    graphics.drawString(kind.label, kind.ordinal() * 120 + 24, row * 112 + 100);
                }
            }
        } finally { graphics.dispose(); }
        java.nio.file.Path output = java.nio.file.Paths.get("build/test-previews/milestones.png");
        java.nio.file.Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(image, "png", output.toFile());
    }

}
