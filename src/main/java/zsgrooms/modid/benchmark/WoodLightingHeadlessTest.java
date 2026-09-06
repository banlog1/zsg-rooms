package zsgrooms.modid.benchmark;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import zsgrooms.modid.RngStandardization;
import zsgrooms.modid.WoodLightingStandardization;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.mixin.ServerWorldPlayerListAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Opt-in integration test using real lava/fire/portal code, not a simulated ignition formula. */
public final class WoodLightingHeadlessTest {
    private static final BlockPos FIRST = new BlockPos(8, 180, 8);
    private static final BlockPos SECOND = new BlockPos(75, 180, 8);
    private static final List<ServerPlayerEntity> PLAYERS = new ArrayList<ServerPlayerEntity>();
    private static boolean finished;

    private WoodLightingHeadlessTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("zsgrooms.woodLightingTest");
    }

    public static void register() {
        if (!isEnabled()) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(WoodLightingHeadlessTest::prepare);
        ServerTickEvents.END_SERVER_TICK.register(WoodLightingHeadlessTest::run);
    }

    private static void prepare(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        RngStandardization.configure(false);
        for (BlockPos origin : new BlockPos[]{FIRST, SECOND}) {
            for (int x = (origin.getX() >> 4) - 3; x <= (origin.getX() >> 4) + 3; x++) {
                for (int z = (origin.getZ() >> 4) - 3; z <= (origin.getZ() >> 4) + 3; z++) {
                    world.getChunk(x, z);
                    world.setChunkForced(x, z, true);
                }
            }
            ServerPlayerEntity player = new ServerPlayerEntity(server, world,
                    new GameProfile(UUID.randomUUID(), "WoodLightTest" + PLAYERS.size()),
                    new ServerPlayerInteractionManager(world));
            player.refreshPositionAndAngles(origin.getX() + 4, origin.getY() + 1, origin.getZ() + 4, 0, 0);
            player.networkHandler = new ServerPlayNetworkHandler(server,
                    new ClientConnection(NetworkSide.SERVERBOUND), player) {
                @Override
                public void sendPacket(Packet<?> packet) {
                }
            };
            ((ServerWorldPlayerListAccessor) world).zsgRooms$getBenchmarkPlayers().add(player);
            PLAYERS.add(player);
        }
        world.getGameRules().get(GameRules.DO_FIRE_TICK).set(true, server);
        server.getCommandManager().execute(server.getCommandSource(), "gamerule randomTickSpeed 3");
        world.setWeather(600000, 0, false, false);
    }

    private static void run(MinecraftServer server) {
        if (finished || server.getTicks() < 60) {
            return;
        }
        ServerWorld world = server.getOverworld();
        if (!world.getChunkManager().shouldTickBlock(FIRST) || !world.getChunkManager().shouldTickBlock(SECOND)) {
            if (server.getTicks() > 600) {
                finished = true;
                ZsgRooms.LOGGER.error("[WoodLightTest] FAIL: test chunks did not become ticking");
                server.stop(false);
            }
            return;
        }
        finished = true;
        try {
            compare(world, Difficulty.EASY, false, false, false);
            compare(world, Difficulty.HARD, false, false, false);
            compare(world, Difficulty.EASY, false, false, true);
            compare(world, Difficulty.HARD, false, false, true);
            compare(world, Difficulty.EASY, true, false, true);
            compare(world, Difficulty.HARD, false, true, true);
            ZsgRooms.LOGGER.info("[WoodLightTest] PASS: translated setups, staggered starts, difficulty change, extra vanilla attempts, rule pause, and disabled fallback");
        } catch (Throwable error) {
            ZsgRooms.LOGGER.error("[WoodLightTest] FAIL", error);
        } finally {
            RngStandardization.configure(false);
            WoodLightingStandardization.tick(world);
            ((ServerWorldPlayerListAccessor) world).zsgRooms$getBenchmarkPlayers().removeAll(PLAYERS);
            PLAYERS.clear();
            for (BlockPos origin : new BlockPos[]{FIRST, SECOND}) {
                for (int x = (origin.getX() >> 4) - 3; x <= (origin.getX() >> 4) + 3; x++) {
                    for (int z = (origin.getZ() >> 4) - 3; z <= (origin.getZ() >> 4) + 3; z++) {
                        world.setChunkForced(x, z, false);
                    }
                }
            }
            server.stop(false);
        }
    }

    private static void compare(ServerWorld world, Difficulty difficulty, boolean switchDifficulty, boolean pause, boolean fireFixture) {
        RngStandardization.configure(false);
        WoodLightingStandardization.tick(world);
        build(world, FIRST);
        build(world, SECOND);
        world.getServer().setDifficulty(difficulty, true);
        RngStandardization.configure(true);
        check(NetherPortalBlock.createAreaHelper(world, FIRST.up()) != null, "Fixture frame is invalid");
        check(world.isRegionLoaded(-26, 0, -26, 40, 255, 40), "Fixture surroundings are not loaded");
        check(world.getPlayers().contains(PLAYERS.get(0)), "Fixture player missing");
        activate(world, FIRST);
        if (fireFixture) {
            startFire(world, FIRST);
        }
        check(WoodLightingStandardization.suppressNaturalLava(world, FIRST.add(0, 0, 1)), "First setup was not detected");
        // Twenty ticks of unrelated elapsed world time must not move the second setup's local clock.
        for (int tick = 0; tick < 20; tick++) {
            WoodLightingStandardization.tick(world);
        }
        activate(world, SECOND);
        if (fireFixture) {
            startFire(world, SECOND);
        }
        check(WoodLightingStandardization.suppressNaturalLava(world, SECOND.add(0, 0, 1)), "Second setup was not detected");
        if (pause) {
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, world.getServer());
            for (int tick = 0; tick < 100; tick++) {
                WoodLightingStandardization.tick(world);
            }
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(true, world.getServer());
        }
        int first = lit(world, FIRST) ? 20 : -1;
        int second = -1;
        Random unrelated = new Random(98534L);
        for (int tick = 1; tick <= 120000 && (first < 0 || second < 0); tick++) {
            if (switchDifficulty && tick == 1) {
                // Match difficulty by each setup's local age, even though they started at different times.
                // The switch is before the first possible scheduled fire update in either setup.
                world.getServer().setDifficulty(Difficulty.HARD, true);
            }
            if (tick % 17 == 0) {
                for (int attempt = 0; attempt < 5; attempt++) {
                    BlockPos source = FIRST.add(0, 0, 1);
                    world.getFluidState(source).onRandomTick(world, source, unrelated);
                    if (fireFixture) {
                        BlockPos fire = FIRST.add(0, 1, 1);
                        if (world.getBlockState(fire).isOf(Blocks.FIRE)) {
                            world.getBlockState(fire).scheduledTick(world, fire, unrelated);
                        }
                    }
                }
            }
            WoodLightingStandardization.tick(world);
            if (first < 0 && lit(world, FIRST)) {
                first = tick + 20;
            }
            if (second < 0 && lit(world, SECOND)) {
                second = tick;
            }
        }
        check(first > 20 && first == second, "Lighting times differ: " + first + " versus " + second);
        RngStandardization.configure(false);
        WoodLightingStandardization.tick(world);
        check(!WoodLightingStandardization.suppressNaturalLava(world, FIRST.add(0, 0, 1)), "Disabled lava hook remained active");
        ZsgRooms.LOGGER.info("[WoodLightTest] {} switch={} pause={} existingFire={}: both portals lit at local tick {}",
                difficulty, switchDifficulty, pause, fireFixture, first);
    }

    private static boolean lit(ServerWorld world, BlockPos origin) {
        return world.getBlockState(origin.up()).isOf(Blocks.NETHER_PORTAL);
    }

    private static void build(ServerWorld world, BlockPos origin) {
        for (BlockPos pos : BlockPos.iterate(origin.add(-5, -1, -5), origin.add(6, 8, 6))) {
            world.setBlockState(pos, pos.getY() == origin.getY() - 1
                    ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
        }
        for (BlockPos pos : BlockPos.iterate(origin.add(-1, 0, 0), origin.add(1, 0, 2))) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState());
        }
        for (int x = -1; x <= 2; x++) {
            for (int y = 0; y <= 4; y++) {
                if (x == -1 || x == 2 || y == 0 || y == 4) {
                    world.setBlockState(origin.add(x, y, 0), Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }
    }

    private static void activate(ServerWorld world, BlockPos origin) {
        world.setBlockState(origin.add(0, 0, 1), Blocks.LAVA.getDefaultState());
        world.setBlockState(origin.add(0, 1, 1), Blocks.OAK_PLANKS.getDefaultState());
        world.setBlockState(origin.add(1, 1, 1), Blocks.OAK_PLANKS.getDefaultState());
    }

    private static void startFire(ServerWorld world, BlockPos origin) {
        // Separate fixture exercises scheduled spread; no remaining lava can light the frame directly.
        world.setBlockState(origin.add(0, 0, 1), Blocks.NETHERRACK.getDefaultState());
        world.setBlockState(origin.add(0, 1, -1), Blocks.OAK_PLANKS.getDefaultState());
        world.setBlockState(origin.add(0, 1, 1), Blocks.FIRE.getDefaultState());
    }

    private static void check(boolean passed, String message) {
        if (!passed) {
            throw new IllegalStateException(message);
        }
    }
}
