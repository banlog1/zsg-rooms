// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PresentationStateTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.getGameVersion(); net.minecraft.Bootstrap.initialize(); }

    @Test void readsExactFovEquipSwayBobbingAndRenderedHandStacks() {
        PresentationState state = PresentationState.decode(withItems(new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.SHIELD)));
        assertNotNull(state); assertEquals(93.125, state.worldFov); assertEquals(70, state.handFov);
        assertEquals(.25F, state.swing); assertEquals(.3F, state.mainEquip); assertEquals(.4F, state.offEquip);
        assertEquals(30.75F, state.useRemaining); assertEquals(-.25F, state.bobPhase); assertEquals(.1F, state.bobStride);
        assertEquals(Items.DIAMOND_AXE, state.main.getItem()); assertEquals(Items.SHIELD, state.off.getItem());
        assertTrue(state.bob()); assertTrue(state.hands()); assertTrue(state.showMain()); assertTrue(state.showOff());
    }

    @Test void malformedScalarAndItemPayloadsFailClosed() {
        byte[] bytes = PresentationTrackTest.payload(70);
        for (int length = 0; length < bytes.length; length++) assertNull(PresentationState.decode(Arrays.copyOf(bytes, length)));
        byte[] invalid = bytes.clone(); ByteBuffer.wrap(invalid).putDouble(4, Double.NaN);
        assertNull(PresentationState.decode(invalid));
        assertNull(PresentationState.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        byte[] invalidItem = bytes.clone(); invalidItem[82] = 1;
        assertNull(PresentationState.decode(invalidItem));
    }

    @Test void interpolationDoesNotRunSwingsBackwardsOrInventItemUseTransitions() {
        PresentationState a = PresentationState.decode(PresentationTrackTest.payload(70));
        PresentationState b = PresentationState.decode(PresentationTrackTest.payload(90));
        PresentationState result = new PresentationState();
        a.swing = .9F; b.swing = .1F; a.useRemaining = 30.75F; b.useRemaining = 30.25F;
        PresentationState.interpolate(a, b, .25, result);
        assertEquals(75, result.worldFov); assertEquals(.95F, result.swing, .00001F);
        assertEquals(30.625F, result.useRemaining);
        assertEquals(.9F, a.swing); assertEquals(.1F, b.swing);
        b.useRemaining = 72000;
        PresentationState.interpolate(a, b, .5, result);
        assertEquals(a.useRemaining, result.useRemaining);
        b.main = new ItemStack(Items.IRON_PICKAXE); b.mainEquip = .99F;
        PresentationState.interpolate(a, b, .5, result);
        assertSame(a.main, result.main); assertEquals(a.mainEquip, result.mainEquip); assertEquals(a.swing, result.swing);
    }

    private static byte[] withItems(ItemStack main, ItemStack off) {
        PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
        try {
            bytes.writeBytes(PresentationTrackTest.payload(93.125), 0, 80);
            bytes.writeItemStack(main); bytes.writeItemStack(off); bytes.writeItemStack(ItemStack.EMPTY);
            byte[] payload = new byte[bytes.writerIndex()]; bytes.getBytes(0, payload); return payload;
        } finally { bytes.release(); }
    }
}
