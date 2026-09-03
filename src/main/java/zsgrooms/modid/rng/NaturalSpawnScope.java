package zsgrooms.modid.rng;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

public final class NaturalSpawnScope {
    private NaturalSpawnScope() {
    }

    public static boolean applies(
            boolean standardizationEnabled,
            RegistryKey<World> worldKey,
            SpawnGroup spawnGroup
    ) {
        return standardizationEnabled
                && World.NETHER.equals(worldKey)
                && spawnGroup == SpawnGroup.MONSTER;
    }
}
