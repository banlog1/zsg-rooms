package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;

public class RoomOptionsScreen extends Screen {
    private static final String[] SEED_TYPES = new String[]{
            "zsg",
            "zsgop",
            "zsgvillage",
            "zsgvillageop",
            "zsgshipwreck",
            "zsgshipwreckop",
            "zsgtemple",
            "zsgtempleop",
            "zsgjungletemple",
            "zsgjungletempleop",
            "rpseedbank",
            "random",
            "room",
            "manual"
    };

    private final Screen parent;
    private final String roomName;
    private TextFieldWidget manualSeedField;
    private ButtonWidget seedTypeButton;
    private int selectedSeedTypeIndex;
    private String initialManualSeed = "";

    public RoomOptionsScreen(Screen parent, String roomName) {
        super(new LiteralText("Room Options"));
        this.parent = parent;
        this.roomName = roomName;
        this.selectedSeedTypeIndex = 0;

        Room room = ZsgRooms.getRoom(roomName);
        if (room != null) {
            String current = ZsgSeedBridge.resolveStructure(room.getSeed());
            if ("manual".equals(current)) {
                this.initialManualSeed = ZsgSeedBridge.extractMinecraftSeed(room.getSeed());
            }
            for (int i = 0; i < SEED_TYPES.length; i++) {
                if (SEED_TYPES[i].equals(current)) {
                    this.selectedSeedTypeIndex = i;
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        String manualSeed = this.manualSeedField == null ? this.initialManualSeed : this.manualSeedField.getText();
        int panelX = panelX();
        int contentX = panelX + 16;
        int contentWidth = panelWidth() - 32;
        int y = panelY() + 54;

        this.seedTypeButton = new ButtonWidget(contentX, y, contentWidth, 20, seedTypeText(), button -> {
            this.selectedSeedTypeIndex = (this.selectedSeedTypeIndex + 1) % SEED_TYPES.length;
            button.setMessage(seedTypeText());
            updateManualSeedState();
        });
        this.addButton(this.seedTypeButton);

        this.manualSeedField = new TextFieldWidget(this.textRenderer, contentX, y + 30, contentWidth, 20, new LiteralText("Manual Seed"));
        this.manualSeedField.setText(manualSeed);
        updateManualSeedSuggestion();
        this.addButton(this.manualSeedField);
        updateManualSeedState();

        int buttonWidth = (contentWidth - 8) / 2;
        this.addButton(new ButtonWidget(contentX, y + 66, buttonWidth, 20, new LiteralText("Apply"), button -> {
            String seedType = selectedSeedTypeValue();
            if (!ZsgSeedBridge.isValidManualSeedSpecification(seedType)) {
                button.setMessage(new LiteralText("Enter Seed"));
                return;
            }
            ZsgRoomsClient.sendRoomAction("filter", this.roomName, seedType);
            this.client.openScreen(this.parent);
        }));

        this.addButton(new ButtonWidget(contentX + buttonWidth + 8, y + 66, buttonWidth, 20, new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.manualSeedField != null) {
            this.manualSeedField.tick();
            updateManualSeedSuggestion();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x77000000);
        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 150, 0xCC070707);
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 28, 0xAA1A120C);
        fill(matrices, panelX, panelY + 28, panelX + panelWidth, panelY + 29, 0xFF000000);
        drawCenteredString(matrices, this.textRenderer, "Room Options", this.width / 2, panelY + 10, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, "Filter for the next seed", this.width / 2, panelY + 36, 0xA8D8FF);
        super.render(matrices, mouseX, mouseY, delta);
    }

    private LiteralText seedTypeText() {
        return new LiteralText(ZsgSeedBridge.seedTypeLabel(currentSeedType()));
    }

    private String currentSeedType() {
        return SEED_TYPES[this.selectedSeedTypeIndex];
    }

    private String selectedSeedTypeValue() {
        String seedType = currentSeedType();
        if ("manual".equals(seedType)) {
            return "manual:" + this.manualSeedField.getText().trim();
        }
        return seedType;
    }

    private void updateManualSeedState() {
        if (this.manualSeedField != null) {
            boolean manual = "manual".equals(currentSeedType());
            this.manualSeedField.active = manual;
            this.manualSeedField.setEditable(manual);
        }
    }

    private int panelWidth() {
        return Math.min(284, this.width - 20);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(10, (this.height - 150) / 2);
    }

    private void updateManualSeedSuggestion() {
        if (this.manualSeedField != null) {
            this.manualSeedField.setSuggestion(
                    this.manualSeedField.getText().isEmpty() && this.manualSeedField.active ? "Manual seed" : ""
            );
        }
    }
}
