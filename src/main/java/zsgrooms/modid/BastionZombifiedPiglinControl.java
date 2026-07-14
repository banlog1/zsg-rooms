package zsgrooms.modid;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.gen.feature.StructureFeature;

public final class BastionZombifiedPiglinControl {
    private static volatile boolean enabled;

    private BastionZombifiedPiglinControl() {
    }

    public static void configure(boolean shouldRemove) {
        enabled = shouldRemove;
    }

    public static boolean shouldReject(ServerWorld world, Entity entity) {
        if (!enabled || !(entity instanceof ZombifiedPiglinEntity)) {
            return false;
        }

        BlockPos pos = entity.getBlockPos();
        boolean insideBastion = world.getStructureAccessor()
                .getStructuresWithChildren(ChunkSectionPos.from(pos), StructureFeature.BASTION_REMNANT)
                .anyMatch(start -> start.getBoundingBox().contains(pos));
        return shouldRemove(true, true, insideBastion);
    }

    static boolean shouldRemove(boolean ruleEnabled, boolean zombifiedPiglin, boolean insideBastion) {
        return ruleEnabled && zombifiedPiglin && insideBastion;
    }
}
