package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import zsgrooms.modid.ZsgRooms;

/** Synthetic client entities exercise overlays without altering the input recording. */
final class AnalysisSmokeChecks {
    private int step;
    private Object controls;
    private Object analysis;
    private EnderDragonEntity dragon;
    private Vec3d anchor;

    void tick(Object raw, MinecraftClient client) throws Exception {
        ReplayHandler handler = (ReplayHandler) raw;
        if (step == 0) {
            java.lang.reflect.Field instance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
            instance.setAccessible(true);
            controls = get(instance.get(null), "controls");
            analysis = get(controls, "analysis");
            ViewerSmokeChecks.dropdown(handler.getOverlay()).getClass().getMethod("setSelected", int.class)
                    .invoke(ViewerSmokeChecks.dropdown(handler.getOverlay()), 3);
            handler.getOverlay().setMouseVisible(false);
        } else if (step == 1) {
            CameraEntity camera = handler.getCameraEntity();
            Object orbit = get(controls, "followDistance");
            double yaw = (Double) get(orbit, "yaw");
            PlayerEntity player = client.world.getPlayers().stream().filter(p -> !(p instanceof CameraEntity)).findFirst().get();
            float originalYaw = player.yaw;
            player.yaw += 90;
            camera.getCameraController().update(0);
            require((Double) get(orbit, "yaw") == yaw, "Player turn rotated drone");
            player.yaw = originalYaw;
            client.openScreen(null);
            client.mouse.lockCursor();
            set(client.mouse, "leftButtonClicked", false);
            camera.changeLookDirection(100, 0);
            require((Double) get(orbit, "yaw") == yaw, "Mouse moved drone without held click");
            set(client.mouse, "leftButtonClicked", true);
            camera.changeLookDirection(100, 0);
            require(Math.abs((Double) get(orbit, "yaw") - yaw - 15) < 0.001, "Held mouse did not orbit");
            client.targetedEntity = player;
            KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(0));
            java.lang.reflect.Method input = CameraEntity.class.getDeclaredMethod("handleInputEvents");
            input.setAccessible(true);
            input.invoke(camera);
            require(handler.isCameraView(), "Orbit click activated spectating");
            set(client.mouse, "leftButtonClicked", false);
            handler.getOverlay().setMouseVisible(true);
            ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
            require(client.currentScreen.getClass().getSimpleName().equals("AnalysisScreen"), "Analysis menu did not open");
            for (Element element : client.currentScreen.children()) {
                if (element instanceof CheckboxWidget && !((CheckboxWidget) element).getMessage().getString().equals("Always show inventory")) {
                    ((CheckboxWidget) element).onPress();
                }
            }
            require((Boolean) get(analysis, "piglinCounter") && (Boolean) get(analysis, "dragonTrail")
                    && (Boolean) get(analysis, "piglinTrail"), "Analysis toggles failed");
            anchor = player.getPos();
            for (int i = 0; i < 3; i++) {
                PiglinEntity pig = new PiglinEntity(EntityType.PIGLIN, client.world);
                pig.setPos(anchor.x + 2 + i * 0.1, anchor.y + 38, anchor.z + 6);
                client.world.addEntity(900000 + i, pig);
            }
            dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, client.world);
            dragon.setPos(anchor.x - 6, anchor.y + 40, anchor.z + 6);
            client.world.addEntity(900010, dragon);
        } else if (step == 2) {
            capture(client, "viewer-analysis-menu.png");
            require((Integer) get(analysis, "piglins") >= 3, "Piglin cluster not counted");
            for (Element element : client.currentScreen.children()) {
                if (element instanceof ButtonWidget && ((ButtonWidget) element).getMessage().getString().equals("Customize")) {
                    ((ButtonWidget) element).onPress();
                    break;
                }
            }
            require(client.currentScreen.getClass().getSimpleName().equals("TrailSettingsScreen"), "Trail customization did not open");
            for (Element element : client.currentScreen.children()) {
                if (element instanceof SliderWidget) {
                    SliderWidget slider = (SliderWidget) element;
                    slider.mouseClicked(slider.x + slider.getWidth() - 5, slider.y + 10, 0);
                    slider.mouseReleased(slider.x + slider.getWidth() - 5, slider.y + 10, 0);
                }
                if (element instanceof ButtonWidget && ((ButtonWidget) element).getMessage().getString().equals("Green")) {
                    ((ButtonWidget) element).onPress();
                }
            }
            Object style = get(analysis, "dragonStyle");
            require((Integer) get(style, "width") == 4 && (Integer) get(style, "seconds") == 30
                    && (Integer) get(style, "opacity") == 100 && get(style, "color").toString().equals("GREEN"), "Style controls failed");
            require((Integer) get(get(analysis, "piglinStyle"), "width") == 1, "Dragon settings changed piglin settings");
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
        } else if (step == 3) {
            capture(client, "viewer-trail-settings-small.png");
            client.currentScreen.onClose();
            client.currentScreen.onClose();
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            ViewerSmokeChecks.dropdown(handler.getOverlay()).getClass().getMethod("setSelected", int.class)
                    .invoke(ViewerSmokeChecks.dropdown(handler.getOverlay()), 0);
            java.lang.reflect.Method updateCamera = controls.getClass().getDeclaredMethod("updateCamera");
            updateCamera.setAccessible(true);
            updateCamera.invoke(controls);
            handler.getCameraEntity().setCameraPosition(anchor.x, anchor.y + 40, anchor.z - 12);
            handler.getCameraEntity().setCameraRotation(0, 0, 0);
            dragon.setPos(anchor.x + 6, anchor.y + 40, anchor.z + 6);
            for (int i = 0; i < 3; i++) client.world.getEntityById(900000 + i).setPos(anchor.x - 6, anchor.y + 38, anchor.z + 6 + i * 1.5);
            set(analysis, "piglinCounter", false);
        } else if (step == 4) {
            require(!((java.util.Map<?, ?>) get(analysis, "trails")).isEmpty(), "Dragon samples missing");
            java.util.Map<?, ?> pigs = (java.util.Map<?, ?>) get(analysis, "piglinTrails");
            require(pigs.size() == 3, "Piglin trails need counter enabled or include unrelated entities");
            for (Object history : pigs.values()) require(((java.util.Collection<?>) get(history, "points")).size() >= 2, "Piglin trail did not accumulate");
            capture(client, "viewer-dragon-trail.png");
            handler.getReplaySender().setReplaySpeed(0);
            java.util.Map<?, ?> trails = (java.util.Map<?, ?>) get(analysis, "trails");
            require(((java.util.Collection<?>) get(trails.values().iterator().next(), "points")).size() >= 2, "Trail did not accumulate");
            handler.doJump(1500, true);
            require(((java.util.Map<?, ?>) get(analysis, "trails")).isEmpty(), "Seek did not clear trail");
            require(pigs.isEmpty(), "Seek did not clear piglin trails");
            handler.getReplaySender().setReplaySpeed(1);
        } else {
            for (int i = 0; i < 3; i++) client.world.removeEntity(900000 + i);
            client.world.removeEntity(900010);
            ZsgRooms.LOGGER.info("[ReplayAnalysisSmoke] PASS: drone inputs, cluster counter, independent trail customization, dragon and piglin trails, seek clearing");
        }
        step++;
    }

    private static Object get(Object object, String name) throws Exception {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
