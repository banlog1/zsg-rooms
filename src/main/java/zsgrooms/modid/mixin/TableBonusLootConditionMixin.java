package zsgrooms.modid.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.loot.condition.TableBonusLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.rng.MirroringRandom;

import java.util.Random;

@Mixin(TableBonusLootCondition.class)
public abstract class TableBonusLootConditionMixin {
    @Shadow @Final private Enchantment enchantment;

    @Redirect(
            method = "test(Lnet/minecraft/loot/context/LootContext;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/loot/context/LootContext;getRandom()Ljava/util/Random;")
    )
    private Random zsgRooms$standardizeGravelFlintRoll(LootContext context) {
        Random vanillaRandom = context.getRandom();
        if (!RngStandardization.isEnabled()
                || this.enchantment != Enchantments.FORTUNE
                || !context.hasParameter(LootContextParameters.BLOCK_STATE)) {
            return vanillaRandom;
        }

        BlockState state = context.get(LootContextParameters.BLOCK_STATE);
        if (state == null || !state.isOf(Blocks.GRAVEL)) {
            return vanillaRandom;
        }

        return new MirroringRandom(
                vanillaRandom,
                RngStandardization.nextGravelFlintRandom(context.getWorld()));
    }
}
