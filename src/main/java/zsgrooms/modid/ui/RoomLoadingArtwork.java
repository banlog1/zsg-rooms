package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import zsgrooms.modid.ZsgSeedBridge;

/** One-shot launch context, consumed by the next world-generation screen only. */
public final class RoomLoadingArtwork {
    public interface ScreenState {
        boolean zsgRooms$hasLoadingArtwork();
    }
    private static String pendingSeed;
    private final SeedVisualType type;
    private final int variant;
    private boolean checked;
    private Identifier texture;

    private RoomLoadingArtwork(String seed) {
        type = SeedVisualType.forFilter(ZsgSeedBridge.resolveStructure(seed));
        variant = ZsgSeedBridge.extractMinecraftSeed(seed).hashCode();
    }

    public static void prepare(String seed) {
        pendingSeed = seed;
    }

    public static void cancel() {
        pendingSeed = null;
    }

    public static RoomLoadingArtwork capture() {
        String seed = pendingSeed;
        pendingSeed = null;
        return seed == null ? null : new RoomLoadingArtwork(seed);
    }

    public boolean available(MinecraftClient client) {
        if (!RoomUiPreferences.areLoadingImagesEnabled()) return false;
        if (!checked) {
            checked = true;
            for (int i = 0; i < type.images.length; i++) {
                String name = type.images[Math.floorMod(variant + i, type.images.length)];
                Identifier candidate = new Identifier("zsg-rooms", "textures/gui/loading/" + name + ".png");
                if (client.getResourceManager().containsResource(candidate)) {
                    texture = candidate;
                    break;
                }
            }
        }
        return texture != null;
    }

    public void render(MatrixStack matrices, MinecraftClient client, int width, int height) {
        float scale = Math.max(width / 1280.0F, height / 720.0F);
        int imageWidth = (int) Math.ceil(1280 * scale);
        int imageHeight = (int) Math.ceil(720 * scale);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.color4f(1, 1, 1, 1);
        client.getTextureManager().bindTexture(texture);
        DrawableHelper.drawTexture(matrices, (width - imageWidth) / 2, (height - imageHeight) / 2,
                imageWidth, imageHeight, 0, 0, 1280, 720, 1280, 720);
    }
}
