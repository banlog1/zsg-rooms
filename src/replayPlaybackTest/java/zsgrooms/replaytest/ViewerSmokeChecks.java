package zsgrooms.replaytest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.ZsgRooms;

import java.lang.reflect.Method;
import java.util.Collection;

/** Development-only checks against the actual installed companion's widget tree. */
final class ViewerSmokeChecks {
    private int step;
    private long next;
    private Object previousCamera;
    private int directSpeed;
    private double classicSpeed;
    private Object milestoneBar;
    private int milestoneTime;
    private int milestoneX;
    private int milestoneY;

    boolean complete() { return step > 18; }

    void tick(Object handler, MinecraftClient client) throws Exception {
        if (complete() || System.nanoTime() < next) return;
        Object overlay = handler.getClass().getMethod("getOverlay").invoke(handler);
        next = System.nanoTime() + 1000000000L;
        switch (step++) {
            case 0:
                require(hideChat(), "Replay chat was not hidden by default");
                client.options.guiScale = 2;
                client.onResolutionChanged();
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, false);
                next += 4000000000L;
                break;
            case 1:
                require(find(overlay, "Editor") == null, "Auto-hide left its buttons interactive");
                screenshot(client, "viewer-hidden.png");
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, true);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                GLFW.glfwSetCursorPos(client.getWindow().getHandle(), 100, 620);
                break;
            case 2:
                require(find(overlay, "Editor") != null, "Cursor did not reveal controls");
                Object chat = find(overlay, "Chat");
                require(chat != null, "Chat toggle missing");
                chat.getClass().getMethod("setChecked", boolean.class).invoke(chat, true);
                require(!hideChat(), "Chat toggle did not reveal chat");
                chat.getClass().getMethod("setChecked", boolean.class).invoke(chat, false);
                require(hideChat(), "Chat toggle did not hide chat again");
                screenshot(client, "viewer-wide.png");
                click(find(overlay, "Editor"));
                break;
            case 3:
                require(find(overlay, "Compact") != null, "Editor toggle not restored");
                ShortcutSmokeChecks.assertEditorBlocked(handler);
                screenshot(client, "viewer-editor.png");
                click(find(overlay, "Compact"));
                break;
            case 4:
                Object dropdown = dropdown(overlay);
                require(dropdown != null, "Camera dropdown missing");
                dropdown.getClass().getMethod("setSelected", int.class).invoke(dropdown, 2);
                break;
            case 5:
                require(!(Boolean) handler.getClass().getMethod("isCameraView").invoke(handler), "Follow player did not engage");
                dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(dropdown(overlay), 0);
                break;
            case 6:
                require((Boolean) handler.getClass().getMethod("isCameraView").invoke(handler), "Freecam did not resume");
                Object entity = handler.getClass().getMethod("getCameraEntity").invoke(handler);
                Object controller = entity.getClass().getMethod("getCameraController").invoke(entity);
                require(controller.getClass().getSimpleName().equals("VanillaCameraController"), "Direct freecam not selected");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, false);
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, true);
                break;
            case 7:
                screenshot(client, "viewer-small.png");
                Object sender = handler.getClass().getMethod("getReplaySender").invoke(handler);
                sender.getClass().getMethod("setReplaySpeed", double.class).invoke(sender, 2.0D);
                int before = (Integer) sender.getClass().getMethod("currentTimeStamp").invoke(sender);
                click(find(overlay, ">>"));
                int after = (Integer) sender.getClass().getMethod("currentTimeStamp").invoke(sender);
                require(after >= before + 4900, "Forward seek did not advance");
                require((Double) sender.getClass().getMethod("getReplaySpeed").invoke(sender) == 2.0D, "Seek changed playback speed");
                break;
            case 8:
                require(find(overlay, "Editor") != null, "Controls missing after seek");
                Object pausedSender = handler.getClass().getMethod("getReplaySender").invoke(handler);
                pausedSender.getClass().getMethod("setReplaySpeed", double.class).invoke(pausedSender, 0.0D);
                click(find(overlay, "<<"));
                require((Boolean) pausedSender.getClass().getMethod("paused").invoke(pausedSender), "Seek resumed paused playback");
                pausedSender.getClass().getMethod("setReplaySpeed", double.class).invoke(pausedSender, 1.0D);
                Object camera = dropdown(overlay);
                camera.getClass().getMethod("setOpened", boolean.class).invoke(camera, true);
                break;
            case 9:
                screenshot(client, "viewer-dropdown.png");
                Object autoHide = find(overlay, "Auto-hide");
                autoHide.getClass().getMethod("setChecked", boolean.class).invoke(autoHide, false);
                dropdown(overlay).getClass().getMethod("setOpened", boolean.class).invoke(dropdown(overlay), false);
                Object direct = controller(handler);
                direct.getClass().getMethod("increaseSpeed").invoke(direct);
                direct.getClass().getMethod("increaseSpeed").invoke(direct);
                directSpeed = (Integer) direct.getClass().getMethod("zsgViewer$getSpeed").invoke(direct);
                previousCamera = cameraEntity(handler);
                jump(handler, 22000);
                break;
            case 10:
                require((Integer) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler)) == directSpeed,
                        "Direct speed lost on dimension change");
                previousCamera = cameraEntity(handler);
                jump(handler, 68000);
                break;
            case 11:
                require(cameraEntity(handler) != previousCamera, "Fixture did not recreate camera on world reset");
                require((Integer) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler)) == directSpeed,
                        "Direct speed lost on world reset");
                dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(dropdown(overlay), 1);
                break;
            case 12:
                Object classic = controller(handler);
                classic.getClass().getMethod("increaseSpeed").invoke(classic);
                classicSpeed = (Double) classic.getClass().getMethod("zsgViewer$getSpeed").invoke(classic);
                jump(handler, 1500);
                break;
            case 13:
                require((Double) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler)) == classicSpeed,
                        "Classic speed lost on rewind/reset");
                dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(dropdown(overlay), 3);
                break;
            case 14:
                require(controller(handler).getClass().getSimpleName().equals("FarFollowController"), "Far follow not active");
                require((Boolean) handler.getClass().getMethod("isCameraView").invoke(handler), "Far follow left free camera view");
                screenshot(client, "viewer-far.png");
                jump(handler, 22000);
                break;
            case 15:
                require(controller(handler).getClass().getSimpleName().equals("FarFollowController"), "Far follow lost on dimension change");
                screenshot(client, "viewer-far-dimension.png");
                java.lang.reflect.Field instance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
                instance.setAccessible(true);
                Object controls = field(instance.get(null), "controls");
                Object index = field(controls, "milestoneIndex");
                require(((java.util.List<?>) field(index, "entries")).size() >= 2, "Fixture's milestones were not indexed: " + field(index, "status"));
                dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(dropdown(overlay), 0);
                break;
            case 16:
                require((Integer) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler)) == directSpeed,
                        "Direct speed lost after changing camera modes");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                break;
            case 17:
                screenshot(client, "viewer-milestones.png");
                java.lang.reflect.Field viewerInstance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
                viewerInstance.setAccessible(true);
                Object viewerControls = field(viewerInstance.get(null), "controls");
                milestoneBar = field(viewerControls, "milestones");
                Object first = ((java.util.List<?>) field(field(viewerControls, "milestoneIndex"), "entries")).get(0);
                milestoneTime = (Integer) field(first, "time");
                int duration = (Integer) handler.getClass().getMethod("getReplayDuration").invoke(handler);
                milestoneX = 18 + (int) ((long) milestoneTime * (client.getWindow().getScaledWidth() - 36) / duration);
                milestoneY = client.getWindow().getScaledHeight() - 52 + 10;
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, true);
                GLFW.glfwSetCursorPos(client.getWindow().getHandle(), milestoneX * 2, milestoneY * 2);
                break;
            case 18:
                screenshot(client, "viewer-milestone-tooltip.png");
                Class<?> renderInfo = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo");
                Object info = renderInfo.getConstructor(float.class, int.class, int.class, int.class)
                        .newInstance(0.0F, milestoneX, milestoneY, 0);
                Method tooltip = milestoneBar.getClass().getMethod("getTooltip", renderInfo);
                tooltip.setAccessible(true);
                require(tooltip.invoke(milestoneBar, info) != null, "Milestone tooltip missed its notch");
                Class<?> clickType = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.function.Click");
                Method markerClick = milestoneBar.getClass().getMethod("mouseClick", clickType);
                markerClick.setAccessible(true);
                Object markerSender = handler.getClass().getMethod("getReplaySender").invoke(handler);
                markerSender.getClass().getMethod("setReplaySpeed", double.class).invoke(markerSender, 0.0D);
                require((Boolean) markerClick.invoke(milestoneBar, clickType.getConstructor(int.class, int.class, int.class, int.class)
                        .newInstance(milestoneX, milestoneY, 0, 0)), "Milestone click missed its notch");
                require((Boolean) markerSender.getClass().getMethod("paused").invoke(markerSender), "Milestone seek resumed playback");
                int markerTime = (Integer) markerSender.getClass().getMethod("currentTimeStamp").invoke(markerSender);
                require(Math.abs(markerTime - milestoneTime) < 100, "Milestone click sought wrong time");
                markerSender.getClass().getMethod("setReplaySpeed", double.class).invoke(markerSender, 1.0D);
                ShortcutSmokeChecks.run((com.replaymod.replay.ReplayHandler) handler, client);
                ZsgRooms.LOGGER.info("[ReplayViewerSmoke] PASS: auto-hide, chat, editor, resize, seeking, milestones, far follow, camera speed across dimension/reset/mode changes, 3/4 shortcuts and rebinding");
                break;
            default:
                break;
        }
    }

    private static boolean hideChat() throws Exception {
        return (Boolean) Class.forName("zsgrooms.replayviewer.ReplayViewer").getMethod("shouldHideChat").invoke(null);
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object cameraEntity(Object handler) throws Exception {
        return handler.getClass().getMethod("getCameraEntity").invoke(handler);
    }

    private static Object controller(Object handler) throws Exception {
        Object entity = cameraEntity(handler);
        return entity.getClass().getMethod("getCameraController").invoke(entity);
    }

    private static void jump(Object handler, int time) throws Exception {
        Object sender = handler.getClass().getMethod("getReplaySender").invoke(handler);
        sender.getClass().getMethod("setReplaySpeed", double.class).invoke(sender, 0.0D);
        handler.getClass().getMethod("doJump", int.class, boolean.class).invoke(handler, time, true);
        sender.getClass().getMethod("setReplaySpeed", double.class).invoke(sender, 1.0D);
    }

    static Object find(Object element, String label) throws Exception {
        try {
            if (label.equals(element.getClass().getMethod("getLabel").invoke(element))) return element;
        } catch (NoSuchMethodException ignored) { }
        for (Object child : children(element)) {
            Object found = find(child, label);
            if (found != null) return found;
        }
        return null;
    }

    static Object dropdown(Object element) throws Exception {
        if (element.getClass().getSimpleName().equals("GuiDropdownMenu")) return element;
        for (Object child : children(element)) {
            Object found = dropdown(child);
            if (found != null) return found;
        }
        return null;
    }

    private static Collection<?> children(Object element) throws Exception {
        try {
            Method method = element.getClass().getMethod("getChildren");
            method.setAccessible(true);
            return (Collection<?>) method.invoke(element);
        } catch (NoSuchMethodException ignored) {
            return java.util.Collections.emptyList();
        }
    }

    static void click(Object button) throws Exception {
        require(button != null, "Button missing");
        Class<?> type = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.function.Click");
        Object click = type.getConstructor(int.class, int.class, int.class, int.class).newInstance(0, 0, 0, 0);
        button.getClass().getMethod("onClick", type).invoke(button, click);
    }

    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
