package zsgrooms.modid.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.replay.ReplayChestLootPacket;
import zsgrooms.modid.replay.ReplayPrototype;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChunkDataS2CPacket.class)
public abstract class ReplayChestLootPacketMixin implements ReplayChestLootPacket {
    @Unique private List<Loot> zsgRooms$loot;

    @Redirect(method = "<init>(Lnet/minecraft/world/chunk/WorldChunk;IZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/entity/BlockEntity;toInitialChunkDataTag()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag zsgRooms$captureLoot(BlockEntity entity) {
        // Reuse vanilla's enumeration. Reading these fields cannot generate loot or advance RNG.
        if (entity instanceof ChestBlockEntity && entity.getWorld() != null && !entity.getWorld().isClient
                && ReplayPrototype.isCapturingChestLoot()) {
            ReplayLootableAccessor loot = (ReplayLootableAccessor) entity;
            Identifier table = loot.zsgRooms$getLootTableId();
            if (table != null && table.toString().length() <= 128 && loot.zsgRooms$getLootTableSeed() != 0) {
                if (zsgRooms$loot == null) zsgRooms$loot = new ArrayList<>();
                if (zsgRooms$loot.size() < 256) zsgRooms$loot.add(new Loot(entity.getPos().asLong(),
                        Block.getRawIdFromState(entity.getCachedState()), entity.getWorld().getRegistryKey().getValue().toString(),
                        table.toString(), loot.zsgRooms$getLootTableSeed()));
            }
        }
        return entity.toInitialChunkDataTag();
    }

    @Override public List<Loot> zsgRooms$getChestLoot() { return zsgRooms$loot; }
}
