package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import java.lang.reflect.Field;

/** Test-only GPU pixel comparison with the original map-then-cover renderer. */
final class LoadingBackingComparison extends Screen {
    private boolean complete;
    private Throwable failure;

    LoadingBackingComparison() { super(new LiteralText("Loading renderer comparison")); }

    @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
        if (complete) return;
        try {
            Field field = LevelLoadingScreen.class.getDeclaredField("STATUS_TO_COLOR");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Object2IntMap<ChunkStatus> colors = (Object2IntMap<ChunkStatus>) field.get(null);
            ChunkStatus[] statuses = colors.keySet().toArray(new ChunkStatus[0]);
            for (int radius : new int[] {0, 1, 11, 24}) {
                WorldGenerationProgressTracker tracker = new WorldGenerationProgressTracker(radius);
                tracker.start(); tracker.start(new ChunkPos(0, 0));
                int half = tracker.getSize() / 2;
                for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) {
                    int index = Math.floorMod(x * 7 + z * 3, statuses.length + 1);
                    if (index < statuses.length) tracker.setChunkStatus(new ChunkPos(x, z), statuses[index]);
                }
                renderBackground(matrices);
                RenderSystem.disableDepthTest();
                int first = width / 4, second = first + width / 2, cy = height / 2;
                int mapSize = tracker.getSize() * 2, size = LoadingLogoLayout.logoSize(mapSize);
                LevelLoadingScreen.drawChunkMap(matrices, tracker, first, cy, 2, 0);
                fill(matrices, first - size / 2, cy - size / 2,
                        first - size / 2 + size, cy - size / 2 + size, 0xFF101010);
                RoomLoadingIndicator.drawBacking(matrices, tracker, colors, second, cy);
                double scale = client.getWindow().getScaleFactor();
                try (NativeImage pixels = ScreenshotUtils.takeScreenshot(client.getWindow().getFramebufferWidth(),
                        client.getWindow().getFramebufferHeight(), client.getFramebuffer())) {
                    int offset = (int) ((second - first) * scale);
                    for (int x = (int) ((first - mapSize / 2) * scale); x < (int) ((first + mapSize / 2) * scale); x++)
                        for (int y = (int) ((cy - mapSize / 2) * scale); y < (int) ((cy + mapSize / 2) * scale); y++)
                            if (pixels.getPixelColor(x, y) != pixels.getPixelColor(x + offset, y))
                                throw new AssertionError("Backing pixels differ at radius " + radius + ", " + x + ", " + y);
                }
            }
        } catch (Throwable error) { failure = error; }
        complete = true;
    }

    void verify() {
        if (!complete) throw new AssertionError("Comparison screen did not render");
        if (failure != null) throw new AssertionError("Loading renderer equivalence failed", failure);
    }
}
