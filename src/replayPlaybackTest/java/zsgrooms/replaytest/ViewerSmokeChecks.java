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
    private double directSpeed;
    private double classicSpeed;
    private Object milestoneBar;
    private int milestoneTime;
    private int milestoneX;
    private int milestoneY;
    private boolean running;
    private int dimensionTime, resetTime;
    private final AnalysisSmokeChecks analysisChecks = new AnalysisSmokeChecks();

    boolean complete() { return step > 31; }

    void tick(Object handler, MinecraftClient client) throws Exception {
        if (running) return;
        running = true;
        try { advance(handler, client); }
        finally { running = false; }
    }

    private void advance(Object handler, MinecraftClient client) throws Exception {
        if (complete() || System.nanoTime() < next) return;
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.ProgressScreen
                || client.currentScreen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen) return;
        Object overlay = handler.getClass().getMethod("getOverlay").invoke(handler);
        next = System.nanoTime() + 1000000000L;
        switch (step++) {
            case 0:
                if (!fixtureTimes()) { step--; break; }
                require(hideChat(), "Replay chat was not hidden by default");
                client.options.guiScale = 2;
                client.onResolutionChanged();
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, false);
                next += 4000000000L;
                break;
            case 1:
                require(find(overlay, "Analysis") == null, "Auto-hide left its buttons interactive");
                screenshot(client, "viewer-hidden.png");
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, true);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                GLFW.glfwSetCursorPos(client.getWindow().getHandle(), 100, 620);
                break;
            case 2:
                require(find(overlay, "Analysis") != null, "Cursor did not reveal controls");
                screenshot(client, "viewer-wide.png");
                click(find(overlay, "Analysis"));
                Method display = client.currentScreen.getClass().getDeclaredMethod("openDisplayTab");
                display.setAccessible(true); display.invoke(client.currentScreen);
                net.minecraft.client.gui.widget.CheckboxWidget chat = (net.minecraft.client.gui.widget.CheckboxWidget)
                        client.currentScreen.children().stream().filter(e -> e instanceof net.minecraft.client.gui.widget.CheckboxWidget
                        && ((net.minecraft.client.gui.widget.CheckboxWidget) e).getMessage().getString().equals("Show chat")).findFirst().get();
                chat.onPress();
                require(!hideChat(), "Chat toggle did not reveal chat");
                chat.onPress();
                require(hideChat(), "Chat toggle did not hide chat again");
                ((net.minecraft.client.gui.widget.ButtonWidget) field(client.currentScreen, "pathEditor")).onPress();
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
                require(find(overlay, "Analysis") != null, "Controls missing after seek");
                Object pausedSender = handler.getClass().getMethod("getReplaySender").invoke(handler);
                pausedSender.getClass().getMethod("setReplaySpeed", double.class).invoke(pausedSender, 0.0D);
                click(find(overlay, "<<"));
                require((Boolean) pausedSender.getClass().getMethod("paused").invoke(pausedSender), "Seek resumed paused playback");
                pausedSender.getClass().getMethod("setReplaySpeed", double.class).invoke(pausedSender, 1.0D);
                Object camera = field(controls(), "camera");
                camera.getClass().getMethod("setOpened", boolean.class).invoke(camera, true);
                break;
            case 9:
                Object currentControls = controls();
                Object autoHide = field(currentControls, "autoHide");
                autoHide.getClass().getMethod("setChecked", boolean.class).invoke(autoHide, false);
                Object openCamera = field(currentControls, "camera");
                if ((Boolean) openCamera.getClass().getMethod("isOpened").invoke(openCamera)) {
                    screenshot(client, "viewer-dropdown.png");
                    openCamera.getClass().getMethod("setOpened", boolean.class).invoke(openCamera, false);
                    step--; break;
                }
                Object direct = controller(handler);
                require(direct.getClass().getSimpleName().equals("VanillaCameraController"), "Direct camera did not resume after menu close");
                direct.getClass().getMethod("increaseSpeed").invoke(direct);
                direct.getClass().getMethod("increaseSpeed").invoke(direct);
                directSpeed = ((Number) direct.getClass().getMethod("zsgViewer$getSpeed").invoke(direct)).doubleValue();
                previousCamera = cameraEntity(handler);
                jump(handler, dimensionTime);
                break;
            case 10:
                require(((Number) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler))).doubleValue() == directSpeed,
                        "Direct speed lost on dimension change");
                previousCamera = cameraEntity(handler);
                jump(handler, resetTime);
                break;
            case 11:
                require(cameraEntity(handler) != previousCamera, "Fixture did not recreate camera on world reset");
                require(((Number) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler))).doubleValue() == directSpeed,
                        "Direct speed lost on world reset");
                dropdown(overlay).getClass().getMethod("setSelected", int.class).invoke(dropdown(overlay), 1);
                break;
            case 12:
                Object classic = controller(handler);
                classic.getClass().getMethod("increaseSpeed").invoke(classic);
                classicSpeed = (Double) classic.getClass().getMethod("zsgViewer$getSpeed").invoke(classic);
                jump(handler, 3000);
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
                jump(handler, dimensionTime);
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
                require(((Number) controller(handler).getClass().getMethod("zsgViewer$getSpeed").invoke(controller(handler))).doubleValue() == directSpeed,
                        "Direct speed lost after changing camera modes");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                break;
            case 17:
                screenshot(client, "viewer-milestones.png");
                java.lang.reflect.Field viewerInstance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
                viewerInstance.setAccessible(true);
                Object viewerControls = field(viewerInstance.get(null), "controls");
                milestoneBar = field(viewerControls, "milestones");
                Object group = ((java.util.List<?>) field(field(milestoneBar, "placement"), "groups")).get(0);
                Object first = ((java.util.List<?>) field(group, "entries")).get(0);
                milestoneTime = (Integer) field(first, "time");
                milestoneX = 8 + ((Integer) field(group, "left") + (Integer) field(group, "right")) / 2;
                milestoneY = client.getWindow().getScaledHeight() - 8 - 32 + 8;
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
                Object selectedGroup = ((java.util.List<?>) field(field(milestoneBar, "placement"), "groups")).get(0);
                if (((java.util.List<?>) field(selectedGroup, "entries")).size() > 1) {
                    Object event = find(overlay, (String) callEventText(firstEntry(selectedGroup)));
                    require(event != null, "Grouped milestone chooser did not expose its first event");
                    click(event);
                }
                require((Boolean) markerSender.getClass().getMethod("paused").invoke(markerSender), "Milestone seek resumed playback");
                int markerTime = (Integer) markerSender.getClass().getMethod("currentTimeStamp").invoke(markerSender);
                require(Math.abs(markerTime - milestoneTime) < 100, "Milestone click sought wrong time");
                markerSender.getClass().getMethod("setReplaySpeed", double.class).invoke(markerSender, 1.0D);
                ShortcutSmokeChecks.run((com.replaymod.replay.ReplayHandler) handler, client);
                ZsgRooms.LOGGER.info("[ReplayViewerSmoke] PASS: auto-hide, chat, editor, resize, seeking, milestones, far follow, camera speed across dimension/reset/mode changes, 3/4 shortcuts and rebinding");
                break;
            case 19: case 20: case 21: case 22: case 23: case 24:
                analysisChecks.tick(handler, client);
                break;
            case 25:
                client.openScreen(null);
                overlay.getClass().getMethod("setMouseVisible", boolean.class).invoke(overlay, true);
                if (find(overlay, "Analysis") == null) { step--; break; }
                click(find(overlay, "Analysis"));
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                break;
            case 26:
                screenshot(client, "viewer-settings-playback-small.png");
                Method displayTab = client.currentScreen.getClass().getDeclaredMethod("openDisplayTab");
                displayTab.setAccessible(true); displayTab.invoke(client.currentScreen);
                break;
            case 27:
                screenshot(client, "viewer-settings-display-small.png");
                for (net.minecraft.client.gui.Element element : client.currentScreen.children()) {
                    if (element instanceof net.minecraft.client.gui.widget.ButtonWidget
                            && ((net.minecraft.client.gui.widget.ButtonWidget) element).getMessage().getString().equals("Analysis")) {
                        ((net.minecraft.client.gui.widget.ButtonWidget) element).onPress(); break;
                    }
                }
                break;
            case 28:
                screenshot(client, "viewer-settings-analysis-small.png");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                break;
            case 29:
                screenshot(client, "viewer-settings-analysis-wide.png");
                client.currentScreen.onClose();
                ZsgRooms.LOGGER.info("[ReplayViewerSmoke] Grouped settings screenshots captured at 320x240 and 640x360 GUI sizes");
                previewMilestones(handler);
                jump(handler, resetTime);
                ((com.replaymod.replay.ReplayHandler) handler).getReplaySender().setReplaySpeed(0);
                break;
            case 30:
                screenshot(client, "viewer-splits-icons-wide.png");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                break;
            case 31:
                screenshot(client, "viewer-splits-icons-small.png");
                break;
            default:
                break;
        }
    }

    private static boolean hideChat() throws Exception {
        return (Boolean) Class.forName("zsgrooms.replayviewer.ReplayViewer").getMethod("shouldHideChat").invoke(null);
    }

    private static Object firstEntry(Object group) throws Exception { return ((java.util.List<?>) field(group, "entries")).get(0); }
    private static Object callEventText(Object entry) throws Exception {
        Method method = Class.forName("zsgrooms.replayviewer.MilestonePlacement").getDeclaredMethod("eventText", entry.getClass());
        method.setAccessible(true); return method.invoke(null, entry);
    }
    private static Object controls() throws Exception {
        java.lang.reflect.Field instance = Class.forName("zsgrooms.replayviewer.ReplayViewer").getDeclaredField("instance");
        instance.setAccessible(true); return field(instance.get(null), "controls");
    }

    private boolean fixtureTimes() throws Exception {
        Object index = field(controls(), "recordingIndex");
        Object recording = field(index, "recording");
        if (recording == null) return false;
        java.util.List<?> intervals = (java.util.List<?>) field(recording, "intervals");
        require(!intervals.isEmpty(), "Fixture has no world coverage");
        int firstWorld = (Integer) field(intervals.get(0), "world");
        for (int i = 1; i < intervals.size(); i++) {
            Object interval = intervals.get(i);
            int world = (Integer) field(interval, "world");
            int start = (Integer) field(interval, "start"), end = (Integer) field(interval, "end");
            if (end - start < 1000) continue;
            int time = start + Math.min(1500, (end - start) / 2);
            if (world == firstWorld && dimensionTime == 0) dimensionTime = time;
            if (world != firstWorld && resetTime == 0) resetTime = time;
        }
        require(dimensionTime > 0 && resetTime > 0, "Fixture needs a dimension change and second world");
        return true;
    }

    private static void previewMilestones(Object handler) throws Exception {
        Class<?> kind = Class.forName("zsgrooms.replayviewer.Milestones$Kind");
        Class<?> entry = Class.forName("zsgrooms.replayviewer.Milestones$Entry");
        java.lang.reflect.Constructor<?> constructor = entry.getDeclaredConstructor(kind, int.class, int.class);
        constructor.setAccessible(true);
        int duration = (Integer) handler.getClass().getMethod("getReplayDuration").invoke(handler);
        java.util.List<Object> entries = new java.util.ArrayList<>();
        Object[] kinds = kind.getEnumConstants();
        for (int index = 0; index < kinds.length; index++) entries.add(constructor.newInstance(kinds[index], duration * (index + 1) / (kinds.length + 1), 1));
        Object milestones = field(controls(), "milestoneIndex");
        java.lang.reflect.Field published = milestones.getClass().getDeclaredField("entries");
        published.setAccessible(true); published.set(milestones, java.util.Collections.unmodifiableList(entries));
        ZsgRooms.LOGGER.info("[ReplayViewerSmoke] Synthetic stage icon preview; recording file unchanged");
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
        sender.getClass().getMethod("setReplaySpeed", double.class).invoke(sender, 0.0D);
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
        if (Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu").isInstance(element)) return element;
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
