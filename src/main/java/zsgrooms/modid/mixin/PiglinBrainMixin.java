package zsgrooms.modid.mixin;

import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.ZsgRooms;

import java.util.List;
import java.util.Random;

@Mixin(PiglinBrain.class)
public abstract class PiglinBrainMixin {
    private static final Identifier STANDARDIZED_BARTERING_LOOT =
            ZsgRooms.id("gameplay/standardized_piglin_bartering");

    @Inject(method = "getBarteredItem", at = @At("HEAD"), cancellable = true)
    private static void zsgRooms$standardizePiglinBarter(
            PiglinEntity piglin, CallbackInfoReturnable<List<ItemStack>> cir) {
        boolean standardized = RngStandardization.isEnabled();
        boolean boosted = RngStandardization.areBartersBoosted();
        if ((!standardized && !boosted) || !(piglin.world instanceof ServerWorld)) {
            return;
        }
        ServerWorld world = (ServerWorld) piglin.world;
        LootTable lootTable = world.getServer().getLootManager().getTable(
                boosted ? STANDARDIZED_BARTERING_LOOT : LootTables.PIGLIN_BARTERING_GAMEPLAY);
        Random random = standardized ? RngStandardization.nextPiglinBarterRandom(world) : world.random;
        cir.setReturnValue(lootTable.generateLoot(
                new LootContext.Builder(world)
                        .parameter(LootContextParameters.THIS_ENTITY, piglin)
                        .random(random)
                        .build(LootContextTypes.BARTER)));
    }
}
