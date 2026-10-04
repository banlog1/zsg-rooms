package zsgrooms.modid.mixin;

import net.minecraft.server.command.SeedCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import zsgrooms.modid.RoomCommandPermissions;

import java.util.function.Predicate;

@Mixin(SeedCommand.class)
public abstract class RoomSeedCommandMixin {
    @ModifyArg(method = "register", at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;requires(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;", remap = false), index = 0)
    private static Predicate<ServerCommandSource> zsgRooms$seedPermission(Predicate<ServerCommandSource> vanilla) {
        return source -> (!(source.getEntity() instanceof ServerPlayerEntity)
                || !RoomCommandPermissions.forbidsCheats(source.getMinecraftServer())) && vanilla.test(source);
    }
}
