package zsgrooms.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZsgRooms implements ModInitializer {
	public static final String MOD_ID = "zsg-rooms";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private static final Map<String, InGame> ACTIVE_GAMES = new ConcurrentHashMap<String, InGame>();
	private static final Map<String, Room> ACTIVE_ROOMS = new ConcurrentHashMap<String, Room>();
	private static volatile String activeRoomName;

	@Override
	public void onInitialize() {
		System.out.println("ZSG Private Race loaded");
		LOGGER.info("Registering ZSG room lifecycle hooks");

		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerTickEvents.END_SERVER_TICK.register(RuinedPortalChestRepair::tick);
		ZsgRoomNetworking.registerServer();
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType) {
		createRoom(roomName, maxPlayers, finishGoal, seedType, "host");
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType, String hostName) {
		createRoom(roomName, maxPlayers, finishGoal, seedType, hostName, false);
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType, String hostName, boolean cheatsAllowed) {
		createRoom(roomName, maxPlayers, finishGoal, seedType, hostName, cheatsAllowed, false);
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType, String hostName,
			boolean cheatsAllowed, boolean rngStandardized) {
		createRoom(roomName, maxPlayers, finishGoal, seedType, hostName, cheatsAllowed, rngStandardized, false);
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType, String hostName,
			boolean cheatsAllowed, boolean rngStandardized, boolean boostedBarters) {
		createRoom(roomName, maxPlayers, finishGoal, seedType, hostName, cheatsAllowed, rngStandardized,
				boostedBarters, false);
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType, String hostName,
			boolean cheatsAllowed, boolean rngStandardized, boolean boostedBarters, boolean minimumBastionIron) {
		createRoom(roomName, maxPlayers, finishGoal, seedType, hostName, cheatsAllowed, rngStandardized,
				boostedBarters, minimumBastionIron, false);
	}

	public static void createRoom(String roomName, int maxPlayers, int finishGoal, String seedType, String hostName,
			boolean cheatsAllowed, boolean rngStandardized, boolean boostedBarters, boolean minimumBastionIron,
			boolean removeBastionZombifiedPiglins) {
		String seed = ZsgSeedBridge.fetchSeedForRoom(roomName, seedType);
		Player host = new Player(cleanPlayerName(hostName), true, true);
		Room room = new Room(roomName, seed, host, Math.max(2, maxPlayers));
		ACTIVE_ROOMS.put(roomName, room);
		activeRoomName = roomName;

		InGame game = new InGame(seed, roomName, InGame.SeedType.FIXED, false);
		game.seedModification(seedType == null || seedType.trim().isEmpty() ? "generic" : seedType.trim(), 4);
		game.setFinishGoal(finishGoal);
		game.setCheatsAllowed(cheatsAllowed);
		game.setRngStandardized(rngStandardized);
		game.setBoostedBarters(boostedBarters);
		game.setMinimumBastionIron(minimumBastionIron);
		game.setRemoveBastionZombifiedPiglins(removeBastionZombifiedPiglins);
		game.setPlayerProgress(host.getName(), 0, "Starting");
		ACTIVE_GAMES.put(roomName, game);
	}

	public static void joinRoom(String roomName, int maxPlayers, int finishGoal, String seedType) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room == null) {
			return;
		}
		activeRoomName = roomName;
		Player guest = new Player("guest", false, false);
		room.addPlayer(guest);
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game != null) {
			game.setPlayerProgress("guest", 0, "Starting");
		}
	}

	public static void trackProgress(String roomName, String playerName, int progress) {
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game != null) {
			game.setPlayerProgress(playerName, progress);
			game.addSharedChatMessage(playerName + " progressed to " + progress);
		}
	}

	public static void resetPlayerRun(String roomName, String playerName) {
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game != null) {
			String name = cleanPlayerName(playerName);
			game.setPlayerProgress(name, 0, "Restarting");
			shareChat(roomName, name + " reset their run on the current seed");
		}
	}

	public static boolean trackAdvancement(String roomName, String playerName, String value) {
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game == null || !game.getIsInGame()) {
			return false;
		}
		String[] parts = value == null ? new String[0] : value.split("\\t", 2);
		String advancementId = parts.length > 0 ? parts[0] : "";
		String title = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : advancementId;
		String name = cleanPlayerName(playerName);
		shareChat(roomName, name + " made the advancement [" + title + "]");

		int stage = advancementStage(advancementId);
		if (stage > 0) {
			Integer current = game.getPlayerProgress().get(name);
			if (current == null || stage >= current) {
				game.setPlayerProgress(name, stage, advancementProgressLabel(advancementId, title));
			}
		}
		return false;
	}

	public static void shareChat(String roomName, String message) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room != null) {
			room.addRoomMessage(message);
		}
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game != null) {
			game.addSharedChatMessage(message);
		}
	}

	public static void applyRoomAction(String action, String roomName, String playerName, String value) {
		if (action == null || roomName == null || roomName.trim().isEmpty()) {
			return;
		}
		if ("state".equals(action)) {
			applyRoomState(roomName, playerName, value);
			return;
		}
		if ("snapshot".equals(action)) {
			applyRoomSnapshot(value);
			return;
		}
		if ("leave_room".equals(action)) {
			removeRoomPlayer(roomName, playerName);
			return;
		}
		Room room = ensureRoom(roomName, playerName, value);
		activeRoomName = roomName;
		ensureRoomPlayer(room, playerName);

		if ("join_room".equals(action)) {
			return;
		} else if ("profile".equals(action)) {
			setPlayerUuid(roomName, cleanPlayerName(playerName), value);
		} else if ("chat".equals(action)) {
			shareChat(roomName, "<" + cleanPlayerName(playerName) + "> " + value);
		} else if ("filter".equals(action)) {
			changeRoomFilter(roomName, value);
		} else if ("start".equals(action)) {
			startRoomGame(roomName);
		} else if ("launch".equals(action)) {
			launchRoomWithSeed(roomName, value);
		} else if ("world_ready".equals(action)) {
			if (markPlayerWorldReady(roomName, playerName, value)
					&& areAllPlayersWorldReady(roomName)) {
				releaseSynchronizedStart(roomName, value);
			}
		} else if ("race_start".equals(action)) {
			releaseSynchronizedStart(roomName, value);
		} else if ("share_seed".equals(action)) {
			InGame game = ACTIVE_GAMES.get(roomName);
			if (game != null) {
				shareChat(roomName, "Seed: " + game.getSeed());
			}
		} else if ("seed_change".equals(action)) {
			requestSeedChange(cleanPlayerName(playerName));
		} else if ("forfeit".equals(action)) {
			forfeitActiveRoom(cleanPlayerName(playerName));
		} else if ("progress".equals(action)) {
			try {
				trackProgress(roomName, cleanPlayerName(playerName), Integer.parseInt(value));
			} catch (NumberFormatException ignored) {
			}
		} else if ("reset_run".equals(action)) {
			resetPlayerRun(roomName, cleanPlayerName(playerName));
		} else if ("advancement".equals(action)) {
			trackAdvancement(roomName, cleanPlayerName(playerName), value);
		} else if ("complete_run".equals(action)) {
			finishMatch(roomName, cleanPlayerName(playerName), completionReason(value));
		} else if ("match_result".equals(action)) {
			String[] result = value == null ? new String[0] : value.split("\\t", 2);
			finishMatch(roomName, result.length > 0 ? result[0] : "Match ended",
					result.length > 1 ? result[1] : "Match finished");
		}
	}

	public static void setPlayerUuid(String roomName, String playerName, String uuid) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room == null) {
			return;
		}
		Player player = room.getPlayer(cleanPlayerName(playerName));
		if (player != null) {
			player.setUuid(uuid);
		}
	}

	public static boolean finishMatch(String roomName, String winner, String reason) {
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game == null || !game.getIsInGame()) {
			return false;
		}
		game.endGame();
		shareChat(roomName, "Match winner: " + cleanPlayerName(winner) + " - " + reason);
		return true;
	}

	public static String completionReason(String value) {
		return value == null || value.trim().isEmpty() ? "Beat the seed" : value.trim();
	}

	public static void changeRoomFilter(String roomName, String seedType) {
		Room room = ACTIVE_ROOMS.get(roomName);
		InGame game = ACTIVE_GAMES.get(roomName);
		if (room == null || game == null) {
			return;
		}
		String seed = ZsgSeedBridge.fetchSeedForRoom(roomName, seedType);
		game.setSeed(seed);
		game.seedModification(seedType, 4);
		room.setSeed(game.getSeed());
		resetSeedChangeRequests(room);
		shareChat(roomName, "Room filter changed to " + ZsgSeedBridge.seedTypeLabel(seedType));
	}

	public static boolean requestSeedChange(String playerName) {
		boolean allPlayersReady = registerSeedChangeRequest(playerName);
		if (allPlayersReady) {
			Room room = getActiveRoom();
			if (room != null) {
				changeRoomFilter(room.roomName, ZsgSeedBridge.resolveStructure(room.getSeed()));
				startRoomGame(room.roomName);
			}
		}
		return allPlayersReady;
	}

	public static boolean registerSeedChangeRequest(String playerName) {
		Room room = getActiveRoom();
		if (room == null) {
			return false;
		}
		Player player = room.getPlayer(playerName);
		if (player == null && room.getPlayerCount() == 1) {
			player = room.host;
		}
		if (player == null || player.getIsRequestingSeedChange()) {
			return false;
		}
		player.setRequestingSeedChange(true);
		shareChat(room.roomName, playerName + " requested a seed change");

		if (allPlayersRequestedSeedChange(room)) {
			shareChat(room.roomName, "All players agreed. The seed will be changed.");
			return true;
		}
		return false;
	}

	public static String forfeitActiveRoom(String playerName) {
		Room room = getActiveRoom();
		if (room == null) {
			return "Match ended";
		}
		String winner = room.findFirstOtherPlayerName(playerName);
		if (winner == null) {
			winner = "No winner";
		}
		InGame game = ACTIVE_GAMES.get(room.roomName);
		if (game != null) {
			game.endGame();
		}
		shareChat(room.roomName, playerName + " forfeited. Winner: " + winner);
		return winner;
	}

	public static boolean startRoomGame(String roomName) {
		Room room = ACTIVE_ROOMS.get(roomName);
		InGame game = ACTIVE_GAMES.get(roomName);
		if (room == null || game == null) {
			LOGGER.warn("[ZSG-Rooms] Cannot start missing room: " + roomName);
			return false;
		}

		String seed = game.getSeed();
		if (seed == null || seed.trim().isEmpty()) {
			seed = ZsgSeedBridge.fetchSeedForRoom(roomName, game.targetStructure);
			game.setSeed(seed);
			room.setSeed(seed);
		}

		boolean launched;
		if (ZsgSeedBridge.isFsgFilterSeedType(game.targetStructure)) {
			launched = ZsgSeedBridge.launchFsgFilterWithAtum(game.targetStructure);
		} else {
			launched = ZsgSeedBridge.launchSeedWithAtum(seed);
		}
		if (launched) {
			activeRoomName = roomName;
			game.startGame();
			game.releaseSynchronizedStart();
			room.setSeed(game.getSeed());
			resetSeedChangeRequests(room);
			game.addSharedChatMessage("Race started for " + roomName);
			room.addRoomMessage("Race started for " + roomName);
			LOGGER.info("[ZSG-Rooms] Race started in room: " + roomName + " with seed: " + ZsgSeedBridge.extractMinecraftSeed(game.getSeed()));
		} else {
			game.addSharedChatMessage("Could not launch Atum for " + roomName);
			LOGGER.warn("[ZSG-Rooms] Failed to launch race in room: " + roomName);
		}
		return launched;
	}

	public static void prepareRoomSeed(String roomName, String seed, String filter) {
		Room room = ACTIVE_ROOMS.get(roomName);
		InGame game = ACTIVE_GAMES.get(roomName);
		if (room == null || game == null || seed == null || seed.trim().isEmpty()) {
			return;
		}
		room.setSeed(seed);
		game.setSeed(seed);
		game.targetStructure = ZsgSeedBridge.normalizeSeedSpecification(filter);
		resetRaceProgress(room, game);
		resetSeedChangeRequests(room);
	}

	public static boolean launchRoomWithSeed(String roomName, String seed) {
		Room room = ACTIVE_ROOMS.get(roomName);
		InGame game = ACTIVE_GAMES.get(roomName);
		if (room == null || game == null || seed == null || seed.trim().isEmpty()) {
			return false;
		}

		prepareRoomSeed(roomName, seed, ZsgSeedBridge.seedSpecificationFromSeed(seed));
		boolean launched = ZsgSeedBridge.launchSeedWithAtum(seed);
		if (launched) {
			game.startGame();
			room.addRoomMessage("Loading synchronized seed for " + roomName);
			game.addSharedChatMessage("Loading synchronized seed for " + roomName);
			LOGGER.info("[ZSG-Rooms] Exact synchronized seed launched for room: " + roomName + " seed: " + ZsgSeedBridge.extractMinecraftSeed(seed));
		} else {
			shareChat(roomName, "Could not launch the synchronized seed");
		}
		return launched;
	}

	public static boolean markPlayerWorldReady(String roomName, String playerName, String seed) {
		Room room = ACTIVE_ROOMS.get(roomName);
		InGame game = ACTIVE_GAMES.get(roomName);
		String name = cleanPlayerName(playerName);
		if (room == null || game == null || !game.getIsInGame() || room.getPlayer(name) == null
				|| seed == null || !seed.equals(game.getSeed())) {
			return false;
		}
		boolean added = game.markPlayerReady(name);
		if (added) {
			shareChat(roomName, name + " finished loading (" + game.getReadyPlayerCount()
					+ "/" + room.getPlayerCount() + ")");
		}
		return added;
	}

	public static boolean areAllPlayersWorldReady(String roomName) {
		Room room = ACTIVE_ROOMS.get(roomName);
		InGame game = ACTIVE_GAMES.get(roomName);
		return room != null && game != null && game.areAllPlayersReady(room.getPlayerNames());
	}

	public static boolean releaseSynchronizedStart(String roomName, String seed) {
		InGame game = ACTIVE_GAMES.get(roomName);
		if (game == null || seed == null || !seed.equals(game.getSeed()) || !game.releaseSynchronizedStart()) {
			return false;
		}
		shareChat(roomName, "All players loaded. The race has started!");
		return true;
	}

	public static String createRoomSnapshot(String roomName) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room == null) {
			return "";
		}
		return RoomSnapshot.capture(room, ACTIVE_GAMES.get(roomName)).toJson();
	}

	public static boolean applyRoomSnapshot(String json) {
		RoomSnapshot snapshot = RoomSnapshot.fromJson(json);
		if (snapshot == null) {
			return false;
		}

		List<Player> players = new ArrayList<Player>();
		Player host = null;
		for (RoomSnapshot.PlayerState playerState : snapshot.players) {
			Player player = playerState.toPlayer();
			players.add(player);
			if (player.getIsHost() || player.getName().equals(snapshot.hostName)) {
				host = player;
			}
		}
		if (host == null) {
			host = new Player(cleanPlayerName(snapshot.hostName), true, true);
			players.add(0, host);
		}

		int maxPlayers = Math.max(2, Math.max(snapshot.maxPlayers, players.size()));
		Room room = new Room(snapshot.roomName, snapshot.seed, host, maxPlayers);
		room.replacePlayers(players, snapshot.hostName);
		room.replaceRoomMessages(snapshot.messages);
		ACTIVE_ROOMS.put(snapshot.roomName, room);

		InGame game = new InGame(snapshot.seed, snapshot.roomName, InGame.SeedType.FIXED, snapshot.inGame);
		game.targetStructure = ZsgSeedBridge.normalizeSeedSpecification(snapshot.filter);
		game.setFinishGoal(snapshot.finishGoal);
		game.setCheatsAllowed(snapshot.cheatsAllowed);
		game.setRngStandardized(snapshot.rngStandardized);
		game.setBoostedBarters(snapshot.boostedBarters);
		game.setMinimumBastionIron(snapshot.minimumBastionIron);
		game.setRemoveBastionZombifiedPiglins(snapshot.removeBastionZombifiedPiglins);
		game.restoreSynchronizedStart(snapshot.readyPlayers, snapshot.synchronizedStartReleased);
		game.replacePlayerProgress(snapshot.progress);
		game.replacePlayerProgressLabels(snapshot.progressLabels);
		ACTIVE_GAMES.put(snapshot.roomName, game);
		activeRoomName = snapshot.roomName;
		return true;
	}

	public static void removeRoomPlayer(String roomName, String playerName) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room == null) {
			return;
		}
		Player player = room.getPlayer(cleanPlayerName(playerName));
		if (player != null && player != room.host) {
			room.removePlayer(player);
			InGame game = ACTIVE_GAMES.get(roomName);
			if (game != null) {
				game.removeReadyPlayer(player.getName());
			}
		}
	}

	public static Room getRoom(String roomName) {
		return ACTIVE_ROOMS.get(roomName);
	}

	public static InGame getGame(String roomName) {
		return ACTIVE_GAMES.get(roomName);
	}

	public static String getActiveRoomName() {
		return activeRoomName;
	}

	public static Room getActiveRoom() {
		return activeRoomName == null ? null : ACTIVE_ROOMS.get(activeRoomName);
	}

	public static boolean hasManagedRoom() {
		return getActiveRoom() != null;
	}

	public static void leaveRoomLocally(String roomName) {
		if (roomName == null || !roomName.equals(activeRoomName)) {
			return;
		}
		ACTIVE_GAMES.remove(roomName);
		ACTIVE_ROOMS.remove(roomName);
		activeRoomName = null;
		ZsgSeedBridge.releaseRoomControl();
	}

	private static boolean allPlayersRequestedSeedChange(Room room) {
		if (room == null || room.getPlayerCount() == 0) {
			return false;
		}
		for (Player player : room.players) {
			if (player != null && !player.getIsRequestingSeedChange()) {
				return false;
			}
		}
		return true;
	}

	private static void resetSeedChangeRequests(Room room) {
		if (room == null) {
			return;
		}
		for (Player player : room.players) {
			if (player != null) {
				player.setRequestingSeedChange(false);
			}
		}
	}

	private static void resetRaceProgress(Room room, InGame game) {
		game.replacePlayerProgress(null);
		game.replacePlayerProgressLabels(null);
		for (Player player : room.players) {
			if (player != null) {
				game.setPlayerProgress(player.getName(), 0, "Starting");
			}
		}
	}

	private static Room ensureRoom(String roomName, String playerName, String seedType) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room != null) {
			return room;
		}

		String hostName = cleanPlayerName(playerName);
		String seed = ZsgSeedBridge.fetchSeedForRoom(roomName, seedType);
		Player host = new Player(hostName, true, true);
		room = new Room(roomName, seed, host, 8);
		ACTIVE_ROOMS.put(roomName, room);

		InGame game = new InGame(seed, roomName, InGame.SeedType.FIXED, false);
		game.seedModification(seedType == null || seedType.trim().isEmpty() ? "room" : seedType, 4);
		game.setFinishGoal(1);
		ACTIVE_GAMES.put(roomName, game);
		return room;
	}

	private static void applyRoomState(String roomName, String playerName, String seed) {
		Room room = ACTIVE_ROOMS.get(roomName);
		if (room == null) {
			Player host = new Player(cleanPlayerName(playerName), true, true);
			room = new Room(roomName, seed, host, 8);
			ACTIVE_ROOMS.put(roomName, room);
		} else {
			room.setSeed(seed);
		}

		InGame game = ACTIVE_GAMES.get(roomName);
		if (game == null) {
			game = new InGame(seed, roomName, InGame.SeedType.FIXED, false);
			ACTIVE_GAMES.put(roomName, game);
		}
		game.setSeed(seed);
		game.targetStructure = ZsgSeedBridge.seedSpecificationFromSeed(seed);
		activeRoomName = roomName;
	}

	private static void ensureRoomPlayer(Room room, String playerName) {
		String name = cleanPlayerName(playerName);
		if (room == null || room.getPlayer(name) != null) {
			return;
		}
		try {
			room.addPlayer(new Player(name, true, false));
		} catch (IllegalStateException ignored) {
		}
	}

	private static String cleanPlayerName(String playerName) {
		if (playerName == null || playerName.trim().isEmpty()) {
			return "player";
		}
		return playerName.trim();
	}

	private static int advancementStage(String id) {
		if ("minecraft:story/root".equals(id)) return 1;
		if ("minecraft:story/mine_stone".equals(id)) return 2;
		if ("minecraft:story/smelt_iron".equals(id) || "minecraft:story/iron_tools".equals(id)) return 3;
		if ("minecraft:nether/root".equals(id)) return 4;
		if ("minecraft:nether/find_fortress".equals(id) || "minecraft:nether/find_bastion".equals(id)) return 5;
		if ("minecraft:story/follow_ender_eye".equals(id)) return 6;
		if ("minecraft:end/root".equals(id)) return 7;
		if ("minecraft:end/kill_dragon".equals(id)) return 8;
		return 0;
	}

	private static String advancementProgressLabel(String id, String fallback) {
		if ("minecraft:nether/root".equals(id)) return "Entered Nether";
		if ("minecraft:nether/find_fortress".equals(id)) return "Found Fortress";
		if ("minecraft:nether/find_bastion".equals(id)) return "Entered Bastion";
		if ("minecraft:story/follow_ender_eye".equals(id)) return "Found Stronghold";
		if ("minecraft:end/root".equals(id)) return "Entered End";
		if ("minecraft:end/kill_dragon".equals(id)) return "Dragon Defeated";
		return fallback == null || fallback.trim().isEmpty() ? "In progress" : fallback;
	}

	private void onServerStarted(MinecraftServer server) {
		Room room = getActiveRoom();
		InGame game = room == null ? null : ACTIVE_GAMES.get(room.roomName);
		boolean cheatsAllowed = game != null && game.areCheatsAllowed();
		server.getPlayerManager().setCheatsAllowed(cheatsAllowed);
		boolean rngStandardized = game != null && game.isRngStandardized();
		boolean boostedBarters = game != null && game.areBartersBoosted();
		boolean minimumBastionIron = game != null && game.hasMinimumBastionIron();
		boolean removeBastionZombifiedPiglins = game != null && game.removesBastionZombifiedPiglins();
		RngStandardization.configure(rngStandardized, boostedBarters);
		BastionIronGuarantee.configure(minimumBastionIron);
		BastionZombifiedPiglinControl.configure(removeBastionZombifiedPiglins);
		LOGGER.info("Server started for ZSG room; cheats allowed: {}, RNG standardized: {}, boosted barters: {}, minimum bastion iron: {}, no bastion zombified piglins: {}",
				cheatsAllowed, rngStandardized, boostedBarters, minimumBastionIron, removeBastionZombifiedPiglins);
	}

}
