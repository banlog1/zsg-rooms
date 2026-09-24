package zsgrooms.modid.replay;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Used only by the explicitly enabled development recording smoke test. */
final class ReplayChestSmoke {
    private int stage;
    private long next;
    private BlockPos single, pair;
    private volatile boolean prepared;
    private volatile boolean templePrepared;
    private volatile Exception templeError;

    boolean tick(MinecraftClient client) {
        if (System.nanoTime() < next) return false;
        if (stage == 0) {
            single = client.player.getBlockPos().add(1, 0, 0);
            pair = client.player.getBlockPos().add(0, 0, 2);
            client.getServer().execute(() -> {
                net.minecraft.server.world.ServerWorld world = client.getServer().getOverworld();
                world.setBlockState(single.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(single, Blocks.TRAPPED_CHEST.getDefaultState());
                world.setBlockState(pair.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(pair.east().up(), Blocks.AIR.getDefaultState());
                world.setBlockState(pair, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                        .with(ChestBlock.CHEST_TYPE, ChestType.LEFT));
                world.setBlockState(pair.east(), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                        .with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
                ((ChestBlockEntity) world.getBlockEntity(single)).setStack(0, new ItemStack(Items.IRON_INGOT, 7));
                ((ChestBlockEntity) world.getBlockEntity(pair)).setStack(0, new ItemStack(Items.DIAMOND, 3));
                ((ChestBlockEntity) world.getBlockEntity(pair.east())).setStack(0, new ItemStack(Items.GOLD_INGOT, 9));
                prepared = true;
            });
        } else if (stage == 1 || stage == 5 || stage == 7) {
            if (!prepared || !(client.world.getBlockState(single).getBlock() instanceof ChestBlock)) return false;
            BlockPos target = stage == 5 ? pair : single;
            client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND,
                    new BlockHitResult(new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5), Direction.UP, target, false));
        } else if (stage == 2 || stage == 6 || stage == 8) {
            if (!(client.currentScreen instanceof GenericContainerScreen)) throw new IllegalStateException("Chest fixture did not open");
            if (stage == 2) {
                client.getServer().execute(() -> ((ChestBlockEntity) client.getServer().getOverworld().getBlockEntity(single))
                        .setStack(0, new ItemStack(Items.IRON_INGOT, 2)));
            } else client.player.closeHandledScreen();
        } else if (stage == 3) {
            if (client.player.currentScreenHandler.getSlot(0).getStack().getCount() != 2) {
                throw new IllegalStateException("Chest fixture slot update missing");
            }
        } else if (stage == 4) client.player.closeHandledScreen();
        else if (stage == 9 && Boolean.getBoolean("zsgrooms.replayTempleSmoke")) {
            client.getServer().execute(() -> {
                try { ReplayTempleCalibration.prepare(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()),
                        client.runDirectory.toPath().resolve("temple-loot-vectors.json")); }
                catch (Exception error) { templeError = error; }
                finally { templePrepared = true; }
            });
        } else if (stage == 10 && Boolean.getBoolean("zsgrooms.replayTempleSmoke")) {
            if (!templePrepared) return false;
            if (templeError != null) throw new IllegalStateException("Temple fixture failed", templeError);
            next = System.nanoTime() + 3000000000L; stage++; return false;
        }
        else return true;
        stage++;
        next = System.nanoTime() + 500000000L;
        return false;
    }
}
