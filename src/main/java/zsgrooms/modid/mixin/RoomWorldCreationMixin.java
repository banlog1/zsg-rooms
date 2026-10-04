package zsgrooms.modid.mixin;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.InGame;
import zsgrooms.modid.RoomCommandPermissions;
import zsgrooms.modid.RoomWorldCreation;
import zsgrooms.modid.ZsgRooms;

@Mixin(CreateWorldScreen.class)
public abstract class RoomWorldCreationMixin {
    @Redirect(method = "createLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;method_29607(Ljava/lang/String;Lnet/minecraft/world/level/LevelInfo;Lnet/minecraft/util/registry/RegistryTracker$Modifiable;Lnet/minecraft/world/gen/GeneratorOptions;)V"))
    private void zsgRooms$createWorld(MinecraftClient client, String directory, LevelInfo original,
                                    RegistryTracker.Modifiable registries, GeneratorOptions generator) {
        if (!RoomWorldCreation.isRoomCreation(ZsgRooms.hasManagedRoom(), getClass().getName())) {
            client.method_29607(directory, original, registries, generator);
            return;
        }
        InGame game = ZsgRooms.getGame(ZsgRooms.getActiveRoomName());
        boolean allowCheats = game != null && game.areCheatsAllowed();
        LevelInfo settings = RoomWorldCreation.settings(original, true, getClass().getName(), allowCheats);
        RoomCommandPermissions.duringCreation(directory, allowCheats,
                () -> client.method_29607(directory, settings, registries, generator));
    }
}
