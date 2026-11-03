package zsgrooms.modid.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.replay.ReplayLocalSoundCapture;

@Mixin(BucketItem.class)
public abstract class ReplayLocalBucketSoundMixin {
    @Redirect(method = "playEmptyingSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/WorldAccess;playSound(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"))
    private void zsgRooms$localBucketSound(WorldAccess world, PlayerEntity player, BlockPos pos,
                                          SoundEvent sound, SoundCategory category, float volume, float pitch) {
        capture(player, pos, sound, category, volume, pitch);
        world.playSound(player, pos, sound, category, volume, pitch);
    }

    @Redirect(method = "placeFluid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"))
    private void zsgRooms$localEvaporationSound(World world, PlayerEntity player, BlockPos pos,
                                               SoundEvent sound, SoundCategory category, float volume, float pitch) {
        capture(player, pos, sound, category, volume, pitch);
        world.playSound(player, pos, sound, category, volume, pitch);
    }

    private static void capture(PlayerEntity player, BlockPos pos, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        ReplayLocalSoundCapture.world(player, sound, category, volume, pitch,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, true);
    }
}
