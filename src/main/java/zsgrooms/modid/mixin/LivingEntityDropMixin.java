package zsgrooms.modid.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.RngStandardization;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDropMixin {
    @Inject(method = "drop", at = @At("HEAD"))
    private void zsgRooms$standardizeMobDrops(DamageSource source, CallbackInfo ci) {
        Object entity = this;
        if (!RngStandardization.isEnabled() || !(entity instanceof MobEntity)
                || !(((MobEntity) entity).world instanceof ServerWorld)) {
            return;
        }
        MobEntity mob = (MobEntity) entity;
        ((EntityRandomAccessor) mob).zsgRooms$getRandom().setSeed(RngStandardization.nextMobDropSeed(mob));
    }
}
