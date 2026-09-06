package zsgrooms.modid.ui;

final class HudPlayerRotation {
    private HudPlayerRotation() {
    }

    // Index into the occupied roster, not the room's potentially sparse player array.
    static int playerIndex(int count, int localIndex, int rows, boolean pinSelf,
                           long elapsedMillis, int seconds, int row) {
        int visible = Math.min(count, rows);
        if (row < 0 || row >= visible) {
            return -1;
        }
        boolean pinned = pinSelf && visible > 1 && localIndex >= 0 && localIndex < count;
        if (pinned && row == 0) {
            return localIndex;
        }
        int rotatingCount = count - (pinned ? 1 : 0);
        long step = count > visible ? Math.max(0L, elapsedMillis) / (Math.max(1, seconds) * 1000L) : 0L;
        int index = (int) ((step + row - (pinned ? 1 : 0)) % rotatingCount);
        return pinned && index >= localIndex ? index + 1 : index;
    }
}
