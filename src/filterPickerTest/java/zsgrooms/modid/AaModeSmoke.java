package zsgrooms.modid;

import com.redlimerl.speedrunigt.timer.InGameTimer;
import com.redlimerl.speedrunigt.timer.category.RunCategories;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.advancement.Advancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

/** Real mixin/timer/goal test in an isolated creative world; excluded from the release. */
public final class AaModeSmoke {
    private static final String LAST = "minecraft:nether/all_effects";
    private volatile Throwable failure;
    private volatile boolean awarded;
    private int stage, ticks;
    private boolean finished;
    private final long started = System.nanoTime();

    public void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            if (failure != null) throw new IllegalStateException("Server assertion failed", failure);
            require(System.nanoTime() - started < 180_000_000_000L, "Timed out");
            if (client.getOverlay() != null || ++ticks < 20) return;
            ticks = 0;
            InGame game = ZsgRooms.getGame("aa-smoke");
            switch (stage) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.maxFps = 60;
                    client.options.viewDistance = 2;
                    String seed = "12345|structure:" + AaThunderless.FILTER;
                    Room room = new Room("aa-smoke", seed, new Player(client.getSession().getUsername(), true, true), 2);
                    game = new InGame(seed, room.roomName, InGame.SeedType.FIXED, false);
                    game.targetStructure = AaThunderless.FILTER;
                    game.setRngStandardized(false);
                    game.startGame();
                    game.releaseSynchronizedStart();
                    ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                    Properties properties = new Properties();
                    properties.setProperty("level-seed", "12345");
                    properties.setProperty("level-type", "flat");
                    properties.setProperty("generate-structures", "false");
                    stage++;
                    client.method_29607("aa-smoke-" + UUID.randomUUID(),
                            new LevelInfo("AA smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), DataPackSettings.SAFE_MODE),
                            RegistryTracker.create(), GeneratorOptions.fromProperties(properties));
                    break;
                case 1:
                    if (client.player == null || client.world == null) return;
                    client.openScreen(null);
                    EndExitTimeCapture.arm(game, client);
                    require(InGameTimer.getInstance().getCategory() == RunCategories.ALL_ADVANCEMENTS, "AA timer category not selected");
                    InGameTimer.getInstance().updateFirstInput();
                    InGameTimer.getInstance().setPause(false, "aa-smoke");
                    ZsgRoomsClient.onEndExitPortalEntered();
                    require(game.getIsInGame(), "End exit completed AA");
                    stage++;
                    break;
                case 2:
                    stage++;
                    client.getServer().execute(() -> {
                        try {
                            ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                            EndExitTimeCapture.capture(player);
                            require(EndExitTimeCapture.consume(client, ZsgRooms.getGame("aa-smoke").getRaceId()) == -1L,
                                    "End exit captured an AA finish time");
                            for (Advancement advancement : player.server.getAdvancementLoader().getAdvancements()) {
                                String id = advancement.getId().toString();
                                if (AaThunderless.isRequired(id) && !LAST.equals(id)) grant(player, advancement);
                            }
                            require(!InGameTimer.getInstance().isCompleted(), "Timer finished with a required advancement missing");
                            awarded = true;
                        } catch (Throwable error) { failure = error; }
                    });
                    break;
                case 3:
                    if (!awarded) return;
                    require(game.getIsInGame(), "Partial AA completed the race");
                    awarded = false;
                    stage++;
                    client.getServer().execute(() -> {
                        try {
                            ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                            grant(player, player.server.getAdvancementLoader().get(new Identifier(LAST)));
                            require(!player.getAdvancementTracker().getProgress(player.server.getAdvancementLoader()
                                    .get(new Identifier(AaThunderless.EXCLUDED))).isDone(), "Lightning was unexpectedly granted");
                            require(InGameTimer.getInstance().isCompleted(), "Custom AA goal did not stop SpeedrunIGT");
                            awarded = true;
                        } catch (Throwable error) { failure = error; }
                    });
                    break;
                case 4:
                    if (!awarded) return;
                    require(!game.getIsInGame(), "AA completion did not submit the race result");
                    game.targetStructure = "rooms-temple-v5";
                    game.startGame();
                    SpeedRunIgtBridge.syncCategory(game);
                    require(InGameTimer.getInstance().getCategory() == RunCategories.ANY, "Ordinary race did not restore Any%");
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: AA timer, End-exit exclusion, full advancement goal without lightning, submitted finish, Any% restoration");
                    finished = true;
                    client.scheduleStop();
                    break;
                default: throw new IllegalStateException("Unexpected stage");
            }
        } catch (Throwable error) {
            finished = true;
            ZsgRooms.LOGGER.error("[AaModeSmoke] FAILED", error);
            client.scheduleStop();
        }
    }

    private static void grant(ServerPlayerEntity player, Advancement advancement) {
        for (String criterion : new ArrayList<>(advancement.getCriteria().keySet()))
            player.getAdvancementTracker().grantCriterion(advancement, criterion);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
