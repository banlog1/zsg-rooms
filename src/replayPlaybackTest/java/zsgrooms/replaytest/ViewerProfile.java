package zsgrooms.replaytest;

import com.google.gson.Gson;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/** Opt-in development driver: profiles the installed viewer without altering its implementation. */
final class ViewerProfile {
    private static final Gson JSON = new Gson();
    private final boolean coldIndexes = Boolean.getBoolean("zsgrooms.viewerProfileColdIndexes");
    private final Path output = Paths.get(System.getProperty("zsgrooms.viewerProfileOutput", "../../run/viewer-performance/baseline.jsonl"));
    private final List<Double> frames = new ArrayList<>();
    private File[] files;
    private ReplayHandler handler;
    private Object controls;
    private int openNumber, scene = -1, ticks;
    private long started, opened, phaseStart, previousFrame;
    private String state = "start", phase = "startup";
    private boolean driving, finished;
    private volatile boolean scanDone;
    private volatile Throwable scanError;
    private long maxEntities, maxPiglins;
    private static final int[] MODES = {2,4,0,4,4,0,4,2,4};
    private static final String[] NAMES = {"follow", "details", "freecam", "details-analysis", "details-analysis-repeat",
            "freecam-repeat", "details-repeat", "follow-repeat", "end-analysis"};

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
            long now = System.nanoTime();
            if (started != 0 && now - started > 600_000_000_000L) throw new IllegalStateException("Profile timeout");
            if (state.equals("start")) {
                if (client.getOverlay() != null || ++ticks < 40) return;
                if (Files.exists(output)) throw new IllegalStateException("Use a fresh profiling output path");
                started = now;
                client.options.pauseOnLostFocus = false;
                client.options.maxFps = 120;
                client.options.enableVsync = false;
                client.getWindow().setVsync(false);
                client.options.viewDistance = 8;
                client.options.guiScale = 2;
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                File input = new File(System.getProperty("zsgrooms.replayPlaybackFile"));
                files = input.getParentFile().listFiles((dir, name) -> name.endsWith(".mcpr"));
                if (files == null || files.length != 3) throw new IllegalStateException("Expected three private replay copies");
                Arrays.sort(files, Comparator.comparing(ViewerProfile::hasEnd)
                        .thenComparing(Comparator.comparingLong(File::length).reversed()));
                event("environment", "java", System.getProperty("java.version"), "maxHeap", Runtime.getRuntime().maxMemory(),
                        "width",1280,"height",720,"renderDistance",8,"fpsCap",120);
                open();
            } else if (state.equals("opening")) {
                Object viewer = get(null, Class.forName("zsgrooms.replayviewer.ReplayViewer"), "instance");
                if (get(viewer, viewer.getClass(), "current") != handler) return;
                controls = get(viewer,"controls");
                if (controls == null) return;
                Object index = get(controls,"milestoneIndex"), metadata = get(controls,"recordingIndex");
                if (((Thread)get(index,"worker")).isAlive() || ((Thread)get(metadata,"worker")).isAlive()
                        || client.world == null || handler.getCameraEntity() == null) return;
                String status = (String)get(index,"chestStatus");
                if (!status.isEmpty()) throw new IllegalStateException("Chest indexing failed: " + status);
                event("open-ready", "file",files[openNumber%3].getName(),"repeat",openNumber>=3,
                        "wallMs",(now-opened)/1e6,"milestones",((List<?>)get(index,"entries")).size(),
                        "cacheHit",cacheHit(index));
                handler.getReplaySender().setReplaySpeed(0);
                if (openNumber < 3) {
                    state="scan"; scanDone=false;
                    final Object archive = handler.getReplayFile();
                    new Thread(() -> measureIndexes(archive),"ZSG profile driver").start();
                } else nextOpen();
            } else if (state.equals("scan")) {
                if (!scanDone) return;
                if (scanError != null) throw new IllegalStateException("Isolated indexing failed",scanError);
                nextOpen();
            } else if (state.equals("settle")) {
                if (now-phaseStart < 3_000_000_000L) return;
                frames.clear(); maxEntities=0; maxPiglins=0;
                previousFrame=now; phaseStart=now; state="measure";
                phase=NAMES[scene]; event("phase-start","dimension",client.world.getRegistryKey().getValue().toString());
                if(scene==8 && !client.world.getRegistryKey().getValue().toString().equals("minecraft:the_end"))
                    throw new IllegalStateException("End sample did not reach the End");
            } else if (state.equals("measure")) {
                frames.add((now-previousFrame)/1e6); previousFrame=now;
                if (frames.size()%30==0 && client.world != null) {
                    long entities=0,piglins=0;
                    for (Entity e:client.world.getEntities()) {entities++; if(e instanceof PiglinEntity) piglins++;}
                    maxEntities=Math.max(maxEntities,entities); maxPiglins=Math.max(maxPiglins,piglins);
                }
                if (now-phaseStart < 12_000_000_000L) return;
                event("phase-end","frames",frames.size(),"wallMs",(now-phaseStart)/1e6,
                        "p50Ms",percentile(0.5),"p95Ms",percentile(0.95),"p99Ms",percentile(0.99),
                        "maxEntities",maxEntities,"maxPiglins",maxPiglins,
                        "detailState",get(get(controls,"detailHud"),"state")!=null);
                ScreenshotUtils.saveScreenshot(client.runDirectory,"profile-"+scene+".png",client.getWindow().getFramebufferWidth(),
                        client.getWindow().getFramebufferHeight(),client.getFramebuffer(),message -> {});
                nextScene(client);
            }
        } catch(Throwable error) {
            finished=true;
            ZsgRooms.LOGGER.error("[ViewerProfile] FAIL",error);
            try { if(handler!=null) handler.endReplay(); } catch(Exception ignored) { }
            client.scheduleStop();
        } finally {driving=false;}
    }

    private void open() throws Exception {
        phase="open-"+openNumber;
        event("phase-start","file",files[openNumber%3].getName());
        opened=System.nanoTime();
        ReplayModReplay.instance.startReplay(files[openNumber%3]);
        handler=ReplayModReplay.instance.getReplayHandler();
        if(handler==null) throw new IllegalStateException("Replay refused");
        event("open-return","wallMs",(System.nanoTime()-opened)/1e6);
        state="opening";
    }

    private void nextOpen() throws Exception {
        event("phase-end");
        if(++openNumber==6) {
            if(coldIndexes) finish(MinecraftClient.getInstance()); else nextScene(MinecraftClient.getInstance());
            return;
        }
        handler.endReplay(); handler=null;
        open();
    }

    private void measureIndexes(Object archive) {
        try {
            Class<?> type=Class.forName("zsgrooms.replayviewer.MilestoneIndex");
            Constructor<?> ctor=type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            String expected = historyDigest(get(controls,"milestoneIndex"));
            event("initial-history","file",files[openNumber%3].getName(),"sha256",expected);
            for(int i=0;i<(coldIndexes ? 5 : 3);i++) {
                if(coldIndexes) {
                    Object cache=get(null,type,"CACHE");
                    synchronized(cache) {
                        ((Map<?,?>)get(cache,"values")).clear();
                        set(cache,"weight",0L);
                    }
                }
                long start=System.nanoTime();
                Object index=ctor.newInstance(archive);
                Thread worker=(Thread)get(index,"worker"); worker.join(120000);
                if(worker.isAlive()) throw new IllegalStateException("Index timeout");
                if(!((String)get(index,"chestStatus")).isEmpty()) throw new IllegalStateException("Chest scan unavailable");
                event("isolated-index","file",files[openNumber%3].getName(),"trial",i,"wallMs",(System.nanoTime()-start)/1e6,
                        "cacheHit",cacheHit(index),
                        "chestKeys",((Map<?,?>)get(get(index,"chests"),"frames")).size(),
                        "lootKeys",((Map<?,?>)get(get(index,"chestLoot"),"frames")).size());
                Object original = get(controls,"milestoneIndex");
                if (!coldIndexes && cacheHit(index) != null && (!cacheHit(index) || get(index,"chests") != get(original,"chests")
                        || get(index,"chestLoot") != get(original,"chestLoot") || get(index,"entries") != get(original,"entries")))
                    throw new IllegalStateException("Completed index was not reused intact");
                if(coldIndexes && Boolean.TRUE.equals(cacheHit(index))) throw new IllegalStateException("Expected a fresh scan");
                String digest=historyDigest(index);
                if(!expected.equals(digest)) throw new IllegalStateException("Index history changed");
                event("index-history","file",files[openNumber%3].getName(),"trial",i,"sha256",digest);
                Method close=type.getDeclaredMethod("close");close.setAccessible(true);close.invoke(index);
            }
        } catch(Throwable error) {scanError=error;} finally {scanDone=true;}
    }

    private void nextScene(MinecraftClient client) throws Exception {
        if(++scene==NAMES.length) {
            finish(client);return;
        }
        phase="seek-"+NAMES[scene];event("phase-start");
        int target=scene==8 ? 180000 : 90000;
        long start=System.nanoTime();
        handler.doJump(target,true);
        event("phase-end","wallMs",(System.nanoTime()-start)/1e6,"target",target);
        Object camera=get(controls,"camera");camera.getClass().getMethod("setSelected",int.class).invoke(camera,MODES[scene]);
        boolean enabled=NAMES[scene].contains("analysis");
        Object analysis=get(controls,"analysis");
        for(String field:Arrays.asList("piglinTrail","dragonTrail","piglinCounter")) set(analysis,field,enabled);
        Object highlights=get(analysis,"highlights");
        Method select=highlights.getClass().getDeclaredMethod("set",Identifier.class,boolean.class);select.setAccessible(true);
        select.invoke(highlights,new Identifier("minecraft:piglin"),enabled);
        select.invoke(highlights,new Identifier("minecraft:ender_dragon"),enabled);
        client.openScreen(null); handler.getOverlay().setMouseVisible(false);
        handler.getReplaySender().setReplaySpeed(1.0);
        state="settle";phaseStart=System.nanoTime();
    }

    private void finish(MinecraftClient client) throws Exception {
        checkCacheGuards();
        finished=true; handler.endReplay();handler=null;
        event("complete"); ZsgRooms.LOGGER.info("[ViewerProfile] PASS");client.scheduleStop();
    }

    @SuppressWarnings("unchecked")
    private static String historyDigest(Object index) throws Exception {
        com.google.gson.Gson gson=new com.google.gson.GsonBuilder().registerTypeAdapter(net.minecraft.item.ItemStack.class,
                (com.google.gson.JsonSerializer<net.minecraft.item.ItemStack>)(stack,type,context)->
                        new com.google.gson.JsonPrimitive(stack.toTag(new net.minecraft.nbt.CompoundTag()).toString())).create();
        Map<String,Object> snapshot=new TreeMap<>();
        snapshot.put("milestones",get(index,"entries"));
        snapshot.put("chests",new TreeMap<>((Map<String,?>)get(get(index,"chests"),"frames")));
        snapshot.put("loot",new TreeMap<>((Map<String,?>)get(get(index,"chestLoot"),"frames")));
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();
        for(byte b:hash) result.append(String.format(Locale.ROOT,"%02x",b & 255));
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private void checkCacheGuards() throws Exception {
        Class<?> type=Class.forName("zsgrooms.replayviewer.MilestoneIndex");
        Object index=get(controls,"milestoneIndex");
        if(cacheHit(index)==null) return;
        Method key=type.getDeclaredMethod("cacheKey",com.replaymod.replaystudio.replay.ReplayFile.class);
        key.setAccessible(true);
        Object expected=key.invoke(null,handler.getReplayFile());
        if(expected==null) throw new IllegalStateException("Cache identity unavailable");
        Class<?> recording=Class.forName("zsgrooms.replayviewer.RecordingIndex");
        Method unwrap=recording.getDeclaredMethod("archive",com.replaymod.replaystudio.replay.ReplayFile.class);
        unwrap.setAccessible(true);
        Object archive=unwrap.invoke(null,handler.getReplayFile());
        Class<?> accessor=Class.forName("zsgrooms.replayviewer.mixin.ReplayFileAccessor");
        Map<String,Object> changed=(Map<String,Object>)accessor.getMethod("zsgViewer$getChangedEntries").invoke(archive);
        Map<String,Object> streams=(Map<String,Object>)accessor.getMethod("zsgViewer$getOutputStreams").invoke(archive);
        Set<String> removed=(Set<String>)accessor.getMethod("zsgViewer$getRemovedEntries").invoke(archive);
        String packets="recording.tmcpr";
        try {
            changed.put(packets,new File("unused-profile-guard"));
            if(key.invoke(null,handler.getReplayFile())!=null) throw new IllegalStateException("Dirty packet cache hit");
        } finally {changed.remove(packets);}
        try {
            streams.put(packets,new java.io.ByteArrayOutputStream());
            if(key.invoke(null,handler.getReplayFile())!=null) throw new IllegalStateException("Writing packet cache hit");
        } finally {streams.remove(packets);}
        try {
            removed.add(packets);
            if(key.invoke(null,handler.getReplayFile())!=null) throw new IllegalStateException("Removed packet cache hit");
        } finally {removed.remove(packets);}
        if(!expected.equals(key.invoke(null,handler.getReplayFile()))) throw new IllegalStateException("Cache identity not restored");
        Object cache=get(null,type,"CACHE");
        event("cache-guards-pass","estimatedRetainedBytes",get(cache,"weight"),
                "retainedEntries",((Map<?,?>)get(cache,"values")).size());
    }

    private static Boolean cacheHit(Object index) throws Exception {
        try {return (Boolean)get(index,"cacheHit");} catch(NoSuchFieldException ignored) {return null;}
    }

    private double percentile(double value) {
        List<Double> sorted=new ArrayList<>(frames);Collections.sort(sorted);
        return sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*value)-1));
    }
    private static boolean hasEnd(File file) {
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file);
             java.io.Reader reader=new java.io.InputStreamReader(zip.getInputStream(zip.getEntry("zsg-rooms/races.json")),StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject metadata=new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            for(com.google.gson.JsonElement interval:metadata.getAsJsonArray("intervals"))
                if(interval.getAsJsonObject().get("dimension").getAsString().equals("minecraft:the_end")) return true;
            return false;
        } catch(Exception e) {throw new IllegalStateException("Cannot read profiling fixture",e);}
    }
    private synchronized void event(String kind,Object... pairs) throws Exception {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("event",kind);row.put("phase",phase);row.put("epochMillis",System.currentTimeMillis());
        for(int i=0;i<pairs.length;i+=2) row.put((String)pairs[i],pairs[i+1]);
        String json=JSON.toJson(row);
        Files.write(output,(json+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        ZsgRooms.LOGGER.info("[ViewerProfile] {}",json);
    }
    private static Object get(Object object,String name) throws Exception {return get(object,object.getClass(),name);}
    private static Object get(Object object,Class<?> type,String name) throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
}
