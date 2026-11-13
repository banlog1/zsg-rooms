package zsgrooms.modid.settings;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Must remain independent of Minecraft classes: this runs before the game is loaded. */
public final class SettingsImportStartup implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT) return;
        java.nio.file.Path state = loader.getGameDir().resolve("config/zsg-rooms-import");
        if (!java.nio.file.Files.exists(state.resolve("pending.json")) && !java.nio.file.Files.exists(state.resolve("recovery.json"))) return;
        try { new SettingsImportStore(loader.getGameDir()).applyAtStartup(); }
        catch (java.io.IOException | RuntimeException e) {
            throw new IllegalStateException("Settings import recovery needs attention. Backups are in config/zsg-rooms-import; do not delete them.", e);
        }
    }
}
