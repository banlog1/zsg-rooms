package zsgrooms.modid;

import net.minecraft.world.GameMode;
import net.minecraft.world.level.LevelInfo;

/** Applies room defaults to creation options, never to Atum's saved configuration. */
public final class RoomWorldCreation {
    private RoomWorldCreation() { }

    public static boolean isRoomCreation(boolean managedRoom, String screenClass) {
        return managedRoom && "me.voidxwalker.autoreset.AtumCreateWorldScreen".equals(screenClass);
    }

    public static LevelInfo settings(LevelInfo original, boolean managedRoom, String screenClass, boolean allowCheats) {
        if (!isRoomCreation(managedRoom, screenClass)) return original;
        // In these mappings hasStructures() is the hardcore flag, not world generation.
        return new LevelInfo(original.getLevelName(), GameMode.SURVIVAL, original.hasStructures(),
                original.getDifficulty(), allowCheats, original.getGameRules(), original.method_29558());
    }
}
