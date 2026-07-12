package zsgrooms.modid.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {
    @Shadow
    @Final
    protected Screen parent;

    private static String zsgRoomsDeleteStatus = "";
    private static long zsgRoomsDeleteStatusUntil;
    private static int zsgRoomsDeleteStatusColor = 0x88FF88;

    protected SelectWorldScreenMixin() {
        super(new LiteralText("Select World"));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void zsgRooms$addClearSpeedrunWorldsButton(CallbackInfo ci) {
        int buttonWidth = this.width < 420 ? 96 : 160;
        String label = this.width < 420 ? "Clear Runs" : "Clear Speedrun Worlds";
        this.addButton(new ButtonWidget(8, 8, buttonWidth, 20, new LiteralText(label), button -> zsgRooms$confirmClear()));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void zsgRooms$renderDeleteStatus(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!zsgRoomsDeleteStatus.isEmpty() && System.currentTimeMillis() < zsgRoomsDeleteStatusUntil) {
            int buttonWidth = this.width < 420 ? 96 : 160;
            drawCenteredString(matrices, this.textRenderer, zsgRoomsDeleteStatus,
                    8 + buttonWidth / 2, 32, zsgRoomsDeleteStatusColor);
        }
    }

    private void zsgRooms$confirmClear() {
        if (this.client == null) {
            return;
        }
        Screen currentScreen = (Screen) (Object) this;
        this.client.openScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                this.client.openScreen(currentScreen);
                return;
            }
            this.client.openScreen(new ProgressScreen());
            DeleteResult result = zsgRooms$clearSpeedrunWorlds();
            zsgRooms$setDeleteStatus(result);
            this.client.openScreen(new SelectWorldScreen(this.parent));
        }, new LiteralText("Clear Speedrun Worlds?"),
                new LiteralText("Permanently delete every world named Set Speedrun #...?"),
                new LiteralText("Clear Worlds"), new LiteralText("Cancel")));
    }

    private DeleteResult zsgRooms$clearSpeedrunWorlds() {
        if (this.client == null) {
            return new DeleteResult(0, 0, true);
        }

        int deleted = 0;
        int failed = 0;
        try {
            LevelStorage storage = this.client.getLevelStorage();
            List<LevelSummary> worlds = this.client.getLevelStorage().getLevelList();
            for (LevelSummary world : worlds) {
                if (zsgRooms$isSpeedrunWorld(world)) {
                    try (LevelStorage.Session session = storage.createSession(world.getName())) {
                        session.deleteSessionLock();
                        deleted += 1;
                    } catch (IOException exception) {
                        failed += 1;
                    }
                }
            }
        } catch (LevelStorageException exception) {
            return new DeleteResult(deleted, failed, true);
        }
        return new DeleteResult(deleted, failed, false);
    }

    private boolean zsgRooms$isSpeedrunWorld(LevelSummary world) {
        return zsgRooms$isSpeedrunName(world.getName()) || zsgRooms$isSpeedrunName(world.getDisplayName());
    }

    private boolean zsgRooms$isSpeedrunName(String name) {
        return name != null && name.trim().startsWith("Set Speedrun #");
    }

    private void zsgRooms$setDeleteStatus(DeleteResult result) {
        if (result.listFailed) {
            zsgRoomsDeleteStatus = "Could not read the world list";
            zsgRoomsDeleteStatusColor = 0xFF7777;
        } else if (result.failed > 0) {
            zsgRoomsDeleteStatus = "Deleted " + result.deleted + ", failed " + result.failed;
            zsgRoomsDeleteStatusColor = 0xFFCC66;
        } else if (result.deleted == 1) {
            zsgRoomsDeleteStatus = "Deleted 1 speedrun world";
            zsgRoomsDeleteStatusColor = 0x88FF88;
        } else {
            zsgRoomsDeleteStatus = "Deleted " + result.deleted + " speedrun worlds";
            zsgRoomsDeleteStatusColor = 0x88FF88;
        }
        zsgRoomsDeleteStatusUntil = System.currentTimeMillis() + 2500L;
    }

    private static final class DeleteResult {
        private final int deleted;
        private final int failed;
        private final boolean listFailed;

        private DeleteResult(int deleted, int failed, boolean listFailed) {
            this.deleted = deleted;
            this.failed = failed;
            this.listFailed = listFailed;
        }
    }
}
