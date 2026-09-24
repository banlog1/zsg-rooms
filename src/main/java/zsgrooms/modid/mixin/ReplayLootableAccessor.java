package zsgrooms.modid.mixin;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootableContainerBlockEntity.class)
public interface ReplayLootableAccessor {
    @Accessor("lootTableId") Identifier zsgRooms$getLootTableId();
    @Accessor("lootTableSeed") long zsgRooms$getLootTableSeed();
}
