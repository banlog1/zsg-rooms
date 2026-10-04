package zsgrooms.modid;

import net.minecraft.resource.DataPackSettings;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.LevelInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomWorldCreationTest {
    private static final String ATUM = "me.voidxwalker.autoreset.AtumCreateWorldScreen";

    @Test void everyRoomCreationStartsInSurvivalWithoutChangingOtherSettings() {
        for (GameMode mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
            for (boolean cheats : new boolean[]{false, true}) {
                for (boolean roomCheats : new boolean[]{false, true}) {
                    LevelInfo original = options(mode, cheats);
                    LevelInfo result = RoomWorldCreation.settings(original, true, ATUM, roomCheats);
                    assertEquals(GameMode.SURVIVAL, result.getGameMode());
                    assertEquals(mode, original.getGameMode());
                    assertEquals(original.getLevelName(), result.getLevelName());
                    assertEquals(original.getDifficulty(), result.getDifficulty());
                    assertEquals(original.hasStructures(), result.hasStructures());
                    // These 1.16.1 mappings call the allowCommands getter isHardcore.
                    assertEquals(roomCheats, result.isHardcore());
                    assertEquals(cheats, original.isHardcore());
                    assertSame(original.getGameRules(), result.getGameRules());
                    assertSame(original.method_29558(), result.method_29558());
                }
            }
        }
    }

    @Test void ordinaryAtumAndManualCreationStayUntouchedEvenWithALobbyOpen() {
        LevelInfo original = options(GameMode.CREATIVE, true);
        assertSame(original, RoomWorldCreation.settings(original, false, ATUM, false));
        assertSame(original, RoomWorldCreation.settings(original, true,
                "net.minecraft.client.gui.screen.world.CreateWorldScreen", false));
        assertSame(original, RoomWorldCreation.settings(original, false,
                "net.minecraft.client.gui.screen.world.CreateWorldScreen", false));
    }

    private static LevelInfo options(GameMode mode, boolean cheats) {
        return new LevelInfo("Room creation test", mode, false, Difficulty.HARD,
                cheats, new GameRules(), DataPackSettings.SAFE_MODE);
    }
}
