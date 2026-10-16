// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.util.Identifier;
import java.util.HashSet;
import java.util.Set;

/** Shared type selection; outlines and paths keep independent instances. */
class MobSelection {
    boolean enabled = true;
    private final Set<Identifier> selected = new HashSet<>();
    private int revision;

    boolean selected(Identifier type) { return selected.contains(type); }
    boolean active() { return enabled && !selected.isEmpty(); }
    int size() { return selected.size(); }
    int revision() { return revision; }
    void set(Identifier type, boolean value) {
        if (value ? selected.add(type) : selected.remove(type)) revision++;
    }
    void clear() {
        if (!selected.isEmpty()) { selected.clear(); revision++; }
    }
    void copyFrom(MobSelection other) {
        if (other == this) return;
        enabled = other.enabled;
        selected.clear();
        selected.addAll(other.selected);
        revision++;
    }
}
