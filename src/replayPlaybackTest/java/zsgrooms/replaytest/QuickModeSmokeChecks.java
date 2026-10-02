package zsgrooms.replaytest;

import com.replaymod.replay.ReplayHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import zsgrooms.modid.ZsgRooms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Real ReplayMod index, mode transitions and HUD snapshots across dimensions and resets. */
final class QuickModeSmokeChecks {
    private int step, index;
    private long next, started;
    private Object playback;
    private int switchTime;

    boolean tick(ReplayHandler handler, MinecraftClient client, Object controls, List<Object> frames, List<String> terrain) throws Exception {
        if (System.nanoTime() < next) return false;
        if (step == 0) {
            require(!handler.isQuickMode(), "Quick Mode enabled by default");
            playback = get(controls, "playback");
            handler.getReplaySender().setReplaySpeed(0);
            get(controls, "camera").getClass().getMethod("setSelected", int.class).invoke(get(controls, "camera"), 4);
            invoke(get(controls, "details"), "closeInventory");
            handler.getOverlay().setMouseVisible(true);
            invoke(controls, "update");
            switchTime = handler.getReplaySender().currentTimeStamp();
            ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
            toggle(client);
            started = System.nanoTime();
        } else if (step == 1) {
            require(System.nanoTime() - started < 120000000000L, "Quick Mode indexing timed out");
            if ((Boolean) invoke(playback, "busy")) return false;
            require(handler.isQuickMode(), "Quick Mode did not enable: " + invoke(playback, "status"));
            require(handler.getReplaySender().paused(), "Enabling Quick Mode unpaused playback");
            require(Math.abs(handler.getReplaySender().currentTimeStamp() - switchTime) <= 50, "Enabling moved replay time");
            capture(client, "viewer-quick-settings.png");
            client.currentScreen.onClose();
            client.options.perspective = 1;
            jump(handler, frames.get(frames.size() - 1));
        } else if (step == 2) {
            // Walk backwards through every world, then forwards to the last one again.
            int frameIndex = frames.size() - 1 - index;
            Object state = get(get(controls, "detailHud"), "state");
            Object frame = get(get(controls, "detailHud"), "last");
            require(state != null && frame != null, "Quick Mode lost detailed HUD at world " + frameIndex);
            require(java.util.Arrays.equals((byte[]) get(frame, "payload"), (byte[]) get(frames.get(frameIndex), "payload")),
                    "Quick Mode displayed wrong world inventory " + frameIndex);
            require(!handler.isCameraView(), "Quick Mode lost player follow");
            require(terrain(client).equals(terrain.get(frameIndex)), "Quick Mode changed terrain at world " + frameIndex);
            checkHands(handler, client, controls, state);
            capture(client, "viewer-quick-details.png");
            if (++index < frames.size()) {
                jump(handler, frames.get(frames.size() - 1 - index));
                next = System.nanoTime() + 1500000000L;
                return false;
            }
            jump(handler, frames.get(frames.size() - 1));
        } else if (step == 3) {
            require(get(get(controls, "detailHud"), "state") != null, "Quick forward seek lost HUD");
            profileHands(handler, controls, frames);
            client.options.perspective = 0;
            invoke(controls, "seekTo", int.class, handler.getReplaySender().currentTimeStamp() - 5000);
            require(handler.getReplaySender().paused(), "Quick skip unpaused playback");
            switchTime = handler.getReplaySender().currentTimeStamp();
            ViewerSmokeChecks.click(ViewerSmokeChecks.find(handler.getOverlay(), "Analysis"));
            toggle(client);
        } else if (step == 4) {
            if ((Boolean) invoke(playback, "busy")) return false;
            require(!handler.isQuickMode(), "Could not return to normal playback");
            require(handler.getReplaySender().paused(), "Disabling Quick Mode unpaused playback");
            require(Math.abs(handler.getReplaySender().currentTimeStamp() - switchTime) <= 50, "Disabling moved replay time");
            client.currentScreen.onClose();
            jump(handler, frames.get(0));
        } else if (step == 5) {
            require(get(get(controls, "detailHud"), "state") != null, "Normal mode HUD not restored");
            require(get(get(controls,"quickHands"),"state") == null, "Normal mode retained held-item overlay");
            handler.getReplaySender().setReplaySpeed(2);
            invoke(playback, "setQuick", boolean.class, true);
        } else if (step == 6) {
            if ((Boolean) invoke(playback, "busy")) return false;
            require(handler.isQuickMode() && handler.getReplaySender().getReplaySpeed() == 2, "Quick mode lost playback speed");
            invoke(controls, "seekTo", int.class, handler.getReplaySender().currentTimeStamp() + 5000);
            require(!handler.getReplaySender().paused() && handler.getReplaySender().getReplaySpeed() == 2,
                    "Quick skip lost playback speed");
            invoke(playback, "setQuick", boolean.class, false);
        } else {
            if ((Boolean) invoke(playback, "busy")) return false;
            require(!handler.isQuickMode() && handler.getReplaySender().getReplaySpeed() == 2, "Normal mode lost playback speed");
            handler.getReplaySender().setReplaySpeed(0);
            ZsgRooms.LOGGER.info("[ReplayQuickSmoke] PASS: UI toggle, index, pause/speed, backwards {} worlds, forward seek, detailed follow and return to normal", frames.size());
            return true;
        }
        step++;
        next = System.nanoTime() + 1500000000L;
        return false;
    }

