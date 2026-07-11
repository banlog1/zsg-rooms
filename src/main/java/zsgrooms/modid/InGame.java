package zsgrooms.modid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InGame {
    public enum SeedType {
        RANDOM,
        FIXED
    }

    public String seed;
    public String roomName;
    public SeedType seedType;
    public boolean isInGame;
    public boolean seedChangeRequested;
    public String targetStructure;
    public int requiredIronCount;
    public int finishGoal;

    private boolean loadingScreenVisible;
    private final Map<String, Integer> playerProgress;
    private final Map<String, String> playerProgressLabels;
    private final Map<String, Long> playerTimers;
    private final List<String> sharedChatMessages;

    public InGame(String seed, String roomName, SeedType seedType, boolean isInGame) {
        this.seed = seed;
        this.roomName = roomName;
        this.seedType = seedType;
        this.isInGame = isInGame;
        this.seedChangeRequested = false;
        this.targetStructure = "generic";
        this.requiredIronCount = 4;
        this.finishGoal = 1;
        this.loadingScreenVisible = false;
        this.playerProgress = new LinkedHashMap<String, Integer>();
        this.playerProgressLabels = new LinkedHashMap<String, String>();
        this.playerTimers = new LinkedHashMap<String, Long>();
        this.sharedChatMessages = new ArrayList<String>();
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    public void setSeedType(SeedType seedType) {
        this.seedType = seedType;
    }

    public void setInGame(boolean isInGame) {
        this.isInGame = isInGame;
    }

    public void setSeedChangeRequested(boolean seedChangeRequested) {
        this.seedChangeRequested = seedChangeRequested;
    }

    public String getSeed() {
        return seed;
    }

    public boolean getSeedChangeRequested() {
        return seedChangeRequested;
    }

    public SeedType getSeedType() {
        return seedType;
    }

    public boolean getIsInGame() {
        return isInGame;
    }

    public boolean isLoadingScreenVisible() {
        return loadingScreenVisible;
    }

    public void startGame() {
        this.isInGame = true;
        this.loadingScreenVisible = false;
        if (this.seed == null || this.seed.trim().isEmpty()) {
            this.seed = "zsg-room-" + System.currentTimeMillis();
        }
        this.playerTimers.put("room", System.currentTimeMillis());
    }

    public void endGame() {
        this.isInGame = false;
        this.loadingScreenVisible = false;
        this.sharedChatMessages.add("Game ended for " + roomName);
    }

    public void StartLoadingScreen() {
        this.loadingScreenVisible = true;
    }

    public void EndLoadingScreen() {
        this.loadingScreenVisible = false;
    }

    public void setPlayerProgress(String playerName, int progress) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        this.playerProgress.put(playerName.trim(), progress);
    }

    public void setPlayerProgress(String playerName, int progress, String label) {
        setPlayerProgress(playerName, progress);
        if (playerName != null && label != null && !label.trim().isEmpty()) {
            this.playerProgressLabels.put(playerName.trim(), label.trim());
        }
    }

    public void displayPlayerProgress() {
        for (Map.Entry<String, Integer> entry : playerProgress.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }

    public void displayHud() {
        System.out.println("Room: " + roomName + " | Seed: " + seed + " | Live: " + isInGame);
    }

    public void display_hud() {
        displayHud();
    }

    public void SeedModification() {
        seedModification("generic", 4);
    }

    public void seedModification(String structureType, int minimumIron) {
        String baseSeed = ZsgSeedBridge.extractMinecraftSeed(seed);
        if (baseSeed == null || baseSeed.trim().isEmpty()) {
            baseSeed = "zsg-room";
        }
        String normalizedStructure = ZsgSeedBridge.normalizeSeedType(structureType);
        this.targetStructure = normalizedStructure;
        this.requiredIronCount = Math.max(1, minimumIron);
        this.seed = ZsgSeedBridge.buildSeedForStructure(baseSeed, normalizedStructure, this.requiredIronCount);
    }

    public void setFinishGoal(int finishGoal) {
        this.finishGoal = Math.max(1, finishGoal);
    }

    public int getFinishGoal() {
        return this.finishGoal;
    }

    public void replacePlayerProgress(Map<String, Integer> progress) {
        this.playerProgress.clear();
        if (progress != null) {
            this.playerProgress.putAll(progress);
        }
    }

    public void replacePlayerProgressLabels(Map<String, String> labels) {
        this.playerProgressLabels.clear();
        if (labels != null) {
            this.playerProgressLabels.putAll(labels);
        }
    }

    public List<String> ensureChestIron(List<String> chestItems, int requiredIron) {
        List<String> safeCopy = new ArrayList<String>();
        if (chestItems != null) {
            safeCopy.addAll(chestItems);
        }

        int ironTotal = 0;
        for (String item : safeCopy) {
            ironTotal += countIron(item);
        }

        int index = 0;
        while (ironTotal < requiredIron) {
            if (index < safeCopy.size()) {
                if (isEmptySlot(safeCopy.get(index))) {
                    safeCopy.set(index, ironVariantForSlot(index));
                    ironTotal += 1;
                }
            } else {
                safeCopy.add(ironVariantForSlot(index));
                ironTotal += 1;
            }
            index += 1;
        }

        return safeCopy;
    }

    public void addSharedChatMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            this.sharedChatMessages.add(message.trim());
            while (this.sharedChatMessages.size() > 50) {
                this.sharedChatMessages.remove(0);
            }
        }
    }

    public List<String> getSharedChatMessages() {
        return new ArrayList<String>(sharedChatMessages);
    }

    public Map<String, Integer> getPlayerProgress() {
        return new LinkedHashMap<String, Integer>(playerProgress);
    }

    public Map<String, String> getPlayerProgressLabels() {
        return new LinkedHashMap<String, String>(playerProgressLabels);
    }

    public Map<String, Long> getPlayerTimers() {
        return new LinkedHashMap<String, Long>(playerTimers);
    }

    private boolean isEmptySlot(String item) {
        return item == null || item.trim().isEmpty() || "empty".equalsIgnoreCase(item.trim());
    }

    private String ironVariantForSlot(int slotIndex) {
        return slotIndex % 2 == 0 ? "iron_nugget" : "iron_ingot";
    }

    private int countIron(String item) {
        if (item == null) {
            return 0;
        }
        String lowered = item.toLowerCase();
        if (lowered.contains("iron")) {
            if (lowered.contains("nugget")) {
                return 1;
            }
            if (lowered.contains("ingot")) {
                return 1;
            }
        }
        return 0;
    }
}
