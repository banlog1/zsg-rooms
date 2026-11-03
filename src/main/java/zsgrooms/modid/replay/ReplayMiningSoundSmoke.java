package zsgrooms.modid.replay;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

/** Development smoke only: exercise the real survival mining constructor with normal and muted audio. */
final class ReplayMiningSoundSmoke {
    private int stage, hits;
    private BlockPos pos;
    private volatile boolean prepared;
    private GameMode previousMode;
    private float previousVolume;

    boolean tick(MinecraftClient client) {
        if (stage == 0) {
            previousMode = client.interactionManager.getCurrentGameMode();
            previousVolume = client.options.getSoundVolume(SoundCategory.MASTER);
            pos = client.player.getBlockPos().east();
            java.util.UUID id = client.player.getUuid();
            client.getServer().execute(() -> {
                net.minecraft.server.network.ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(id);
                player.getServerWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
                player.setGameMode(GameMode.SURVIVAL);
                prepared = true;
            });
            stage++;
        } else if (stage == 1) {
            if (!prepared || client.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL
                    || !client.world.getBlockState(pos).isOf(Blocks.STONE)) return false;
            if (!client.interactionManager.attackBlock(pos, Direction.UP)) throw new IllegalStateException("Mining fixture not attacked");
            stage++;
        } else if (stage == 2) {
            if (hits == 4) client.options.setSoundVolume(SoundCategory.MASTER, 0);
            if (!client.interactionManager.updateBlockBreakingProgress(pos, Direction.UP))
                throw new IllegalStateException("Mining fixture did not progress");
            if (++hits >= 8) {
                client.interactionManager.cancelBlockBreaking();
                client.options.setSoundVolume(SoundCategory.MASTER, previousVolume);
                java.util.UUID id = client.player.getUuid();
                client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayer(id).setGameMode(previousMode));
                stage++;
            }
        } else if (client.interactionManager.getCurrentGameMode() == previousMode) return true;
        return false;
    }
}
