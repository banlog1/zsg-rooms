package zsgrooms.modid;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import zsgrooms.modid.net.RoomSnapshot;

import java.lang.reflect.Field;

/** Real Atum creation/reset checks in the isolated test instance. */
public final class RoomWorldCreationSmoke {
    private static final String ROOM = "survival-creation-smoke";
    private final long started = System.nanoTime();
    private int phase;
    private boolean launched;
    private boolean checking;
    private boolean finished;
    private volatile boolean checked;
    private volatile Throwable failure;
    private MinecraftServer previousServer;
    private Class<?> atum;
    private Object config;
    private Field modeField;
    private Object originalMode;
    private boolean originalCheats;

    public void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished || client.getOverlay() != null) return;
        try {
            if (System.nanoTime() - started > 360_000_000_000L) throw new IllegalStateException("Timed out");
            if (failure != null) throw new IllegalStateException("Server check failed", failure);
            if (!launched) {
                launched = true;
                client.options.pauseOnLostFocus = false;
                client.options.maxFps = 60;
                client.options.viewDistance = 3;
                atum = Class.forName("me.voidxwalker.autoreset.Atum");
                config = atum.getField("config").get(null);
                modeField = config.getClass().getField("gameMode");
                originalMode = modeField.get(config);
                originalCheats = config.getClass().getField("cheatsEnabled").getBoolean(config);
                for (Object value : modeField.getType().getEnumConstants()) {
                    if (((Enum<?>) value).name().equals("CREATIVE")) modeField.set(config, value);
                }
                config.getClass().getField("cheatsEnabled").setBoolean(config, true);
                Room room = new Room(ROOM, "12345|structure:manual",
                        new Player(client.getSession().getUsername(), true, true), 2);
                InGame game = new InGame(room.seed, ROOM, InGame.SeedType.FIXED, false);
                ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                require(ZsgSeedBridge.launchSeedWithAtum(room.seed), "Room launch failed");
            } else if (checked) {
                boolean cheats = phase == 1 || phase == 3;
                require(client.player.hasPermissionLevel(2) == cheats, "Client permission does not match server");
                for (String command : new String[]{"gamemode", "give", "tp", "seed"}) {
                    require((client.getNetworkHandler().getCommandDispatcher().getRoot().getChild(command) != null) == cheats,
                            "Client command tree mismatch for /" + command);
                }
                require(client.getNetworkHandler().getCommandDispatcher().getRoot().getChild("help") != null,
                        "Client lost /help");
                checked = false;
                checking = false;
                previousServer = client.getServer();
                phase++;
                if (phase == 1) {
                    config.getClass().getField("cheatsEnabled").setBoolean(config, false);
                    ZsgRooms.getGame(ROOM).setCheatsAllowed(true);
                    require(ZsgSeedBridge.launchSeedWithAtum("24680|structure:manual"), "Room reset failed");
                } else if (phase == 2) {
                    config.getClass().getField("cheatsEnabled").setBoolean(config, true);
                    ZsgRooms.getGame(ROOM).setCheatsAllowed(false);
                    require(ZsgSeedBridge.launchSeedWithAtum("24680|structure:manual"), "Same-seed reset failed");
                } else {
                    atum.getMethod("stopRunning").invoke(null);
                    client.world.disconnect();
                    client.disconnect();
                    client.openScreen(new TitleScreen());
                    if (phase == 3) {
                        // Keep the lobby active: a normal world must not inherit its policy.
                        client.method_29607("ordinary-command-smoke-" + System.nanoTime(),
                                new net.minecraft.world.level.LevelInfo("Ordinary", GameMode.CREATIVE, false,
                                        net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(),
                                        net.minecraft.resource.DataPackSettings.SAFE_MODE),
                                net.minecraft.util.registry.RegistryTracker.create(),
                                net.minecraft.world.gen.GeneratorOptions.getDefaultOptions());
                    } else {
                        ZsgRooms.leaveRoomLocally(ROOM);
                        restoreTemplate();
                        ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: real Atum Creative template starts Rooms in Survival, "
                                + "room commands override template and LAN flags, resets rebind, ordinary lobby world stays vanilla");
                        finished = true;
                        client.scheduleStop();
                    }
                }
            } else if (!checking && client.player != null && client.world != null && client.currentScreen == null
                    && client.getServer() != null && client.getServer() != previousServer) {
                checking = true;
                MinecraftServer server = client.getServer();
                server.execute(() -> {
                    try {
                        GameMode expected = phase == 3 ? GameMode.CREATIVE : GameMode.SURVIVAL;
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(client.getSession().getUsername());
                        require(player != null, "Player missing");
                        require(server.getSaveProperties().getGameMode() == expected, "Saved world has wrong game mode");
                        require(player.interactionManager.getGameMode() == expected, "Player has wrong game mode");
                        require(player.abilities.creativeMode == (expected == GameMode.CREATIVE), "Wrong creative abilities");
                        require(((Enum<?>) modeField.get(config)).name().equals("CREATIVE"), "Atum template was changed");
                        boolean cheats = phase == 1 || phase == 3;
                        require(config.getClass().getField("cheatsEnabled").getBoolean(config) == (phase != 1),
                                "Atum cheats template was changed");
                        require(server.getSaveProperties().areCommandsAllowed() == cheats, "Wrong saved command flag");
                        require(server.getPermissionLevel(player.getGameProfile()) == (cheats ? 4 : 0), "Wrong permission level");
                        require(RoomCommandPermissions.forbidsCheats(server) == !cheats, "Wrong server binding");
                        if (previousServer != null) require(!RoomCommandPermissions.forbidsCheats(previousServer), "Stopped server retained policy");
                        ServerCommandSource source = player.getCommandSource();
                        for (String command : new String[]{"gamemode", "give", "tp", "seed"}) {
                            require(server.getCommandManager().getDispatcher().getRoot().getChild(command).canUse(source) == cheats,
                                    "Wrong permission for /" + command);
                        }
                        require(server.getCommandManager().getDispatcher().getRoot().getChild("help").canUse(source), "Help was blocked");
                        if (!cheats) {
                            InGame game = ZsgRooms.getGame(ROOM);
                            game.setCheatsAllowed(true);
                            try {
                                require(RoomCommandPermissions.forbidsCheats(server), "Room change unlocked existing world");
                            } finally { game.setCheatsAllowed(false); }
                            // Exercise the same setter as Open to LAN, then an explicit operator entry.
                            server.getPlayerManager().setCheatsAllowed(true);
                            require(!server.getPlayerManager().areCheatsAllowed(), "LAN setter bypassed rule");
                            server.getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
                            try {
                                require(server.getPermissionLevel(player.getGameProfile()) == 0, "Operator entry bypassed rule");
                                require(server.getCommandManager().execute(source, "gamemode creative") == 0, "Cheat command executed");
                                require(player.interactionManager.getGameMode() == GameMode.SURVIVAL, "Cheat changed mode");
                            } finally { server.getPlayerManager().getOpList().remove(player.getGameProfile()); }
                        } else if (phase == 1) {
                            server.getPlayerManager().setCheatsAllowed(false);
                            require(server.getPlayerManager().areCheatsAllowed(), "LAN setter disabled enabled room rule");
                        } else {
                            // Failure/cancellation must not leave a pending rule that binds a later world.
                            String directory = server.getSavePath(WorldSavePath.ROOT).normalize().getFileName().toString();
                            RoomCommandPermissions.duringCreation(directory + "-other", false,
                                    () -> RoomCommandPermissions.bind(server));
                            require(!RoomCommandPermissions.forbidsCheats(server), "Unrelated directory inherited policy");
                            try {
                                RoomCommandPermissions.duringCreation(directory, false, () -> { throw new IllegalStateException("fixture"); });
                            } catch (IllegalStateException expectedFailure) { }
                            RoomCommandPermissions.bind(server);
                            require(!RoomCommandPermissions.forbidsCheats(server), "Failed launch leaked policy");
                            server.getPlayerManager().setCheatsAllowed(false);
                            require(!server.getPlayerManager().areCheatsAllowed(), "Ordinary manager was overridden");
                            require(server.getPermissionLevel(player.getGameProfile()) == 4, "Ordinary saved cheats stopped working");
                        }
                        ZsgRooms.LOGGER.info("[RoomWorldCreationSmoke] phase={} world/player={} commands={} template=CREATIVE",
                                phase, expected, cheats);
                    } catch (Throwable error) { failure = error; }
                    finally { checked = true; }
                });
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[RoomWorldCreationSmoke] FAILED", error);
            try { restoreTemplate(); } catch (Exception ignored) { }
            client.scheduleStop();
        }
    }

    private void restoreTemplate() throws Exception {
        if (modeField == null || config == null) return;
        modeField.set(config, originalMode);
        config.getClass().getField("cheatsEnabled").setBoolean(config, originalCheats);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
