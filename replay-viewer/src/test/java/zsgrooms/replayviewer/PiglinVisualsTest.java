// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.PiglinEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PiglinVisualsTest {
    @Test void onlyLivingAdultsAdmiringAnOffhandGoldIngotAreBartering() {
        for (PiglinEntity.Activity activity : PiglinEntity.Activity.values()) {
            for (boolean alive : new boolean[]{false, true}) {
                for (boolean baby : new boolean[]{false, true}) {
                    for (boolean goldInOffHand : new boolean[]{false, true}) {
                        assertEquals(alive && !baby && goldInOffHand
                                        && activity == PiglinEntity.Activity.ADMIRING_ITEM,
                                PiglinVisuals.bartering(alive, baby, goldInOffHand, activity),
                                activity + ": alive=" + alive + ", baby=" + baby + ", ingot=" + goldInOffHand);
                    }
                }
            }
        }
        assertFalse(PiglinVisuals.bartering(true, false, true, null));
        assertFalse(PiglinVisuals.bartering(null));
    }

    @Test void countTextDistinguishesUnavailableFromZeroAndClampsNegativeValues() {
        assertEquals("--", PiglinVisuals.counterText(31, false));
        assertEquals("--", PiglinVisuals.counterText(-1, false));
        assertEquals("0", PiglinVisuals.counterText(0, true));
        assertEquals("0", PiglinVisuals.counterText(-1, true));
        assertEquals("12", PiglinVisuals.counterText(12, true));
        assertEquals("2147483647", PiglinVisuals.counterText(Integer.MAX_VALUE, true));
    }

    @Test void countColorsAreOpaqueAndDistinguishPositiveEmptyAndUnavailable() {
        Set<Integer> colors = new HashSet<>(Arrays.asList(
                PiglinVisuals.counterColor(12, true), PiglinVisuals.counterColor(0, true),
                PiglinVisuals.counterColor(12, false)));
        assertEquals(3, colors.size());
        for (int color : colors) assertEquals(0xFF, color >>> 24);
        assertEquals(PiglinVisuals.counterColor(0, true), PiglinVisuals.counterColor(-1, true));
        assertEquals(PiglinVisuals.counterColor(0, false), PiglinVisuals.counterColor(12, false));
        int positive = PiglinVisuals.counterColor(1, true);
        assertTrue((positive >> 8 & 255) > (positive >> 16 & 255));
        int empty = PiglinVisuals.counterColor(0, true);
        assertTrue((empty >> 16 & 255) > (empty >> 8 & 255));
    }

    @Test void widthLeavesAFixedFaceGapAndRightAlignedTextShadow() {
        for (int textWidth : new int[]{6, 12, 18, 60}) {
            int right = 247;
            int left = right - PiglinVisuals.counterWidth(textWidth);
            int textLeft = left + PiglinVisuals.FACE_WIDTH + 4;
            assertEquals(right, textLeft + textWidth + 1);
            assertEquals(4, textLeft - (left + PiglinVisuals.FACE_WIDTH));
        }
    }

    @Test void faceIsBoundedTransparentAndHasEyesTusksAndSnout() {
        int width = PiglinVisuals.FACE_WIDTH;
        int height = PiglinVisuals.FACE_HEIGHT;
        int[] pixels = new int[width * height];
        PiglinVisuals.drawFace(31, 17, (x, y, w, h, color) -> {
            assertTrue(x >= 31 && y >= 17);
            assertTrue(w > 0 && h == 1);
            assertTrue(x + w <= 31 + width && y + h <= 17 + height);
            assertEquals(0xFF, color >>> 24);
            for (int column = x; column < x + w; column++) {
                int index = (y - 17) * width + column - 31;
                assertEquals(0, pixels[index], "Pixel runs must not overlap");
                pixels[index] = color;
            }
        });
        assertEquals(0, pixels[0]);
        assertEquals(0, pixels[width - 1]);
        assertEquals(0, pixels[width * height - 1]);
        assertTrue(Arrays.stream(pixels).filter(pixel -> pixel != 0).count() >= 100);
        assertEquals(pixels[4 * width + 5], pixels[4 * width + 10], "Paired dark eyes");
        assertNotEquals(pixels[4 * width + 5], pixels[4 * width + 7]);
        assertEquals(pixels[6 * width + 5], pixels[6 * width + 10], "Paired pale tusks");
        assertNotEquals(pixels[6 * width + 5], pixels[6 * width + 7]);
        assertEquals(pixels[7 * width + 7], pixels[7 * width + 9], "Paired nostrils");
        assertNotEquals(pixels[7 * width + 7], pixels[7 * width + 8]);
    }

    @Test void integrationMethodsRemainPublicStatic() throws Exception {
        assertPublicStatic(void.class, "renderCounter", MatrixStack.class, int.class, int.class, int.class, boolean.class);
        assertPublicStatic(int.class, "counterWidth", int.class, boolean.class);
        assertPublicStatic(boolean.class, "bartering", PiglinEntity.class);
        assertPublicStatic(void.class, "renderAbove", PiglinEntity.class, MatrixStack.class,
                VertexConsumerProvider.class, int.class);
    }

    private static void assertPublicStatic(Class<?> result, String name, Class<?>... parameters) throws Exception {
        Method method = PiglinVisuals.class.getDeclaredMethod(name, parameters);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(result, method.getReturnType());
    }
}
