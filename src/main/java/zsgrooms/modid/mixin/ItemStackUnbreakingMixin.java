package zsgrooms.modid.mixin;

import net.minecraft.enchantment.UnbreakingEnchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.rng.MirroringRandom;

import java.util.Random;

@Mixin(ItemStack.class)
public abstract class ItemStackUnbreakingMixin {
    @Redirect(
            method = "damage(ILjava/util/Random;Lnet/minecraft/server/network/ServerPlayerEntity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/UnbreakingEnchantment;shouldPreventDamage("
                            + "Lnet/minecraft/item/ItemStack;ILjava/util/Random;)Z")
    )
    private boolean zsgRooms$standardizeUnbreakingRoll(
            ItemStack stack,
            int level,
            Random vanillaRandom,
            int amount,
            Random methodRandom,
            ServerPlayerEntity player
    ) {
        if (!RngStandardization.isEnabled()
                || player == null
                || !(player.world instanceof ServerWorld)) {
            return UnbreakingEnchantment.shouldPreventDamage(stack, level, vanillaRandom);
        }

        Random deterministicRandom = RngStandardization.nextUnbreakingRandom(
                (ServerWorld) player.world, stack);
        return UnbreakingEnchantment.shouldPreventDamage(
                stack,
                level,
                new MirroringRandom(vanillaRandom, deterministicRandom));
    }
}
