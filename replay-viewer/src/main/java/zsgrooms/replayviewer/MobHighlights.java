// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.util.Identifier;
import java.util.HashSet;
import java.util.Set;

final class MobHighlights {
    boolean enabled = true;
    TrailStyle.Color color = TrailStyle.Color.CYAN;
    private final Set<Identifier> selected = new HashSet<>();

    boolean selected(Identifier type) { return selected.contains(type); }
    boolean active() { return enabled && !selected.isEmpty(); }
    int size() { return selected.size(); }
    void set(Identifier type, boolean value) {
        if (value) selected.add(type); else selected.remove(type);
    }
    void clear() { selected.clear(); }
    void copyFrom(MobHighlights other) {
        if (other == this) return;
        enabled = other.enabled;
        color = other.color;
        selected.clear();
        selected.addAll(other.selected);
    }
}
