package zsgrooms.replaytest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Fixed full-detail seeking workload with destination snapshots, not shipped. */
final class ViewerSeekProfile {
    private static final int[] TARGETS = {90000, 95000, 90000, 180000, 30000, 90000};
    private static final Gson JSON = new GsonBuilder().registerTypeAdapter(ItemStack.class,
            (com.google.gson.JsonSerializer<ItemStack>)(stack,type,context) ->
                    new com.google.gson.JsonPrimitive(stack.toTag(new CompoundTag()).toString())).create();
    private final Path output = Paths.get(System.getProperty("zsgrooms.viewerProfileOutput"));
    private File[] files;
    private ReplayHandler handler;
    private Object controls;
    private int file, target, ticks;
    private long started;
    private boolean driving, finished, ready;
    private int settingsFrame;

    void start() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> { if (handler == null) tick(client); });
        new EventRegistrations().on(PreRenderCallback.EVENT, () -> {
            if (handler != null) tick(MinecraftClient.getInstance());
        }).register();
    }

    private void tick(MinecraftClient client) {
        if (finished || driving) return;
        driving = true;
        try {
            if (started != 0 && System.nanoTime() - started > 600_000_000_000L) throw new IllegalStateException("Seek profile timeout");
            if (files == null) {
                if (client.getOverlay() != null || ++ticks < 40) return;
                if (Boolean.getBoolean("zsgrooms.viewerSeekBatchLights") && Boolean.getBoolean("zsgrooms.viewerSeekFast"))
                    throw new IllegalStateException("Choose one lighting experiment, not both");
                if (Files.exists(output)) throw new IllegalStateException("Use a fresh output path");
                started = System.nanoTime();
                client.options.pauseOnLostFocus = false;
                client.options.maxFps = 120; client.options.enableVsync = false;
                client.getWindow().setVsync(false); client.options.viewDistance = 8; client.options.guiScale = 2;
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                files = new File(System.getProperty("zsgrooms.replayPlaybackFile")).getParentFile()
                        .listFiles((dir,name) -> name.endsWith(".mcpr"));
                if (files == null || files.length != 3) throw new IllegalStateException("Expected three private replay copies");
                Arrays.sort(files, Comparator.comparing(File::getName));
                event("environment", "java", System.getProperty("java.version"), "batch", Boolean.getBoolean("zsgrooms.viewerSeekBatchLights"));
                open(); return;
            }
            if (!ready) {
                Object viewer = get(null, Class.forName("zsgrooms.replayviewer.ReplayViewer"), "instance");
                if (get(viewer,"current") != handler || (controls = get(viewer,"controls")) == null) return;
                for (String name : Arrays.asList("milestoneIndex", "recordingIndex"))
                    if (((Thread)get(get(controls,name),"worker")).isAlive()) return;
                if (client.world == null || handler.getCameraEntity() == null) return;
                if (!((String)get(get(controls,"milestoneIndex"),"chestStatus")).isEmpty()) throw new IllegalStateException("Chest indexing failed");
                handler.getReplaySender().setReplaySpeed(0);
                Object playback = get(controls,"playback");
                Method fast = playback.getClass().getDeclaredMethod("fastSeeking"); fast.setAccessible(true);
                if ((Boolean)fast.invoke(playback)) throw new IllegalStateException("Fast seeking must default off");
                Method setFast = playback.getClass().getDeclaredMethod("setFastSeeking",boolean.class); setFast.setAccessible(true);
                setFast.invoke(playback,Boolean.getBoolean("zsgrooms.viewerSeekFast"));
                event("setting", "fastSeeking",fast.invoke(playback));
                Object camera = get(controls,"camera");
                camera.getClass().getMethod("setSelected",int.class).invoke(camera,4);
                ready = true;
                return;
            }
            if (file == 0 && settingsFrame < 4 && settings(client)) return;
            if (target < TARGETS.length) {
                int destination = TARGETS[target];
                SeekLightingProbe.reset();
                event("phase-start", "target", destination);
                long start = System.nanoTime();
                handler.doJump(destination,true);
                event("phase-end", "target",destination,"actual",handler.getReplaySender().currentTimeStamp(),
                        "wallMs",(System.nanoTime()-start)/1e6,"lightMs",SeekLightingProbe.nanos/1e6,
                        "drains",SeekLightingProbe.drains,"deferred",SeekLightingProbe.deferred,"batches",SeekLightingProbe.batches);
                snapshot(client,destination);
                target++;
                return;
            }
            handler.endReplay(); handler = null;
            if (++file < files.length) { target = 0; open(); }
            else { finished = true; event("complete"); ZsgRooms.LOGGER.info("[ViewerSeekProfile] PASS"); client.scheduleStop(); }
        } catch (Throwable error) {
            finished = true; ZsgRooms.LOGGER.error("[ViewerSeekProfile] FAIL",error);
            try { if (handler != null) handler.endReplay(); } catch (Exception ignored) { }
            client.scheduleStop();
        } finally { driving = false; }
    }

    private void open() throws Exception {
        ready = false;
        ReplayModReplay.instance.startReplay(files[file]);
        handler = ReplayModReplay.instance.getReplayHandler();
        if (handler == null) throw new IllegalStateException("Replay refused");
    }

    private boolean settings(MinecraftClient client) throws Exception {
        if (settingsFrame == 0 || settingsFrame == 2) {
            client.options.guiScale = settingsFrame == 0 ? 2 : 3;
            client.onResolutionChanged();
            java.lang.reflect.Constructor<?> constructor = Class.forName("zsgrooms.replayviewer.AnalysisScreen").getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            client.openScreen((Screen)constructor.newInstance(get(controls,"analysis"),get(controls,"details"),get(controls,"playback")));
            CheckboxWidget checkbox = (CheckboxWidget)get(client.currentScreen,"fastSeeking");
            GLFW.glfwSetCursorPos(client.getWindow().getHandle(),1280.0 / client.currentScreen.width * (checkbox.x + 140),
                    720.0 / client.currentScreen.height * (checkbox.y + 10));
        } else {
            Screen screen = client.currentScreen;
            for (net.minecraft.client.gui.Element child : screen.children()) if (child instanceof AbstractButtonWidget) {
                AbstractButtonWidget button = (AbstractButtonWidget) child;
                if (button.x < 0 || button.y < 0 || button.x + button.getWidth() > screen.width || button.y + button.getHeight() > screen.height)
                    throw new IllegalStateException("Settings control outside screen: " + button.getMessage().getString());
            }
            CheckboxWidget checkbox = (CheckboxWidget)get(screen,"fastSeeking");
            Object playback = get(controls,"playback");
            Method fast = playback.getClass().getDeclaredMethod("fastSeeking"); fast.setAccessible(true);
            boolean initial = (Boolean)fast.invoke(playback);
            if (!checkbox.active || checkbox.isChecked() != initial) throw new IllegalStateException("Seek checkbox state mismatch");
            checkbox.onPress();
            if ((Boolean)fast.invoke(playback) == initial) throw new IllegalStateException("Seek checkbox did not toggle");
            checkbox.onPress();
            if ((Boolean)fast.invoke(playback) != initial) throw new IllegalStateException("Seek checkbox did not restore");
            ScreenshotUtils.saveScreenshot(client.runDirectory,"seek-settings-"+client.options.guiScale+".png",client.getWindow().getFramebufferWidth(),
                    client.getWindow().getFramebufferHeight(),client.getFramebuffer(),message -> {});
            event("settings-ui","width",screen.width,"height",screen.height);
            client.openScreen(null);
            client.options.guiScale = 2; client.onResolutionChanged();
        }
        settingsFrame++;
        return true;
    }

    private void snapshot(MinecraftClient client, int time) throws Exception {
        if (!(Boolean)Class.forName("zsgrooms.replayviewer.ReplayAudio").getMethod("muted").invoke(null))
            throw new IllegalStateException("Seek audio was not suppressed");
        long settleStart = System.nanoTime();
        while (client.world.getLightingProvider().hasUpdates())
            client.world.getLightingProvider().doLightUpdates(Integer.MAX_VALUE,true,true);
        event("light-settled","wallMs",(System.nanoTime()-settleStart)/1e6);
        Object manager = client.world.getChunkManager(), map = get(manager,"chunks");
        AtomicReferenceArray<?> chunks = (AtomicReferenceArray<?>)get(map,"chunks");
        Map<String,String> blocks = new TreeMap<>(), lights = new TreeMap<>(), entities = new TreeMap<>();
        byte[] states = new byte[4096 * 4];
        for (int i = 0; i < chunks.length(); i++) {
            WorldChunk chunk = (WorldChunk)chunks.get(i);
            if (chunk == null) continue;
            String key = chunk.getPos().x + "," + chunk.getPos().z;
            MessageDigest block = MessageDigest.getInstance("SHA-256"), light = MessageDigest.getInstance("SHA-256");
            for (ChunkSection section : chunk.getSectionArray()) {
                int p = 0;
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                    int state = section == null ? 0 : Block.getRawIdFromState(section.getBlockState(x,y,z));
                    states[p++]=(byte)(state>>>24); states[p++]=(byte)(state>>>16);
                    states[p++]=(byte)(state>>>8); states[p++]=(byte)state;
                }
                block.update(states);
            }
            for (LightType type : LightType.values()) for (int y=-1;y<=16;y++) {
                ChunkNibbleArray data = client.world.getLightingProvider().get(type).getLightArray(ChunkSectionPos.from(chunk.getPos(),y));
                light.update((byte)(data == null ? 0 : 1));
                if (data != null) light.update(data.asByteArray());
            }
            blocks.put(key,hex(block.digest())); lights.put(key,hex(light.digest()));
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity == handler.getCameraEntity()) continue;
            String value = Registry.ENTITY_TYPE.getId(entity.getType())+"/"+entity.getX()+"/"+entity.getY()+"/"+entity.getZ()
                    +"/"+entity.yaw+"/"+entity.pitch;
            if (entity instanceof LivingEntity) value += "/"+((LivingEntity)entity).getHealth();
            entities.put(Integer.toString(entity.getEntityId()),value);
        }
        Map<String,Object> data = new TreeMap<>();
        Object index = get(controls,"milestoneIndex"), metadata = get(controls,"recordingIndex");
        data.put("chests",new TreeMap<>((Map<?,?>)get(get(index,"chests"),"frames")));
        data.put("loot",new TreeMap<>((Map<?,?>)get(get(index,"chestLoot"),"frames")));
        data.put("hud",get(metadata,"hud"));
        event("snapshot","target",time,"dimension",client.world.getRegistryKey().getValue().toString(),
                "pendingLight",client.world.getLightingProvider().hasUpdates(),"blocks",blocks,"lights",lights,"entities",entities,
                "viewerData",hex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(data).getBytes(StandardCharsets.UTF_8))));
    }

    private void event(String kind,Object... pairs) throws Exception {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("event",kind); row.put("phase","seek-"+file+"-"+target); row.put("epochMillis",System.currentTimeMillis());
        if (files != null && file < files.length) row.put("file",files[file].getName());
        for (int i=0;i<pairs.length;i+=2) row.put((String)pairs[i],pairs[i+1]);
        Files.write(output,(JSON.toJson(row)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        if (!kind.equals("snapshot")) ZsgRooms.LOGGER.info("[ViewerSeekProfile] {}",JSON.toJson(row));
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b:bytes) value.append(String.format(Locale.ROOT,"%02x",b&255));
        return value.toString();
    }
    private static Object get(Object object,String name) throws Exception { return get(object,object.getClass(),name); }
    private static Object get(Object object,Class<?> type,String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
}
