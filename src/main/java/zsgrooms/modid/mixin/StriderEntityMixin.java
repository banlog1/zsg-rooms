package zsgrooms.modid.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.StriderJockeyControl;

@Mixin(StriderEntity.class)
public abstract class StriderEntityMixin extends AnimalEntity {
    protected StriderEntityMixin(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$removeNaturalJockeys(
            WorldAccess world,
            LocalDifficulty difficulty,
            SpawnReason spawnReason,
            EntityData entityData,
            CompoundTag entityTag,
            CallbackInfoReturnable<EntityData> cir
    ) {
        if (!StriderJockeyControl.isEnabled()) {
            return;
        }

        EntityData noJockeyData = entityData;
        // Existing StriderData carries the natural group spawn counter and baby chance.
        // Passing it through preserves those values while cancellation skips rider creation.
        if (!this.isBaby() && !(entityData instanceof StriderEntity.StriderData)) {
            StriderJockeyControl.consumeVanillaSelectionRolls(this.random);
            StriderEntity.StriderData striderData = new StriderEntity.StriderData(
                    StriderEntity.StriderData.RiderType.NO_RIDER);
            striderData.setBabyChance(0.5F);
            noJockeyData = striderData;
        }

        cir.setReturnValue(super.initialize(
                world,
                difficulty,
                spawnReason,
                noJockeyData,
                entityTag));
    }
}
