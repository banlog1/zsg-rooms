package zsgrooms.modid.ui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerHeadRenderer {
    private static final Map<UUID, Identifier> SKINS = new ConcurrentHashMap<UUID, Identifier>();
    private static final Set<UUID> REQUESTED = ConcurrentHashMap.newKeySet();

    private PlayerHeadRenderer() {
    }

    static void draw(MatrixStack matrices, MinecraftClient client, String playerName, String uuidText,
                     int x, int y, int size) {
        UUID uuid = parseUuid(uuidText);
        UUID displayUuid = uuid == null ? offlineUuid(playerName) : uuid;
        Identifier texture = SKINS.get(displayUuid);
        if (texture == null) {
            texture = DefaultSkinHelper.getTexture(displayUuid);
        }

        if (uuid != null && client != null && REQUESTED.add(uuid)) {
            GameProfile profile = new GameProfile(uuid, playerName == null ? "player" : playerName);
            client.getSkinProvider().loadSkin(profile, (type, identifier, profileTexture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    SKINS.put(uuid, identifier);
                }
            }, true);
        }

        client.getTextureManager().bindTexture(texture);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DrawableHelper.drawTexture(matrices, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        DrawableHelper.drawTexture(matrices, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() == 32) {
            normalized = normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-"
                    + normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-"
                    + normalized.substring(20);
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID offlineUuid(String playerName) {
        String name = playerName == null ? "player" : playerName;
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
