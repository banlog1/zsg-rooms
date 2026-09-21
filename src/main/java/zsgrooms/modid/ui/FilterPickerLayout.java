package zsgrooms.modid.ui;

final class FilterPickerLayout {
    static final int GAP = 8;
    static final int TOP = 64;
    final int columns;
    final int rows;
    final int cellWidth;
    final int cellHeight;
    final int left;
    final boolean gallery;

    FilterPickerLayout(int width, int height, boolean requestedGallery) {
        int availableWidth = Math.min(720, width - 24);
        int availableHeight = height - TOP - 38;
        this.columns = Math.max(1, Math.min(3, (availableWidth + GAP) / 148));
        this.cellWidth = (availableWidth - GAP * (this.columns - 1)) / this.columns;
        int galleryHeight = (this.cellWidth - 2) * 9 / 16 + 32;
        this.gallery = requestedGallery && availableHeight >= galleryHeight;
        this.cellHeight = this.gallery ? galleryHeight : 52;
        this.rows = Math.max(1, (availableHeight + GAP) / (this.cellHeight + GAP));
        this.left = (width - availableWidth) / 2;
    }

    int pageSize() { return this.columns * this.rows; }

    int pages(int count) { return Math.max(1, (count + pageSize() - 1) / pageSize()); }
}