    private static void checkHands(ReplayHandler handler, MinecraftClient client, Object controls, Object state) throws Exception {
        PlayerEntity player = (PlayerEntity)client.getCameraEntity();
        ItemStack[] items = (ItemStack[])get(state,"items");
        require(ItemStack.areEqual(player.getMainHandStack(), items[(Integer)get(state,"selected")]), "Quick main hand mismatch");
        require(ItemStack.areEqual(player.getOffHandStack(), items[40]), "Quick offhand mismatch");
        Object hands = get(controls,"quickHands"), cached = get(hands,"state");
        Object frame = get(hands,"last");
        Method lookup = hands.getClass().getDeclaredMethod("get",PlayerEntity.class,EquipmentSlot.class,int.class);
        lookup.setAccessible(true);
        int time = handler.getReplaySender().currentTimeStamp();
        require(lookup.invoke(hands,player,EquipmentSlot.MAINHAND,time + 1) == null, "Stale seek item leaked");
        require(lookup.invoke(hands,handler.getCameraEntity(),EquipmentSlot.MAINHAND,time) == null, "Item leaked onto camera");
        require(lookup.invoke(hands,player,EquipmentSlot.HEAD,time) == null, "Overlay changed armor");
        Method update = hands.getClass().getDeclaredMethod("update",PlayerEntity.class,frame.getClass(),int.class);
        update.setAccessible(true);
        String inventory = player.inventory.serialize(new net.minecraft.nbt.ListTag()).toString();
        update.invoke(hands,player,null,time);
        require(lookup.invoke(hands,player,EquipmentSlot.MAINHAND,time) == null, "Missing HUD retained held item");
        update.invoke(hands,player,frame,time);
        Object restored = get(hands,"state");
        for (int i=0;i<100;i++) update.invoke(hands,player,frame,time);
        require(get(hands,"state") == restored, "Paused frame repeatedly decoded items");
        require(inventory.equals(player.inventory.serialize(new net.minecraft.nbt.ListTag()).toString()), "Overlay mutated inventory");
        require(cached != null, "Quick held items never decoded");
        java.lang.reflect.Constructor<?> constructor = frame.getClass().getDeclaredConstructor(int.class,int.class,int.class,byte[].class);
        constructor.setAccessible(true);
        byte[] payload = ((byte[])get(frame,"payload")).clone();
        // selectedSlot follows eight four-byte HUD values; test both a changed and empty slot.
        for (int slot : new int[]{1,8}) {
            payload[32] = (byte)slot;
            Object changed = constructor.newInstance(time,get(frame,"world"),player.getEntityId(),payload.clone());
            update.invoke(hands,player,changed,time);
            require(ItemStack.areEqual((ItemStack)lookup.invoke(hands,player,EquipmentSlot.MAINHAND,time),items[slot]),
                    "Slot switch/empty hand did not replace previous item");
        }
        Object invalid = constructor.newInstance(time,get(frame,"world"),player.getEntityId(),new byte[]{1});
        update.invoke(hands,player,invalid,time);
        require(lookup.invoke(hands,player,EquipmentSlot.OFFHAND,time) == null, "Malformed snapshot retained offhand");
        update.invoke(hands,player,frame,time);
    }

    private static void profileHands(ReplayHandler handler, Object controls, List<Object> frames) throws Exception {
        Object index = get(controls,"recordingIndex");
        Field hud = index.getClass().getDeclaredField("hud"); hud.setAccessible(true);
        Object track = hud.get(index);
        double[] millis = new double[2];
        try {
            // Alternate order to reduce warmup bias. This test-only switch removes the HUD overlay.
            for (int round=0;round<4;round++) for (int mode=0;mode<2;mode++) {
                int enabled = (mode + round) % 2;
                hud.set(index,enabled == 1 ? track : null);
                for (Object frame : frames) {
                    long start = System.nanoTime();
                    jump(handler,frame);
                    invoke(controls,"update");
                    millis[enabled] += (System.nanoTime()-start)/1e6;
                }
            }
        } finally { hud.set(index,track); invoke(controls,"update"); }
        ZsgRooms.LOGGER.info("[ReplayQuickHands] {} seeks each: overlay absent {} ms, present {} ms (includes viewer update)",
                frames.size()*4,millis[0],millis[1]);
    }

    private static void toggle(MinecraftClient client) {
        for (net.minecraft.client.gui.Element element : client.currentScreen.children()) {
            if (element instanceof CheckboxWidget && ((CheckboxWidget) element).getMessage().getString().equals("Quick Mode (experimental)")) {
                ((CheckboxWidget) element).onPress();
                return;
            }
        }
        throw new IllegalStateException("Missing Quick Mode setting");
    }
    static String terrain(MinecraftClient client) {
        net.minecraft.util.math.BlockPos origin = client.getCameraEntity().getBlockPos();
        StringBuilder blocks = new StringBuilder();
        for (int y = -4; y <= 2; y++) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            blocks.append(net.minecraft.block.Block.getRawIdFromState(client.world.getBlockState(origin.add(x, y, z)))).append(',');
        }
        return blocks.toString();
    }
    private static void jump(ReplayHandler handler, Object frame) throws Exception {
        AudioSmokeChecks.seek(handler, (Integer) get(frame, "time"));
        handler.getReplaySender().setReplaySpeed(0);
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static Object invoke(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }
    private static Object invoke(Object object, String name, Class<?> type, Object value) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(object, value);
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
