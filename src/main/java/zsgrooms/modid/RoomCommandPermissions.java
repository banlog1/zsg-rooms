package zsgrooms.modid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** A launch-scoped rule, bound to the server instance rather than permanently to its save. */
public final class RoomCommandPermissions {
    private static final Map<MinecraftServer, Boolean> SERVERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Launch launch;

    private RoomCommandPermissions() { }

    public static void duringCreation(String directory, boolean allowCheats, Runnable create) {
        Launch previous = launch;
        launch = new Launch(directory, allowCheats);
        try {
            // Minecraft waits for server startup here; SERVER_STARTING runs on the server thread.
            create.run();
        } finally {
            launch = previous;
        }
    }

    public static void bind(MinecraftServer server) {
        Launch pending = launch;
        if (pending != null && !server.isDedicated()
                && pending.directory.equals(server.getSavePath(WorldSavePath.ROOT).normalize().getFileName().toString())) {
            SERVERS.put(server, pending.allowCheats);
            server.getPlayerManager().setCheatsAllowed(pending.allowCheats);
        }
    }

    public static void stop(MinecraftServer server) { SERVERS.remove(server); }

    public static boolean forbidsCheats(MinecraftServer server) {
        return server != null && Boolean.FALSE.equals(SERVERS.get(server));
    }

    public static boolean allowed(MinecraftServer server, boolean vanilla) {
        Boolean policy = SERVERS.get(server);
        return policy == null ? vanilla : policy;
    }

    private static final class Launch {
        final String directory;
        final boolean allowCheats;

        Launch(String directory, boolean allowCheats) {
            this.directory = directory;
            this.allowCheats = allowCheats;
        }
    }
}
