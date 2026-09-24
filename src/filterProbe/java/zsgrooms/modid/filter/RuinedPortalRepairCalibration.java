package zsgrooms.modid.filter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import zsgrooms.modid.InGame;
import zsgrooms.modid.RuinedPortalChestRepair;
import zsgrooms.modid.StructureSpawnProximity;
import zsgrooms.modid.ZsgRooms;

import java.util.UUID;

/** Offline regression for portal terrain corruption during synchronous random-mode launch. */
final class RuinedPortalRepairCalibration {
    private static final long SEED = 3818903892210348937L;
    private static final BlockPos CHEST = new BlockPos(1, 72, 29);
    private static final String SPECIFICATION = SEED + "|structure:rpseedbank|iron:4|selection:rooms-mix";

    static void run(MinecraftServer server) {
        try {
            Long expectedSeed = null;
            BlockState expectedState = null;
            for (boolean delayedRoomUpdate : new boolean[]{false, true}) {
                String room = "portal-repair-calibration";
                ZsgRooms.createRoom(room, 2, 1, "rooms-mix", "Host");
                InGame game = ZsgRooms.getGame(room);
                if (!delayedRoomUpdate) game.setSeed(SPECIFICATION);
                StructureSpawnProximity.prepareNextLaunch(SPECIFICATION);
                try (ProbeWorlds ignored = new ProbeWorlds(server, SEED, UUID.randomUUID().toString())) {
                    ServerWorld world = server.getOverworld();
                    for (int x = -1; x <= 1; x++) {
                        for (int z = 0; z <= 2; z++) world.getChunk(x, z, ChunkStatus.FULL);
                    }
                    ZsgRooms.LOGGER.info("[PortalRepairTest] delayedRoomUpdate={} beforeRepair={}",
                            delayedRoomUpdate, world.getBlockState(CHEST));
                    game.setSeed(SPECIFICATION);
                    RuinedPortalChestRepair.tick(server);
                    BlockEntity entity = world.getBlockEntity(CHEST);
                    check(entity instanceof ChestBlockEntity, "Missing chest, delayedRoomUpdate=" + delayedRoomUpdate);
                    CompoundTag tag = entity.toTag(new CompoundTag());
                    check("minecraft:chests/ruined_portal".equals(tag.getString("LootTable")), "Wrong loot table");
                    if (expectedSeed == null) {
                        expectedSeed = tag.getLong("LootTableSeed");
                        expectedState = world.getBlockState(CHEST);
                    } else {
                        check(expectedSeed == tag.getLong("LootTableSeed"), "Changed original loot seed");
                        check(expectedState.equals(world.getBlockState(CHEST)), "Changed chest state");
                    }
                    // Normal looting consumes the loot table and must never refill the chest.
                    ((ChestBlockEntity) entity).checkLootInteraction(null);
                    ((ChestBlockEntity) entity).clear();
                    RuinedPortalChestRepair.tick(server);
                    check(((ChestBlockEntity) world.getBlockEntity(CHEST)).isEmpty(), "Loot was duplicated");
                    world.setBlockState(CHEST, Blocks.AIR.getDefaultState(), 3);
                    RuinedPortalChestRepair.tick(server);
                    check(world.getBlockState(CHEST).isAir(), "Looted chest was recreated");
                }
            }
            ZsgRooms.LOGGER.info("[PortalRepairTest] PASS: reported seed, synchronous random-mode launch, original loot/state, no loot refill");
        } catch (Throwable failure) {
            ZsgRooms.LOGGER.error("[PortalRepairTest] FAIL", failure);
        } finally {
            StructureSpawnProximity.prepareNextLaunch(null);
            server.stop(false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
