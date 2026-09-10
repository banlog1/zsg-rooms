package zsgrooms.replaytest;

import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.core.ReplayMod;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplaySender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/** Actual keyboard dispatch and contextual hotbar checks in the disposable test instance. */
final class ShortcutSmokeChecks {
    static void outsideReplay(MinecraftClient client) {
        KeyBinding.updateKeysByCode();
        for (int slot = 0; slot < 4; slot++) {
            KeyBinding hotbar = client.options.keysHotbar[slot];
            while (hotbar.wasPressed()) { }
            KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_1 + slot));
            require(hotbar.wasPressed(), "Hotbar slot " + (slot + 1) + " stolen outside replay");
        }
        require(!binding("back").keyBinding.wasPressed(), "Back shortcut active outside replay");
        require(!binding("forward").keyBinding.wasPressed(), "Forward shortcut active outside replay");
    }

    static void run(ReplayHandler handler, MinecraftClient client) throws Exception {
        ReplaySender sender = handler.getReplaySender();
        sender.setReplaySpeed(0);
        handler.doJump(15000, true);
        sender.setReplaySpeed(0);
        handler.getOverlay().setMouseVisible(false);
        KeyBinding.updateKeysByCode();
        require(binding("back").keyBinding.matchesKey(GLFW.GLFW_KEY_3, 0), "Back default is not 3");
        require(binding("forward").keyBinding.matchesKey(GLFW.GLFW_KEY_4, 0), "Forward default is not 4");

        int start = sender.currentTimeStamp();
        key(client, GLFW.GLFW_KEY_4);
        require(Math.abs(sender.currentTimeStamp() - start - 5000) < 100, "4 did not seek forward once");
        require(sender.paused(), "Shortcut resumed paused playback");
        key(client, GLFW.GLFW_KEY_3);
        require(Math.abs(sender.currentTimeStamp() - start) < 100, "3 did not seek backward once");
        key(client, GLFW.GLFW_KEY_1);
        key(client, GLFW.GLFW_KEY_2);
        require(Math.abs(sender.currentTimeStamp() - start) < 100, "Reserved number keys sought replay");

        handler.getOverlay().setMouseVisible(true);
        key(client, GLFW.GLFW_KEY_4);
        require(Math.abs(sender.currentTimeStamp() - start - 5000) < 100, "Shortcut did not work with controls visible");
        int beforeChat = sender.currentTimeStamp();
        client.openScreen(new ChatScreen(""));
        key(client, GLFW.GLFW_KEY_3);
        binding("back").trigger();
        require(sender.currentTimeStamp() == beforeChat, "Typing context triggered seek");
        client.openScreen(null);
        handler.getOverlay().setMouseVisible(false);

        KeyBinding forward = binding("forward").keyBinding;
        forward.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_5));
        KeyBinding.updateKeysByCode();
        try {
            key(client, GLFW.GLFW_KEY_4);
            require(sender.currentTimeStamp() == beforeChat, "Old key still bound after rebinding");
            key(client, GLFW.GLFW_KEY_5);
            require(Math.abs(sender.currentTimeStamp() - beforeChat - 5000) < 100, "Rebound forward key failed");
        } finally {
            forward.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_4));
            KeyBinding.updateKeysByCode();
            sender.setReplaySpeed(1);
            handler.getOverlay().setMouseVisible(true);
        }
    }

    static void assertEditorBlocked(Object handler) {
        ReplaySender sender = ((ReplayHandler) handler).getReplaySender();
        double speed = sender.getReplaySpeed();
        sender.setReplaySpeed(0);
        int time = sender.currentTimeStamp();
        binding("back").trigger();
        binding("forward").trigger();
        require(sender.currentTimeStamp() == time, "Compact shortcuts affected camera-path editor");
        sender.setReplaySpeed(speed);
    }

    private static KeyBindingRegistry.Binding binding(String direction) {
        KeyBindingRegistry.Binding binding = ReplayMod.instance.getKeyBindingRegistry().getBindings()
                .get("replaymod.input.zsg_skip_" + direction);
        require(binding != null, "Shortcut not registered: " + direction);
        return binding;
    }

    private static void key(MinecraftClient client, int key) throws Exception {
        Method onKey = client.keyboard.getClass().getDeclaredMethod("onKey", long.class, int.class, int.class, int.class, int.class);
        onKey.setAccessible(true);
        onKey.invoke(client.keyboard, client.getWindow().getHandle(), key, 0, GLFW.GLFW_PRESS, 0);
        onKey.invoke(client.keyboard, client.getWindow().getHandle(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
