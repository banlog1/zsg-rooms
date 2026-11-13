package zsgrooms.modid.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;

/** Opens onboarding only after the title screen and resource loading are ready. */
public final class TutorialOnboarding {
    private static TutorialPreferences preferences;
    private static boolean offered;

    private TutorialOnboarding() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TutorialOnboarding::tick);
    }

    static TutorialPreferences preferences() {
        if (preferences == null) preferences = new TutorialPreferences(
                FabricLoader.getInstance().getConfigDir().resolve("zsg-rooms-tutorial.properties"));
        return preferences;
    }

    static void tick(MinecraftClient client) {
        if (offered || client.world != null || client.getOverlay() != null
                || !(client.currentScreen instanceof TitleScreen)) return;
        offered = true;
        if (preferences().shouldPrompt()) open(client.currentScreen);
    }

    public static void open(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) client.openScreen(new TutorialScreen(parent, preferences()));
    }
}
