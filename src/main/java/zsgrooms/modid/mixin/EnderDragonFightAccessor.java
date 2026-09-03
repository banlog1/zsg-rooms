package zsgrooms.modid.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.ServerBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
    @Accessor("endCrystalsAlive")
    void zsgRooms$setEndCrystalsAlive(int endCrystalsAlive);

    @Accessor("dragonKilled")
    void zsgRooms$setDragonKilled(boolean dragonKilled);

    @Accessor("dragonUuid")
    void zsgRooms$setDragonUuid(UUID dragonUuid);

    @Accessor("dragonSeenTimer")
    void zsgRooms$setDragonSeenTimer(int dragonSeenTimer);

    @Accessor("doLegacyCheck")
    void zsgRooms$setDoLegacyCheck(boolean doLegacyCheck);

    @Accessor("playerUpdateTimer")
    void zsgRooms$setPlayerUpdateTimer(int playerUpdateTimer);

    @Accessor("bossBar")
    ServerBossBar zsgRooms$getBossBar();
}
