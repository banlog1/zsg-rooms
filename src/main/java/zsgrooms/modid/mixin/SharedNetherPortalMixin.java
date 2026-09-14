package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.SharedNetherEntry;

import java.util.Random;

@Mixin(PortalForcer.class)
public abstract class SharedNetherPortalMixin {
    @Shadow @Final private ServerWorld world;

    @Redirect(method = "createPortal", at = @At(value = "INVOKE",
            target = "Ljava/util/Random;nextInt(I)I"))
    private int zsgRooms$firstEntryOrientation(Random random, int bound, Entity entity) {
        return SharedNetherEntry.portalOrientation(random, bound, entity, this.world);
    }
}
