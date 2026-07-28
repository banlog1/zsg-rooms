package zsgrooms.modid;

import net.minecraft.server.integrated.IntegratedServer;

public final class PauseWorldSaveControl {
    private static volatile boolean enabled;

    private PauseWorldSaveControl() {
    }

    public static void configure(boolean shouldEnable) {
        enabled = shouldEnable;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean shouldSkipPauseWorldSave() {
        return shouldSkipPauseWorldSave(new RaceStateLookup() {
            @Override
            public RaceState read() {
                boolean managedRoom = ZsgRooms.hasManagedRoom();
                Room room = ZsgRooms.getActiveRoom();
                InGame game = room == null ? null : ZsgRooms.getGame(room.roomName);
                return new RaceState(
                        managedRoom,
                        room != null,
                        game != null,
                        game != null && game.getIsInGame());
            }
        });
    }

    public static void onPauseSaveSkipped(IntegratedServer server) {
        try {
            String roomName = ZsgRooms.getActiveRoomName();
            InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
            String seed = game == null || game.getSeed() == null ? "none" : game.getSeed();
            String serverName = server == null || server.getSaveProperties() == null
                    ? "unknown"
                    : server.getSaveProperties().getLevelName();
            SeedDebugLog.info(
                    "[ZSG-Rooms/PauseSave] Skipped pause-triggered world save "
                            + "room={} seed={} server={} thread={}",
                    roomName == null ? "none" : roomName,
                    seed,
                    serverName,
                    Thread.currentThread().getName());
        } catch (Throwable ignored) {
            // Diagnostics must not affect pausing or saving.
        }
    }

    static boolean shouldSkipPauseWorldSave(RaceStateLookup lookup) {
        if (!enabled || lookup == null) {
            return false;
        }
        try {
            RaceState state = lookup.read();
            return state != null && shouldSkipForState(
                    enabled,
                    state.managedRoom,
                    state.activeRoom,
                    state.activeGame,
                    state.raceActive);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean shouldSkipForState(
            boolean ruleEnabled,
            boolean managedRoom,
            boolean activeRoom,
            boolean activeGame,
            boolean raceActive
    ) {
        return ruleEnabled && managedRoom && activeRoom && activeGame && raceActive;
    }

    interface RaceStateLookup {
        RaceState read();
    }

    static final class RaceState {
        final boolean managedRoom;
        final boolean activeRoom;
        final boolean activeGame;
        final boolean raceActive;

        RaceState(boolean managedRoom, boolean activeRoom, boolean activeGame, boolean raceActive) {
            this.managedRoom = managedRoom;
            this.activeRoom = activeRoom;
            this.activeGame = activeGame;
            this.raceActive = raceActive;
        }
    }
}
