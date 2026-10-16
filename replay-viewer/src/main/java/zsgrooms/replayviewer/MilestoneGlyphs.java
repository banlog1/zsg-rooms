// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Hand-pixelled silhouettes with dark edges and compact, subject-specific palettes. */
final class MilestoneGlyphs {
    static final int SIZE = 12;
    private static final String[] NETHER = {
            "..00000000..", "..03311310..", "..03222410..", "..01244230..",
            "..03242210..", "..01224230..", "..03244210..", "..01222430..",
            "..03242210..", "..01224230..", "..03131310..", "..00000000.."};
    private static final String[] BASTION = {
            "000......000", "0430....0340", ".0320000230.", ".0333333320.",
            ".0352332530.", ".0302332030.", ".0234444320.", ".0241441420.",
            ".0524444250.", ".0522222250.", "..02222220..", "...000000..."};
    private static final String[] FORTRESS = {
            "000..00..000", "032003300230", "032223222230", "012112112210",
            ".0322322320.", ".0211211220.", ".0322322320.", ".0220000220.",
            ".0320..0230.", ".0220..0220.", "03220..02230", "00000..00000"};
    private static final String[] STRONGHOLD = {
            "............", "....0000....", "..00333300..", ".0332222330.",
            "033225522230", "032250022230", "032240012230", "032224422230",
            ".0322222230.", "..00333300..", "....0000....", "............"};
    private static final String[] END = {
            "............", "...000000...", "..03343330..", ".0342222430.",
            "034211112430", "032115511230", "032151151230", "034211112430",
            ".0342222430.", "..03343330..", "...000000...", "............"};
    private static final String[] DRAGON = {
            ".00......00.", ".030....030.", ".0320000230.", "..03222230..",
            ".0322222230.", "032552255230", "032442244230", ".0222222220.",
            "..02222220..", "...033330...", "...031130...", "....0000...."};
    private static final int[][] PALETTES = {
            {0xFF14111C, 0xFF342C47, 0xFF8540BB, 0xFF686079, 0xFFCC83F0, 0xFFF5D2FF},
            {0xFF342419, 0xFF604533, 0xFFA97D5D, 0xFFD7AB7E, 0xFFE5A99A, 0xFFFFEAC7},
            {0xFF241C23, 0xFF462B36, 0xFF854C52, 0xFFD18577, 0xFFF4B578, 0xFFFFDEAF},
            {0xFF12272C, 0xFF1D4446, 0xFF44A783, 0xFFB9D98A, 0xFF6AD4BD, 0xFFE9F7CF},
            {0xFF192728, 0xFF151529, 0xFF3D7262, 0xFFE2DDA2, 0xFF9BAE77, 0xFFC29AEA},
            {0xFF111019, 0xFF23212E, 0xFF383442, 0xFF817689, 0xFF9F47C5, 0xFFF2B5FF}
    };

    interface Rects { void draw(int x, int y, int width, int height, int color); }

    private MilestoneGlyphs() { }

    static void draw(Milestones.Kind kind, int x, int y, int clipWidth, int clipHeight, Rects rects) {
        String[] pixels;
        switch (kind) {
            case NETHER: pixels = NETHER; break;
            case BASTION: pixels = BASTION; break;
            case FORTRESS: pixels = FORTRESS; break;
            case STRONGHOLD: pixels = STRONGHOLD; break;
            case END: pixels = END; break;
            case DRAGON: pixels = DRAGON; break;
            default: throw new IllegalArgumentException("Missing milestone glyph: " + kind);
        }
        for (int row = 0; row < Math.min(SIZE, clipHeight); row++) {
            for (int column = 0; column < Math.min(SIZE, clipWidth);) {
                char pixel = pixels[row].charAt(column);
                int end = column + 1;
                while (end < Math.min(SIZE, clipWidth) && pixels[row].charAt(end) == pixel) end++;
                if (pixel != '.') rects.draw(x + column, y + row, end - column, 1,
                        PALETTES[kind.ordinal()][pixel - '0']);
                column = end;
            }
        }
    }
}
