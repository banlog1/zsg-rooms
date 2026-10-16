package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.text.LiteralText;

/** The recorder can be installed without the optional ReplayMod viewer. */
public final class ReplayBrowserLauncher {
    private ReplayBrowserLauncher() {}

    public static void open(Screen parent) {
        try {
            Class<?> browser = Class.forName("zsgrooms.replayviewer.ReplayBrowserScreen");
            Screen screen = (Screen) browser.getConstructor(Screen.class).newInstance(parent);
            MinecraftClient.getInstance().openScreen(screen);
        } catch (ReflectiveOperationException | LinkageError error) {
            MinecraftClient.getInstance().openScreen(new DisconnectedScreen(parent, "Replay viewer unavailable",
                    new LiteralText("Install ZSG Replay Viewer and ReplayMod to play recordings. Recording files have not been changed.")));
        }
    }
}
