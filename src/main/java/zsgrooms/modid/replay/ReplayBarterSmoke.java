package zsgrooms.modid.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.world.Difficulty;
import zsgrooms.modid.ZsgRooms;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Only invoked inside the explicitly opted-in development recording smoke test. */
final class ReplayBarterSmoke {
    private final Path file = ReplayPrototype.recordingFile();
    private final UUID id = UUID.randomUUID();
    private final List<ItemStack> expected = new ArrayList<>();
    private volatile PiglinEntity piglin;
    private volatile Exception failure;
    private volatile boolean completed;
    private int stage;
    private long next;

    boolean tick(MinecraftClient client) throws Exception {
        if (failure != null) throw failure;
        if (System.nanoTime() < next) return false;
        if (stage == 0) {
            UUID player = client.player.getUuid();
            client.getServer().execute(() -> {
                try {
                    ServerWorld world = client.getServer().getOverworld();
                    client.getServer().setDifficulty(Difficulty.NORMAL, true);
                    net.minecraft.server.network.ServerPlayerEntity owner = client.getServer().getPlayerManager().getPlayer(player);
                    PiglinEntity entity = EntityType.PIGLIN.create(world);
                    entity.setUuid(id);
                    entity.setAiDisabled(true);
                    entity.setPersistent();
                    entity.setInvulnerable(true);
                    entity.refreshPositionAndAngles(owner.getX() + 1, owner.getY(), owner.getZ(), 0, 0);
                    world.spawnEntity(entity);
                    piglin = entity;
                } catch (Exception error) { failure = error; }
            });
            stage++;
        } else if (stage == 1) {
            if (piglin == null || client.world.getEntityById(piglin.getEntityId()) == null) return false;
            client.getServer().execute(() -> {
                try {
                    ServerWorld world = (ServerWorld) piglin.world;
                    Set<Integer> before = new HashSet<>();
                    for (ItemEntity item : world.getEntities(ItemEntity.class, piglin.getBoundingBox().expand(8), e -> true)) before.add(item.getEntityId());
                    Method consume = PiglinBrain.class.getDeclaredMethod("consumeOffHandItem", PiglinEntity.class, boolean.class);
                    consume.setAccessible(true);
                    piglin.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
                    consume.invoke(null, piglin, true);
                    if (!piglin.getOffHandStack().isEmpty()) throw new IllegalStateException("Barter did not consume gold");
                    for (ItemEntity item : world.getEntities(ItemEntity.class, piglin.getBoundingBox().expand(8), e -> true)) {
                        if (!before.contains(item.getEntityId())) expected.add(item.getStack().copy());
                    }
                    if (expected.isEmpty()) throw new IllegalStateException("Barter fixture produced no drops");
                    piglin.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
                    consume.invoke(null, piglin, false);
                    piglin.setBaby(true);
                    piglin.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
                    consume.invoke(null, piglin, true);
                    completed = true;
                } catch (Exception error) { failure = error; }
            });
            stage++;
        } else if (stage == 2) {
            if (!completed) return false;
            // Let client-side observation and packet recording drain before removing the fixture.
            stage++;
            next = System.nanoTime() + 1000000000L;
            return false;
        } else {
            client.getServer().execute(() -> {
                piglin.remove();
                client.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            });
            return true;
        }
        next = System.nanoTime() + 250000000L;
        return false;
    }

    void verify() throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry("zsg-rooms/barters.json") == null) throw new IllegalStateException("Missing barter capture track");
            JsonObject track;
            try (InputStreamReader input = new InputStreamReader(zip.getInputStream(zip.getEntry("zsg-rooms/barters.json")), StandardCharsets.UTF_8)) {
                track = new JsonParser().parse(input).getAsJsonObject();
            }
            if (track.get("truncated").getAsBoolean()) throw new IllegalStateException("Barter smoke capture truncated");
            int matches = 0;
            for (com.google.gson.JsonElement element : track.getAsJsonArray("events")) {
                JsonObject event = element.getAsJsonObject();
                if (!event.get("piglin").getAsString().equals(id.toString())) continue;
                matches++;
                List<ItemStack> actual = new ArrayList<>();
                for (com.google.gson.JsonElement value : event.getAsJsonArray("items")) {
                    PacketByteBuf bytes = new PacketByteBuf(Unpooled.wrappedBuffer(Base64.getDecoder().decode(value.getAsString())));
                    try { actual.add(bytes.readItemStack()); } finally { bytes.release(); }
                }
                if (actual.size() != expected.size()) throw new IllegalStateException("Barter output size changed");
                for (ItemStack item : expected) {
                    int found = -1;
                    for (int i = 0; i < actual.size(); i++) if (ItemStack.areEqual(item, actual.get(i))) { found = i; break; }
                    if (found < 0) throw new IllegalStateException("Captured barter differs from dropped stack");
                    actual.remove(found);
                }
            }
            if (matches != 1) throw new IllegalStateException("Expected one completed barter, found " + matches);
            ZsgRooms.LOGGER.info("[ReplayBarterCaptureSmoke] PASS: real completed output matches drops; cancelled/baby consumption excluded; {}", file);
        }
    }
}
