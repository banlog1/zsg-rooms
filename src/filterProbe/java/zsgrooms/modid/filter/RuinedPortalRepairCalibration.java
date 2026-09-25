package zsgrooms.modid.filter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.ChunkStatus;
import zsgrooms.modid.InGame;
import zsgrooms.modid.RuinedPortalChestRepair;
import zsgrooms.modid.StructureSpawnProximity;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ui.RoomUiPreferences;

import java.util.UUID;

/** Offline regression for portal terrain corruption during synchronous random-mode launch. */
final class RuinedPortalRepairCalibration {
    private static final long SEED = 3818903892210348937L;
    private static final BlockPos CHEST = new BlockPos(1, 72, 29);
    private static final String SPECIFICATION = SEED + "|structure:rpseedbank|iron:4|selection:rooms-mix";

    static void run(MinecraftServer server) {
        boolean originalTesting = RoomUiPreferences.isRuinedPortalRepairTestingEnabled();
        try {
            Long expectedSeed = null;
            BlockState expectedState = null;
            for (int scenario = 0; scenario < 3; scenario++) {
                boolean delayedRoomUpdate = scenario == 1;
                boolean standalone = scenario == 2;
                RoomUiPreferences.setRuinedPortalRepairTestingEnabled(standalone);
                String room = "portal-repair-calibration";
                ZsgRooms.createRoom(room, 2, 1, "rooms-mix", "Host");
                InGame game = ZsgRooms.getGame(room);
                if (!delayedRoomUpdate) game.setSeed(SPECIFICATION);
                StructureSpawnProximity.prepareNextLaunch(SPECIFICATION);
                if (standalone) {
                    ZsgRooms.leaveRoomLocally(room);
                    StructureSpawnProximity.prepareNextLaunch(null);
                }
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
                    checkClearance(world, server);
                    RuinedPortalChestRepair.stop(server);
                }
            }
            ZsgRooms.LOGGER.info("[PortalRepairTest] PASS: automatic RP and random mode, standalone testing, original loot/state, one-shot clearance, player protection");
        } catch (Throwable failure) {
            ZsgRooms.LOGGER.error("[PortalRepairTest] FAIL", failure);
        } finally {
            RoomUiPreferences.setRuinedPortalRepairTestingEnabled(originalTesting);
            StructureSpawnProximity.prepareNextLaunch(null);
            server.stop(false);
        }
    }

    private static void checkClearance(ServerWorld world, MinecraftServer server) {
        BlockPos pos = new BlockPos(8, 100, 8);
        BlockState chest = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH);
        for (BlockPos air : BlockPos.iterate(pos.add(-4, -1, -4), pos.add(4, 4, 4))) {
            world.setBlockState(air, Blocks.AIR.getDefaultState(), 18);
        }
        queueChest(world, pos, chest);
        world.setBlockState(pos, Blocks.NETHERRACK.getDefaultState(), 18);
        world.setBlockState(pos.up(), Blocks.DIRT.getDefaultState(), 18);
        world.setBlockState(pos.north(), Blocks.DIRT.getDefaultState(), 18);
        RuinedPortalChestRepair.tick(server);
        check(world.getBlockState(pos).equals(chest), "Fixture chest not restored");
        check(world.getBlockState(pos.up()).isAir() && world.getBlockState(pos.north()).isAir(), "Access not cleared");
        check(world.getBlockEntity(pos).toTag(new CompoundTag()).getLong("LootTableSeed") == 7L, "Fixture loot changed");
        world.setBlockState(pos.up(), Blocks.DIRT.getDefaultState(), 18);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 18);
        RuinedPortalChestRepair.tick(server);
        check(world.getBlockState(pos.up()).isOf(Blocks.DIRT), "Player terrain removed later");
        check(world.getBlockState(pos).isAir(), "Unlooted broken chest recreated");

        queueChest(world, pos, chest);
        RuinedPortalChestRepair.onPlayerInteraction(world, pos);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 18);
        RuinedPortalChestRepair.tick(server);
        check(world.getBlockState(pos).isAir(), "Pre-tick interaction ignored");

        queueChest(world, pos, chest);
        world.setBlockState(pos.north(), Blocks.OBSIDIAN.getDefaultState(), 18);
        RuinedPortalChestRepair.tick(server);
        check(world.getBlockState(pos.up()).isOf(Blocks.DIRT), "Unsafe pocket partly cleared");
        check(world.getBlockState(pos.north()).isOf(Blocks.OBSIDIAN), "Portal frame cleared");
    }

    private static void queueChest(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, 18);
        ((ChestBlockEntity) world.getBlockEntity(pos)).setLootTable(new Identifier("minecraft:chests/ruined_portal"), 7L);
        RuinedPortalChestRepair.captureGenerated(world, pos, state);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
