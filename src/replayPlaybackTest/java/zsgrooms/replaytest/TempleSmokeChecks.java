package zsgrooms.replaytest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class TempleSmokeChecks {
    private int step, time;
    private long next;
    private Object controls, recording;
    private BlockPos pos;
    private JsonObject fixture;

    boolean tick(ReplayHandler handler, MinecraftClient client) throws Exception {
        if (System.nanoTime() < next) return false;
        if (step == 0) {
            Field instance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
            instance.setAccessible(true);
            controls = get(instance.get(null), "controls");
            if (controls == null || get(get(controls, "milestoneIndex"), "chests") == null) return false;
            recording = get(get(controls, "recordingIndex"), "recording");
            if (recording == null) return false;
            Path vectors = Paths.get(System.getProperty("zsgrooms.replayPlaybackFile")).getParent().getParent().resolve("temple-loot-vectors.json");
            try (Reader reader = Files.newBufferedReader(vectors, StandardCharsets.UTF_8)) {
                fixture = new JsonParser().parse(reader).getAsJsonObject();
            }
            long seed = fixture.get("seed").getAsLong();
            require(Long.valueOf(seed).equals(get(recording, "predictionSeed")), "Post-match seed not released");
            Class<?> model = Class.forName("zsgrooms.replayviewer.TempleLootModel");
            Method index = model.getDeclaredMethod("chestIndex", long.class, int.class, int.class, int.class); index.setAccessible(true);
            Method lootSeed = model.getDeclaredMethod("lootSeed", long.class, int.class, int.class, int.class); lootSeed.setAccessible(true);
            checkRotations(index, lootSeed);
            Method generate = Class.forName("zsgrooms.replayviewer.TempleLootPrediction").getDeclaredMethod("generate", long.class); generate.setAccessible(true);
            if (fixture.has("allTables")) {
                Method all = Class.forName("zsgrooms.replayviewer.ChestLootPrediction").getDeclaredMethod("generate", String.class, long.class, List.class);
                all.setAccessible(true);
                for (com.google.gson.JsonElement value : fixture.getAsJsonArray("allTables")) {
                    JsonObject row = value.getAsJsonObject();
                    try {
                        compare((List<?>) all.invoke(null, row.get("table").getAsString(), row.get("lootSeed").getAsLong(), get(recording, "stewOrder")), row.getAsJsonArray("items"));
                    } catch (Exception error) {
                        throw new IllegalStateException(row.get("table") + " seed=" + row.get("lootSeed"), error);
                    }
                }
                require(!((List<?>) get(recording, "chestLoot")).isEmpty(), "Chunk loot metadata missing from recording");
            }
            for (com.google.gson.JsonElement value : fixture.getAsJsonArray("vectors")) {
                JsonObject row = value.getAsJsonObject();
                compare((List<?>) generate.invoke(null, row.get("lootSeed").getAsLong()), row.getAsJsonArray("items"));
            }
            for (com.google.gson.JsonElement value : fixture.getAsJsonArray("chests")) {
                JsonObject row = value.getAsJsonObject();
                int x = row.get("x").getAsInt(), z = row.get("z").getAsInt();
                int ordinal = (Integer) index.invoke(null, seed, x, 53, z);
                require(ordinal >= 0, "Generated temple chest not recognized");
                long actualSeed = (Long) lootSeed.invoke(null, seed, x >> 4, z >> 4, ordinal);
                require(actualSeed == row.get("lootSeed").getAsLong(), "Chest seed/rotation mismatch");
                compare((List<?>) generate.invoke(null, actualSeed), row.getAsJsonArray("items"));
            }
            JsonObject first = fixture.getAsJsonArray("chests").get(0).getAsJsonObject();
            pos = new BlockPos(first.get("x").getAsInt(), 53, first.get("z").getAsInt());
            Object interval = ((List<?>) get(recording, "intervals")).get(0);
            time = (Integer) get(interval, "end") - 500;
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            client.options.guiScale = 2; client.onResolutionChanged();
            Object camera = get(controls, "camera");
            camera.getClass().getMethod("setSelected", int.class).invoke(camera, 0);
            seek(handler, time);
        } else if (step == 1 || step == 5) {
            if (fixture.has("allTables")) checkAllChests(client, step == 1 ? time : time - 250);
            client.openScreen(null); handler.getOverlay().setMouseVisible(false);
            handler.getCameraEntity().setCameraPosition(pos.getX() + 0.5,
                    pos.getY() + 0.5 - handler.getCameraEntity().getStandingEyeHeight(), pos.getZ() + 2.5);
            handler.getCameraEntity().setCameraRotation(180, 0, 0);
            client.onWindowFocusChanged(true); client.mouse.lockCursor();
            KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(client.options.keyUse.getBoundKeyTranslationKey()));
            Method input = handler.getCameraEntity().getClass().getDeclaredMethod("handleInputEvents");
            input.setAccessible(true); input.invoke(handler.getCameraEntity());
            require(client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("ChestInspectionScreen"), "Prediction inspector did not open");
            require("Predicted vanilla loot".equals(get(client.currentScreen, "status")), "Unopened chest not labelled predicted");
            compare((List<?>) get(client.currentScreen, "items"), fixture.getAsJsonArray("chests").get(0).getAsJsonObject().getAsJsonArray("items"));
            require(handler.getReplaySender().paused(), "Prediction inspector resumed paused playback");
        } else if (step == 2 || step == 6) {
            ScreenshotUtils.saveScreenshot(client.runDirectory, step == 2 ? "viewer-temple-prediction.png" : "viewer-temple-prediction-quick.png",
                    client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> {});
            client.currentScreen.onClose();
            if (step == 6) {
                Method predict = Class.forName("zsgrooms.replayviewer.TempleLootPrediction").getDeclaredMethod("predict",
                        recording.getClass(), net.minecraft.client.world.ClientWorld.class, BlockPos.class);
                predict.setAccessible(true);
                require(predict.invoke(null, recording, client.world, pos.up()) == null, "Non-temple chest position predicted");
                String room = "prediction-guard-smoke";
                ZsgRooms.createRoom(room, 2, 1, "manual:12345", "Tester");
                try {
                    ZsgRooms.getGame(room).startGame();
                    require(predict.invoke(null, recording, client.world, pos) == null, "Active race allowed loot prediction");
                    zsgrooms.modid.replay.ReplayPrototype.returnedToRoom();
                    require(predict.invoke(null, recording, client.world, pos) != null, "Room return kept prediction blocked");
                    zsgrooms.modid.replay.ReplayPrototype.armRace("new-race", true);
                    require(predict.invoke(null, recording, client.world, pos) == null, "New race retained room-return permission");
                    ZsgRooms.getGame(room).endGame();
                    require(predict.invoke(null, recording, client.world, pos) != null, "Ended race kept prediction blocked");
                } finally { ZsgRooms.leaveRoomLocally(room); }
                ZsgRooms.LOGGER.info("[ReplayTempleSmoke] PASS: 512 temple inventories plus all vanilla chest tables, chunk metadata, unopened UI, post-match release and Quick seeking");
                return true;
            }
            Object playback = get(controls, "playback");
            Method toggle = playback.getClass().getDeclaredMethod("setQuick", boolean.class); toggle.setAccessible(true);
            toggle.invoke(playback, true);
        } else if (step == 3) {
            Object playback = get(controls, "playback");
            Method busy = playback.getClass().getDeclaredMethod("busy"); busy.setAccessible(true);
            if ((Boolean) busy.invoke(playback)) return false;
            require(handler.isQuickMode(), "Quick Mode not enabled");
            seek(handler, time - 250);
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
            client.options.guiScale = 2; client.onResolutionChanged();
        }
        step++; next = System.nanoTime() + 1200000000L; return false;
    }

    private static void compare(List<?> items, JsonArray expected) throws Exception {
        require(items != null, "Missing predicted inventory");
        require(items.size() == 27, "Wrong inventory size");
        for (int i = 0; i < 27; i++) require(((ItemStack) items.get(i)).toTag(new CompoundTag())
                .equals(StringNbtReader.parse(expected.get(i).getAsString())), "Vanilla loot mismatch at slot " + i
                        + ": actual=" + ((ItemStack) items.get(i)).toTag(new CompoundTag()) + " expected=" + expected.get(i));
    }

    private void checkAllChests(MinecraftClient client, int timestamp) throws Exception {
        Object history = get(get(controls, "milestoneIndex"), "chestLoot");
        Method predict = Class.forName("zsgrooms.replayviewer.ChestLootPrediction").getDeclaredMethod("predict",
                recording.getClass(), history.getClass(), net.minecraft.client.world.ClientWorld.class, BlockPos.class, int.class, int.class);
        predict.setAccessible(true);
        JsonArray all = fixture.getAsJsonArray("allTables");
        for (int i = 0; i < all.size(); i += 16) {
            JsonObject row = all.get(i).getAsJsonObject();
            BlockPos chest = new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
            List<?> predicted = (List<?>) predict.invoke(null, recording, history, client.world, chest, 0, timestamp);
            require(predicted != null, "Missing predicted inventory for " + row.get("table") + " at " + chest
                    + " time=" + timestamp + " state=" + client.world.getBlockState(chest));
            compare(predicted, row.getAsJsonArray("items"));
            require(predict.invoke(null, recording, history, client.world, chest, 0, 0) == null, "Future loot leaked before chunk");
        }
        if (fixture.has("doubleChest")) {
            JsonArray halves = fixture.getAsJsonArray("doubleChest");
            for (int click = 0; click < 2; click++) {
                JsonObject row = halves.get(click).getAsJsonObject();
                BlockPos chest = new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
                List<?> contents = (List<?>) predict.invoke(null, recording, history, client.world, chest, 0, timestamp);
                require(contents != null && contents.size() == 54, "Double chest prediction missing");
                compare(contents.subList(0, 27), halves.get(0).getAsJsonObject().getAsJsonArray("items"));
                compare(contents.subList(27, 54), halves.get(1).getAsJsonObject().getAsJsonArray("items"));
            }
        }
    }
    private static void checkRotations(Method index, Method lootSeed) throws Exception {
        for (int i = 0; i < 128; i++) {
            long seed = i * 0x9e3779b97f4a7c15L;
            int rx = i % 13 - 6, rz = i % 17 - 8;
            java.util.Random placement = new java.util.Random(rx * 341873128712L + rz * 132897987541L + seed + 14357617);
            int cx = rx * 32 + placement.nextInt(24), cz = rz * 32 + placement.nextInt(24);
            net.minecraft.world.gen.ChunkRandom carver = new net.minecraft.world.gen.ChunkRandom();
            carver.setCarverSeed(seed, cx, cz);
            TemplePose pose = new TemplePose(carver, cx, cz);
            net.minecraft.world.gen.ChunkRandom decorator = new net.minecraft.world.gen.ChunkRandom();
            decorator.setDecoratorSeed(decorator.setPopulationSeed(seed, cx * 16, cz * 16), 3, 4);
            int ordinal = 0;
            for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                BlockPos pos = pose.chest(direction);
                require((Integer) index.invoke(null, seed, pos.getX(), pos.getY(), pos.getZ()) == ordinal, "Vanilla rotation disagrees");
                require((Long) lootSeed.invoke(null, seed, cx, cz, ordinal++) == decorator.nextLong(), "Vanilla decorator disagrees");
            }
        }
    }
    private static final class TemplePose extends net.minecraft.structure.DesertTempleGenerator {
        TemplePose(java.util.Random random, int cx, int cz) { super(random, cx * 16, cz * 16); }
        BlockPos chest(net.minecraft.util.math.Direction direction) {
            int x = 10 + direction.getOffsetX() * 2, z = 10 + direction.getOffsetZ() * 2;
            return new BlockPos(applyXTransform(x, z), applyYTransform(-11), applyZTransform(x, z));
        }
    }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void seek(ReplayHandler handler, int time) throws Exception {
        handler.getReplaySender().setReplaySpeed(0); AudioSmokeChecks.seek(handler, time); handler.getReplaySender().setReplaySpeed(0);
    }
    private static void require(boolean test, String message) { if (!test) throw new IllegalStateException(message); }
}
