// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

/** Values consumed only by render overrides; never installed in an entity or game options. */
public final class PresentationState {
    public int flags;
    public double worldFov, handFov;
    public float worldZoom, worldZoomX, worldZoomY, handZoom, handZoomX, handZoomY;
    public float bobPhase, bobStride, pitch, swayPitch, swayYaw, swing, mainEquip, offEquip, useRemaining;
    public ItemStack main, off, active;

    public boolean bob() { return (flags & PresentationTrack.BOB) != 0; }
    public boolean hands() { return (flags & PresentationTrack.HANDS) != 0; }
    public boolean using() { return (flags & PresentationTrack.USING) != 0; }
    public boolean riptide() { return (flags & PresentationTrack.RIPTIDE) != 0; }
    public boolean leftMain() { return (flags & PresentationTrack.LEFT_MAIN) != 0; }
    public boolean swingOff() { return (flags & PresentationTrack.SWING_OFF) != 0; }
    public boolean activeOff() { return (flags & PresentationTrack.ACTIVE_OFF) != 0; }
    public boolean showMain() { return (flags & PresentationTrack.SHOW_MAIN) != 0; }
    public boolean showOff() { return (flags & PresentationTrack.SHOW_OFF) != 0; }

    static PresentationState decode(byte[] payload) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.wrappedBuffer(payload));
        try {
            PresentationTrack.validate(payload, payload.length, 0, 0);
            PresentationState state = new PresentationState();
            state.flags = data.readInt();
            if ((state.flags & PresentationTrack.AVAILABLE) == 0 || (state.flags & PresentationTrack.GAP) != 0) return null;
            state.worldFov = data.readDouble(); state.handFov = data.readDouble();
            state.worldZoom = data.readFloat(); state.worldZoomX = data.readFloat(); state.worldZoomY = data.readFloat();
            state.handZoom = data.readFloat(); state.handZoomX = data.readFloat(); state.handZoomY = data.readFloat();
            state.bobPhase = data.readFloat(); state.bobStride = data.readFloat();
            state.pitch = data.readFloat(); state.swayPitch = data.readFloat(); state.swayYaw = data.readFloat();
            state.swing = data.readFloat(); state.mainEquip = data.readFloat(); state.offEquip = data.readFloat();
            state.useRemaining = data.readFloat();
            state.main = data.readItemStack(); state.off = data.readItemStack(); state.active = data.readItemStack();
            return data.isReadable() ? null : state;
        } catch (RuntimeException | java.io.IOException error) { return null; }
        finally { data.release(); }
    }

    static void interpolate(PresentationState a, PresentationState b, double fraction, PresentationState output) {
        // Items, use-hand and animation transitions are discrete. Never blend one item's animation into another.
        output.flags = a.flags; output.main = a.main; output.off = a.off; output.active = a.active;
        output.worldFov = lerp(a.worldFov, b.worldFov, fraction);
        output.handFov = lerp(a.handFov, b.handFov, fraction);
        output.worldZoom = lerp(a.worldZoom, b.worldZoom, fraction);
        output.worldZoomX = lerp(a.worldZoomX, b.worldZoomX, fraction);
        output.worldZoomY = lerp(a.worldZoomY, b.worldZoomY, fraction);
        output.handZoom = lerp(a.handZoom, b.handZoom, fraction);
        output.handZoomX = lerp(a.handZoomX, b.handZoomX, fraction);
        output.handZoomY = lerp(a.handZoomY, b.handZoomY, fraction);
        output.bobPhase = lerp(a.bobPhase, b.bobPhase, fraction);
        output.bobStride = lerp(a.bobStride, b.bobStride, fraction);
        boolean sameHands = a.flags == b.flags && ItemStack.areEqual(a.main, b.main)
                && ItemStack.areEqual(a.off, b.off) && ItemStack.areEqual(a.active, b.active);
        double handsFraction = sameHands ? fraction : 0;
        output.pitch = lerp(a.pitch, b.pitch, handsFraction);
        output.swayPitch = lerp(a.swayPitch, b.swayPitch, handsFraction);
        output.swayYaw = lerp(a.swayYaw, b.swayYaw, handsFraction);
        // A completed swing resets to zero; it must not run backwards during interpolation.
        float swingEnd = b.swing < a.swing && a.swing - b.swing > .5F ? b.swing + 1 : b.swing;
        float swing = lerp(a.swing, swingEnd, handsFraction);
        output.swing = swing >= 1 ? swing - 1 : swing;
        output.mainEquip = lerp(a.mainEquip, b.mainEquip, handsFraction);
        output.offEquip = lerp(a.offEquip, b.offEquip, handsFraction);
        // A newly started use can reset its timer. Preserve the pre-transition state until the next sample.
        output.useRemaining = lerp(a.useRemaining, b.useRemaining,
                b.useRemaining <= a.useRemaining && a.useRemaining - b.useRemaining <= 4 ? handsFraction : 0);
    }

    private static float lerp(float a, float b, double fraction) { return (float) (a + (b - a) * fraction); }
    private static double lerp(double a, double b, double fraction) { return a + (b - a) * fraction; }
}
