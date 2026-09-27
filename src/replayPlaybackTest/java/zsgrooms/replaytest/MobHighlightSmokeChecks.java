package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

final class MobHighlightSmokeChecks {
    private int step;
    private long next;
    private Object options;
    private Object controls;
    private PiglinEntity pig;
    private BlazeEntity blaze;
    private net.minecraft.util.math.Vec3d cameraPosition;
    private final Map<BlockPos, BlockState> wall = new LinkedHashMap<>();

    boolean complete() { return step >= 5; }

    void tick(Object raw, MinecraftClient client) throws Exception {
        ReplayHandler handler = (ReplayHandler) raw;
        if (cameraPosition != null && handler.getCameraEntity() != null) {
            handler.getCameraEntity().setCameraPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            handler.getCameraEntity().setCameraRotation(0, 0, 0);
        }
        if (complete() || System.nanoTime() < next) return;
        if (client.world == null || handler.getCameraEntity() == null
                || handler.getReplaySender().currentTimeStamp() < 2500) return;
        next = System.nanoTime() + 1500000000L;
        if (step == 0) {
            handler.getReplaySender().setReplaySpeed(0);
            client.options.guiScale = 2;
            client.options.fov = 70;
            client.onResolutionChanged();
            Object viewer = get(null, Class.forName("zsgrooms.replayviewer.ReplayViewer"), "instance");
            controls = get(viewer, viewer.getClass(), "controls");
            Object analysis = get(controls, controls.getClass(), "analysis");
            options = get(analysis, analysis.getClass(), "highlights");
            require(!(Boolean) invoke(options, "active"), "Highlights started enabled for a type");
            // Open the same screen as the playback button without depending on cursor auto-hide timing.
            Class<?> screen = Class.forName("zsgrooms.replayviewer.AnalysisScreen");
            java.lang.reflect.Constructor<?> constructor = screen.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            client.openScreen((net.minecraft.client.gui.screen.Screen) constructor.newInstance(analysis,
                    get(controls, controls.getClass(), "details"), get(controls, controls.getClass(), "playback")));
            click(client, "Mob Highlights");
            require(client.currentScreen.getClass().getSimpleName().equals("MobHighlightScreen"), "Highlights menu missing");
            for (Element child : client.currentScreen.children()) if (child instanceof TextFieldWidget) ((TextFieldWidget) child).setText("piglin");
            click(client, "Piglin");
            click(client, "Green");
            require((Integer) invoke(options, "size") == 1, "Mob selection was not applied");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
        } else if (step == 1) {
            for (Element child : client.currentScreen.children()) if (child instanceof AbstractButtonWidget) {
                AbstractButtonWidget button = (AbstractButtonWidget) child;
                if (button.visible) require(button.x >= 0 && button.y >= 0 && button.x + button.getWidth() <= client.currentScreen.width
                        && button.y + button.getHeight() <= client.currentScreen.height, "Highlight control outside small viewport");
            }
            capture(client, "viewer-mob-highlights-menu-small.png");
            Object snapshot = invoke(controls, "snapshot");
            Object copied = get(snapshot, snapshot.getClass(), "highlights");
            invoke(copied, "clear");
            require((Integer) invoke(options, "size") == 1, "Player switch snapshot shared highlight selection");
            client.currentScreen.onClose();
            client.currentScreen.onClose();
            handler.getOverlay().setMouseVisible(true);
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            Entity player = client.world.getPlayers().stream().filter(p -> p != handler.getCameraEntity()).findFirst().get();
            double x = Math.floor(player.getX()), y = Math.floor(player.getY()) + 40, z = Math.floor(player.getZ());
            pig = new PiglinEntity(EntityType.PIGLIN, client.world);
            position(pig, x, y, z + 5);
            client.world.addEntity(900020, pig);
            blaze = new BlazeEntity(EntityType.BLAZE, client.world);
            position(blaze, x + 4, y, z + 5);
            client.world.addEntity(900021, blaze);
            for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 3; dy++) {
                BlockPos pos = new BlockPos(x + dx, y + dy, z + 2);
                wall.put(pos, client.world.getBlockState(pos));
                client.world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
            }
            cameraPosition = new net.minecraft.util.math.Vec3d(x, y + 1, z - 3);
            next += 3000000000L;
        } else if (step == 2) {
            require(client.method_27022(pig) && pig.getTeamColorValue() == 0x80ED99, "Selected mob did not use a green outline");
            require(!client.method_27022(blaze), "Unselected mob was highlighted");
            require(!pig.isGlowing() && pig.getScoreboardTeam() == null, "Highlight mutated recorded entity flags or teams");
            capture(client, "viewer-mob-highlights-wall.png");
            client.options.hudHidden = true;
            require(!client.method_27022(pig), "F1 did not hide viewer highlights");
            client.options.hudHidden = false;
            require(client.method_27022(pig), "F1 did not restore highlights");
            Field enabled = options.getClass().getDeclaredField("enabled");
            enabled.setAccessible(true);
            enabled.setBoolean(options, false);
            require(!client.method_27022(pig) && pig.getTeamColorValue() == 0xFFFFFF, "Disabled highlights changed vanilla rendering");
            enabled.setBoolean(options, true);
            invoke(controls, "clearTrails");
            require(client.method_27022(pig), "Seek cleanup discarded selected types");
            invoke(options, "clear");
            require(!client.method_27022(pig), "Clear left mob highlights behind");
        } else if (step == 3) {
            capture(client, "viewer-mob-highlights-cleared.png");
            for (Map.Entry<BlockPos, BlockState> entry : wall.entrySet()) client.world.setBlockState(entry.getKey(), entry.getValue(), 3);
            client.world.removeEntity(900020);
            client.world.removeEntity(900021);
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
        } else {
            int outlined = greenPixels(client, "viewer-mob-highlights-wall.png");
            int cleared = greenPixels(client, "viewer-mob-highlights-cleared.png");
            require(outlined > cleared + 100, "Screenshot did not show the selected outline through stone");
            ZsgRooms.LOGGER.info("[MobHighlightSmoke] PASS: selection UI, small layout, color, through-wall pixels ({} -> {}), unselected mobs, F1, clear, seek cleanup, snapshot independence and untouched entity flags/teams", outlined, cleared);
        }
        step++;
    }

    private static Object get(Object target, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void position(Entity entity, double x, double y, double z) {
        entity.refreshPositionAndAngles(x, y, z, 0, 0);
        // Playback is paused, so no entity tick initializes interpolation coordinates.
        entity.prevX = entity.lastRenderX = x;
        entity.prevY = entity.lastRenderY = y;
        entity.prevZ = entity.lastRenderZ = z;
    }
    private static int greenPixels(MinecraftClient client, String name) throws java.io.IOException {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.File(client.runDirectory, "screenshots/" + name));
        int count = 0;
        for (int y = 0; y < image.getHeight() * 2 / 3; y++) {
            for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                int rgb = image.getRGB(x, y);
                int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
                if (green > red + 30 && green > blue + 20) count++;
            }
        }
        return count;
    }
    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
    private static void click(MinecraftClient client, String label) {
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget
                && ((ButtonWidget) child).getMessage().getString().equals(label)) {
            ((ButtonWidget) child).onPress();
            return;
        }
        // CheckboxWidget is not a ButtonWidget in 1.16.1.
        for (Element child : client.currentScreen.children()) if (child instanceof net.minecraft.client.gui.widget.CheckboxWidget
                && ((net.minecraft.client.gui.widget.CheckboxWidget) child).getMessage().getString().equals(label)) {
            ((net.minecraft.client.gui.widget.CheckboxWidget) child).onPress();
            return;
        }
        throw new IllegalStateException("Missing highlight control: " + label);
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
