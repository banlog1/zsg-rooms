// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** A read-only equipment overlay; never writes to ReplayMod's reconstructed inventory. */
final class QuickHeldItems {
    private PlayerEntity player;
    private PlayerHudTrack.Frame last;
    private PlayerHudState state;
    private int time;

    void update(PlayerEntity target, PlayerHudTrack.Frame frame, int timestamp) {
        if (target != player || frame != last) {
            state = frame == null ? null : PlayerHudState.decode(frame.payload);
            player = target;
            last = frame;
        }
        time = timestamp;
    }

    ItemStack get(PlayerEntity target, EquipmentSlot slot, int timestamp) {
        if (target != player || state == null || time != timestamp) return null;
        if (slot == EquipmentSlot.MAINHAND) return state.items[state.selected];
        if (slot == EquipmentSlot.OFFHAND) return state.items[40];
        return null;
    }

    void clear() { player = null; last = null; state = null; }
}
