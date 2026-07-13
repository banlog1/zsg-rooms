package zsgrooms.modid.mixin;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.BastionIronGuarantee;

@Mixin(LootableContainerBlockEntity.class)
public abstract class BastionChestLootMixin {
    @Shadow protected Identifier lootTableId;
    @Shadow protected long lootTableSeed;
    @Unique private Identifier zsgRooms$openedBastionLootTable;
    @Unique private long zsgRooms$openedBastionLootSeed;
    @Unique private int zsgRooms$lootInteractionDepth;

    @Inject(method = "checkLootInteraction", at = @At("HEAD"))
    private void zsgRooms$captureBastionLootTable(PlayerEntity player, CallbackInfo ci) {
        this.zsgRooms$lootInteractionDepth++;
        LootableContainerBlockEntity container = (LootableContainerBlockEntity) (Object) this;
        if (this.zsgRooms$lootInteractionDepth == 1
                && container.getWorld() instanceof ServerWorld
                && (Object) this instanceof ChestBlockEntity
                && BastionIronGuarantee.shouldInspect(this.lootTableId)) {
            this.zsgRooms$openedBastionLootTable = this.lootTableId;
            this.zsgRooms$openedBastionLootSeed = this.lootTableSeed;
        }
    }

    @Inject(method = "checkLootInteraction", at = @At("TAIL"))
    private void zsgRooms$ensureMinimumBastionIron(PlayerEntity player, CallbackInfo ci) {
        this.zsgRooms$lootInteractionDepth = Math.max(0, this.zsgRooms$lootInteractionDepth - 1);
        if (this.zsgRooms$lootInteractionDepth != 0) {
            return;
        }
        Identifier lootTable = this.zsgRooms$openedBastionLootTable;
        this.zsgRooms$openedBastionLootTable = null;
        if (lootTable != null) {
            BastionIronGuarantee.topUpFirstChest((Inventory) this, lootTable,
                    this.zsgRooms$openedBastionLootSeed);
        }
        this.zsgRooms$openedBastionLootSeed = 0L;
    }
}
