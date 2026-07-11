package zsgrooms.modid.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {
    @Shadow
    @Final
    protected Screen parent;

    private static String zsgRoomsDeleteStatus = "";
    private static long zsgRoomsDeleteStatusUntil;

    protected SelectWorldScreenMixin() {
        super(new LiteralText("Select World"));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void zsgRooms$addClearSpeedrunWorldsButton(CallbackInfo ci) {
        int buttonWidth = this.width < 420 ? 96 : 160;
        String label = this.width < 420 ? "Clear Runs" : "Clear Speedrun Worlds";
        this.addButton(new ButtonWidget(8, 8, buttonWidth, 20, new LiteralText(label), button -> {
            int deleted = zsgRooms$clearSpeedrunWorlds();
            zsgRoomsDeleteStatus = deleted == 1 ? "Deleted 1 speedrun world" : "Deleted " + deleted + " speedrun worlds";
            zsgRoomsDeleteStatusUntil = System.currentTimeMillis() + 1800L;
            if (this.client != null) {
                this.client.openScreen(new SelectWorldScreen(this.parent));
            }
        }));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void zsgRooms$renderDeleteStatus(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!zsgRoomsDeleteStatus.isEmpty() && System.currentTimeMillis() < zsgRoomsDeleteStatusUntil) {
            int buttonWidth = this.width < 420 ? 96 : 160;
            drawCenteredString(matrices, this.textRenderer, zsgRoomsDeleteStatus, 8 + buttonWidth / 2, 32, 0x88FF88);
        }
    }

    private int zsgRooms$clearSpeedrunWorlds() {
        if (this.client == null) {
            return 0;
        }

        int deleted = 0;
        try {
            List<LevelSummary> worlds = this.client.getLevelStorage().getLevelList();
            for (LevelSummary world : worlds) {
                if (zsgRooms$isSpeedrunWorld(world)) {
                    zsgRooms$deleteWorld(world.getFile().toPath());
                    deleted += 1;
                }
            }
        } catch (LevelStorageException | IOException exception) {
            zsgRoomsDeleteStatus = "Could not clear speedrun worlds";
            zsgRoomsDeleteStatusUntil = System.currentTimeMillis() + 2500L;
        }
        return deleted;
    }

    private boolean zsgRooms$isSpeedrunWorld(LevelSummary world) {
        return zsgRooms$isSpeedrunName(world.getName()) || zsgRooms$isSpeedrunName(world.getDisplayName());
    }

    private boolean zsgRooms$isSpeedrunName(String name) {
        return name != null && name.trim().startsWith("Set Speedrun #");
    }

    private void zsgRooms$deleteWorld(Path worldPath) throws IOException {
        if (worldPath == null || !Files.exists(worldPath)) {
            return;
        }
        Files.walk(worldPath)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
    }
}
