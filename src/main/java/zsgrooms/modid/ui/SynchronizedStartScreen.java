package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;

public class SynchronizedStartScreen extends Screen {
    private final String roomName;

    public SynchronizedStartScreen(String roomName) {
        super(new LiteralText("Synchronized Start"));
        this.roomName = roomName;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x88000000);

        int panelWidth = Math.min(320, this.width - 24);
        int panelHeight = 112;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = Math.max(12, (this.height - panelHeight) / 2);
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xEE080808);
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 28, 0xCC1A120C);

        drawCenteredString(matrices, this.textRenderer, "World Ready", this.width / 2, panelY + 10, 0x88FF88);
        drawCenteredString(matrices, this.textRenderer, "Waiting for all players...", this.width / 2, panelY + 43, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, readyCount(), this.width / 2, panelY + 62, 0xA8D8FF);
        drawCenteredString(matrices, this.textRenderer, "The race starts automatically", this.width / 2, panelY + 86, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private String readyCount() {
        Room room = ZsgRooms.getRoom(this.roomName);
        InGame game = ZsgRooms.getGame(this.roomName);
        int total = room == null ? 1 : Math.max(1, room.getPlayerCount());
        int ready = game == null ? 1 : Math.min(total, game.getReadyPlayerCount());
        return ready + " / " + total + " ready";
    }
}
