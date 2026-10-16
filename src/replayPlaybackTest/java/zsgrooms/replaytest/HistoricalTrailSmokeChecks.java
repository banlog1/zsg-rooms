package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixture: 97310cb1-627b-4ffa-8772-242ce63bd6bf.mcpr. Never changes the input ZIP. */
final class HistoricalTrailSmokeChecks {
    private int step;
    private long deadline;
    private long next;
    private Object controls, analysis, original, snapshot;
    private UUID playerId;
    private Vec3d reference;
    private int referenceTime;
    private int frozenTime;
    private List<String> frozenPoints;
    private UUID pigId, dragonId, blazeId;
    private int recordedMobs;

    boolean tick(ReplayHandler handler, MinecraftClient client) throws Exception {
        if (client.world == null || handler.getCameraEntity() == null || System.nanoTime() < next) return false;
        if (step == 0) {
            if (handler.getReplaySender().currentTimeStamp() < 2500) return false;
            Class<?> viewer = Class.forName("zsgrooms.replayviewer.ReplayViewer");
            controls = get(field(viewer, "instance").get(null), "controls");
            analysis = get(controls, "analysis");
            original = call(controls, "snapshot");
            client.options.guiScale = 2;
            client.onResolutionChanged();
            handler.getReplaySender().setReplaySpeed(0);
            set(analysis, "dragonTrail", false);
            set(analysis, "piglinTrail", false);
            set(analysis, "playerTrail", false);
            call(get(analysis, "mobTrails"), "clear");
            seek(handler, 10000);
            PlayerEntity player = player(client);
            playerId = player.getUuid();
            reference = player.getPos();
            referenceTime = handler.getReplaySender().currentTimeStamp();
            seek(handler, 14000);
            frozenTime = handler.getReplaySender().currentTimeStamp();
            openSettings(client);
            click(client, "Analysis");
            CheckboxWidget checkbox = checkbox(client, "Player trails");
            require(!checkbox.isChecked(), "Player trails were enabled before the paused enable test");
            checkbox.onPress();
            ButtonWidget customize = null;
            for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
                ButtonWidget button = (ButtonWidget) child;
                if (button.visible && button.y == checkbox.y && button.getMessage().getString().equals("Customize")) customize = button;
            }
            require(customize != null, "Player trail customization missing");
            customize.onPress();
            require(client.currentScreen.getTitle().getString().equals("Player Trails"), "Wrong trail customization opened");
            click(client, "Pink");
            setSlider(client, "Length:", 0);
            setSlider(client, "Thickness:", 1);
            require((Integer) get(get(analysis, "playerStyle"), "seconds") == 2, "Player length slider failed");
            require((Integer) get(get(analysis, "playerStyle"), "width") == 4, "Player thickness slider failed");
            require(get(get(analysis, "playerStyle"), "color").toString().equals("PINK"), "Player color swatch failed");
            require((Integer) get(get(analysis, "dragonStyle"), "width") == 1
                    && (Integer) get(get(analysis, "piglinStyle"), "width") == 1, "Player styling changed shortcut styles");
            set(get(analysis, "playerStyle"), "seconds", 5);
            next = System.nanoTime() + 500000000L;
            step = 9;
        } else if (step == 9) {
            capture(client, "viewer-player-trail-settings.png");
            client.currentScreen.onClose();
            client.currentScreen.onClose();
            waitStep(1);
        } else if (step == 1) {
            if (!playerHistoryReady(analysis, playerId, frozenTime)) return waiting("paused player history");
            requirePaused(handler);
            bounds(paths("playerTrails"), frozenTime, 5000, 0);
            require(matchesReference(paths("playerTrails").get(playerId)), "Historical trail does not match the earlier recorded player position");
            frozenPoints = points(paths("playerTrails"));
            next = System.nanoTime() + 1200000000L;
            step = 2;
        } else if (step == 2) {
            requirePaused(handler);
            require(frozenPoints.equals(points(paths("playerTrails"))), "Paused history accumulated samples without replay time advancing");
            set(get(analysis, "playerStyle"), "seconds", 2);
            waitStep(3);
        } else if (step == 3) {
            if (!within(paths("playerTrails"), frozenTime, 2000)) return waiting("shorter paused history window");
            bounds(paths("playerTrails"), frozenTime, 2000, 0);
            requirePaused(handler);
            set(get(analysis, "playerStyle"), "seconds", 5);
            seek(handler, 8000);
            frozenTime = handler.getReplaySender().currentTimeStamp();
            waitStep(4);
        } else if (step == 4) {
            if (!playerHistoryReady(analysis, playerId, frozenTime)) return waiting("backward seek history");
            requirePaused(handler);
            bounds(paths("playerTrails"), frozenTime, 5000, 0);
            seek(handler, 14000);
            frozenTime = handler.getReplaySender().currentTimeStamp();
            openSettings(client);
            click(client, "Analysis");
            click(client, "Mob Trails");
            require(client.currentScreen.getTitle().getString().equals("Mob Trails"), "Mob trail picker did not open");
            search(client, "minecraft:blaze");
            click(client, "Blaze");
            require(selected(get(analysis, "mobTrails"), "minecraft:blaze"), "Non-piglin search selection failed");
            click(client, "Track selected mobs");
            require(!(Boolean) call(get(analysis, "mobTrails"), "active"), "Mob trail enable toggle failed");
            click(client, "Track selected mobs");
            require((Boolean) call(get(analysis, "mobTrails"), "active"), "Mob trail toggle lost selection");
            require(!(Boolean) call(get(analysis, "highlights"), "active"), "Trail picker changed mob highlights");
            click(client, "Blue");
            click(client, "Customize");
            require(client.currentScreen.getTitle().getString().equals("Mob Trails"), "Mob trail style screen missing");
            setSlider(client, "Thickness:", 0);
            set(get(analysis, "mobStyle"), "seconds", 5);
            require((Integer) get(get(analysis, "playerStyle"), "width") == 4
                    && get(get(analysis, "playerStyle"), "color").toString().equals("PINK"), "Mob style changed player style");
            client.currentScreen.onClose();
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
            next = System.nanoTime() + 500000000L;
            step = 5;
        } else if (step == 5) {
            assertBounds(client.currentScreen);
            capture(client, "viewer-mob-trails-picker-small.png");
            client.currentScreen.onClose();
            client.currentScreen.onClose();
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            Object selection = get(analysis, "mobTrails");
            for (Entity entity : client.world.getEntities()) if (entity instanceof MobEntity) {
                select(selection, Registry.ENTITY_TYPE.getId(entity.getType()), true);
                recordedMobs++;
            }
            require(recordedMobs > 0, "Fixture has no loaded recorded mobs at 14000ms");
            waitStep(6);
        } else if (step == 6) {
            if (!hasHistory(paths("mobPaths"), frozenTime)) return waiting("recorded mob history");
            requirePaused(handler);
            bounds(paths("mobPaths"), frozenTime, 5000, 0);
            bounds(paths("playerTrails"), frozenTime, 5000, 0);
            require(matchesReference(paths("playerTrails").get(playerId)), "Forward seek failed to restore earlier player positions");
            capture(client, "viewer-historical-trails.png");
            seek(handler, 87500);
            frozenTime = handler.getReplaySender().currentTimeStamp();
            playerId = player(client).getUuid();
            waitStep(7);
        } else if (step == 7) {
            if (!playerHistoryReady(analysis, playerId, frozenTime)) return waiting("reset-world player history");
            requirePaused(handler);
            bounds(paths("playerTrails"), frozenTime, 5000, 71029);
            bounds(paths("mobPaths"), frozenTime, 5000, 71029);
            PlayerEntity player = player(client);
            Vec3d p = player.getPos();
            PiglinEntity pig = new PiglinEntity(EntityType.PIGLIN, client.world);
            pig.setPos(p.x + 2, p.y, p.z + 2);
            client.world.addEntity(900040, pig);
            pigId = pig.getUuid();
            EnderDragonEntity dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, client.world);
            dragon.setPos(p.x + 4, p.y + 3, p.z + 4);
            client.world.addEntity(900041, dragon);
            dragonId = dragon.getUuid();
            BlazeEntity blaze = new BlazeEntity(EntityType.BLAZE, client.world);
            blaze.setPos(p.x + 3, p.y + 1, p.z + 3);
            client.world.addEntity(900042, blaze);
            blazeId = blaze.getUuid();
            Object selection = get(analysis, "mobTrails");
            select(selection, new Identifier("minecraft:piglin"), true);
            select(selection, new Identifier("minecraft:ender_dragon"), true);
            set(analysis, "dragonTrail", true);
            set(analysis, "piglinTrail", true);
            waitStep(8);
        } else if (step == 8) {
            if (!paths("trails").containsKey(dragonId) || !paths("piglinTrails").containsKey(pigId)
                    || !paths("mobPaths").containsKey(blazeId)) return waiting("synthetic fallback and shortcut selection");
            require(!paths("mobPaths").containsKey(pigId) && !paths("mobPaths").containsKey(dragonId),
                    "Generic mob paths duplicate enabled dragon/piglin shortcut rendering");
            require(java.util.Collections.disjoint(paths("mobPaths").keySet(), paths("trails").keySet())
                    && java.util.Collections.disjoint(paths("mobPaths").keySet(), paths("piglinTrails").keySet()),
                    "A recorded mob is rendered by both generic and shortcut trails");
            snapshot = call(controls, "snapshot");
            require((Boolean) get(snapshot, "playerTrail"), "Snapshot lost player trail toggle");
            require(selected(get(snapshot, "mobTrails"), "minecraft:blaze"), "Snapshot lost mob trail selection");
            require(get(snapshot, "playerStyle") != get(analysis, "playerStyle")
                    && get(snapshot, "mobStyle") != get(analysis, "mobStyle")
                    && get(snapshot, "mobTrails") != get(analysis, "mobTrails"), "Snapshot shares mutable trail settings");
            set(analysis, "playerTrail", false);
            call(get(analysis, "mobTrails"), "clear");
            set(get(analysis, "playerStyle"), "seconds", 30);
            set(get(analysis, "mobStyle"), "width", 4);
            require((Integer) get(get(snapshot, "playerStyle"), "seconds") == 5
                    && (Integer) get(get(snapshot, "mobStyle"), "width") == 1
                    && get(get(snapshot, "playerStyle"), "color").toString().equals("PINK")
                    && get(get(snapshot, "mobStyle"), "color").toString().equals("BLUE")
                    && selected(get(snapshot, "mobTrails"), "minecraft:blaze"), "Mutating live options changed snapshot");
            restore(snapshot);
            require((Boolean) get(analysis, "playerTrail") && selected(get(analysis, "mobTrails"), "minecraft:blaze")
                    && (Integer) get(get(analysis, "playerStyle"), "seconds") == 5
                    && (Integer) get(get(analysis, "mobStyle"), "width") == 1, "Restore lost trail selection/styles");
            call(get(snapshot, "mobTrails"), "clear");
            require(selected(get(analysis, "mobTrails"), "minecraft:blaze"), "Restored selection shares snapshot state");
            client.world.removeEntity(900040);
            client.world.removeEntity(900041);
            client.world.removeEntity(900042);
            restore(original);
            ZsgRooms.LOGGER.info("[HistoricalTrailSmoke] PASS: paused historical player positions, stable samples, backward/forward seeks, time-window bounds, reset worlds, recorded mob history ({} loaded mobs), searchable non-piglin picker, small layout, independent styles, synthetic fallback, shortcut deduplication, snapshot/restore", recordedMobs);
            return true;
        }
        return false;
    }

    private void requirePaused(ReplayHandler handler) {
        require(handler.getReplaySender().paused() && handler.getReplaySender().currentTimeStamp() == frozenTime,
                "Historical trail check advanced paused playback");
    }

    private void seek(ReplayHandler handler, int time) throws Exception {
        handler.doJump(time, true);
        handler.getReplaySender().setReplaySpeed(0);
    }

    private void waitStep(int value) { step = value; deadline = System.nanoTime() + 45000000000L; }
    private boolean waiting(String label) throws Exception {
        if (System.nanoTime() > deadline) throw new IllegalStateException("Timed out waiting for " + label + ": " + call(analysis, "trailStatus"));
        return false;
    }

    private void openSettings(MinecraftClient client) throws Exception {
        Class<?> type = Class.forName("zsgrooms.replayviewer.AnalysisScreen");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(analysis.getClass(),
                Class.forName("zsgrooms.replayviewer.FollowDetailOptions"), Class.forName("zsgrooms.replayviewer.PlaybackMode"));
        constructor.setAccessible(true);
        client.openScreen((Screen) constructor.newInstance(analysis, get(controls, "details"), get(controls, "playback")));
    }

    private void restore(Object state) throws Exception { call(controls, "restore", new Class<?>[]{state.getClass()}, state); }
    @SuppressWarnings("unchecked")
    private Map<UUID, Object> paths(String name) throws Exception { return (Map<UUID, Object>) get(analysis, name); }

    private static boolean playerHistoryReady(Object analysis, UUID id, int time) throws Exception {
        Object history = ((Map<?, ?>) get(analysis, "playerTrails")).get(id);
        return history != null && hasHistory(java.util.Collections.singletonMap(id, history), time);
    }
    private static boolean hasHistory(Map<?, ?> histories, int time) throws Exception {
        for (Object history : histories.values()) for (Object point : (Collection<?>) get(history, "points")) {
            if ((Integer) get(point, "time") < time - 250) return true;
        }
        return false;
    }
    private static boolean within(Map<?, ?> histories, int time, int duration) throws Exception {
        if (histories.isEmpty()) return false;
        for (Object history : histories.values()) for (Object point : (Collection<?>) get(history, "points")) {
            int sampleTime = (Integer) get(point, "time");
            if (sampleTime > time || sampleTime < time - duration) return false;
        }
        return hasHistory(histories, time);
    }
    private static void bounds(Map<?, ?> histories, int time, int duration, int worldStart) throws Exception {
        for (Object history : histories.values()) {
            int previous = Integer.MIN_VALUE;
            for (Object point : (Collection<?>) get(history, "points")) {
                int sampleTime = (Integer) get(point, "time");
                require(sampleTime <= time, "Trail contains future sample " + sampleTime + " at " + time);
                require(sampleTime >= Math.max(worldStart, time - duration), "Trail exceeds configured window/world boundary");
                require(sampleTime >= previous, "Trail samples are out of order");
                previous = sampleTime;
            }
        }
    }
    private boolean matchesReference(Object history) throws Exception {
        if (history == null) return false;
        Object latest = null;
        for (Object point : (Collection<?>) get(history, "points")) {
            if ((Integer) get(point, "time") > referenceTime) break;
            latest = point;
        }
        if (latest == null) return false;
        double dx = (Double) get(latest, "x") - reference.x;
        double dy = (Double) get(latest, "y") - reference.y;
        double dz = (Double) get(latest, "z") - reference.z;
        return dx * dx + dy * dy + dz * dz < 4;
    }
    private static List<String> points(Map<?, ?> histories) throws Exception {
        List<String> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : histories.entrySet()) for (Object point : (Collection<?>) get(entry.getValue(), "points")) {
            result.add(entry.getKey() + ":" + get(point, "time") + ":" + get(point, "x") + ":" + get(point, "y") + ":" + get(point, "z"));
        }
        java.util.Collections.sort(result);
        return result;
    }
    private static PlayerEntity player(MinecraftClient client) {
        return client.world.getPlayers().stream().filter(p -> !(p instanceof CameraEntity)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Recorded player missing"));
    }
    private static void select(Object selection, Identifier type, boolean enabled) throws Exception {
        call(selection, "set", new Class<?>[]{Identifier.class, boolean.class}, type, enabled);
    }
    private static boolean selected(Object selection, String type) throws Exception {
        return (Boolean) call(selection, "selected", new Class<?>[]{Identifier.class}, new Identifier(type));
    }
    private static void search(MinecraftClient client, String query) {
        for (Element child : client.currentScreen.children()) if (child instanceof TextFieldWidget) {
            ((TextFieldWidget) child).setText(query);
            return;
        }
        throw new IllegalStateException("Mob picker search field missing");
    }
    private static CheckboxWidget checkbox(MinecraftClient client, String label) {
        for (Element child : client.currentScreen.children()) if (child instanceof CheckboxWidget) {
            CheckboxWidget checkbox = (CheckboxWidget) child;
            if (checkbox.visible && checkbox.active && checkbox.getMessage().getString().equals(label)) return checkbox;
        }
        throw new IllegalStateException("Missing checkbox: " + label);
    }
    private static void click(MinecraftClient client, String label) {
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (button.visible && button.active && button.getMessage().getString().equals(label)) { button.onPress(); return; }
        }
        checkbox(client, label).onPress();
    }
    private static void setSlider(MinecraftClient client, String label, double fraction) {
        for (Element child : client.currentScreen.children()) if (child instanceof SliderWidget) {
            SliderWidget slider = (SliderWidget) child;
            if (!slider.getMessage().getString().startsWith(label)) continue;
            double x = slider.x + 4 + fraction * (slider.getWidth() - 8);
            slider.mouseClicked(x, slider.y + 10, 0);
            slider.mouseReleased(x, slider.y + 10, 0);
            return;
        }
        throw new IllegalStateException("Missing trail slider: " + label);
    }
    private static void assertBounds(Screen screen) {
        for (Element child : screen.children()) if (child instanceof AbstractButtonWidget) {
            AbstractButtonWidget button = (AbstractButtonWidget) child;
            if (button.visible) require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= screen.width
                    && button.y + button.getHeight() <= screen.height, "Trail picker control outside small viewport");
        }
    }
    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target.getClass(), name).set(target, value); }
    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object target, String name, Class<?>[] parameters, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(target, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
