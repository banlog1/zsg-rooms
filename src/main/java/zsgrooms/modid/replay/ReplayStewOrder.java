package zsgrooms.modid.replay;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReplayStewOrder {
    private ReplayStewOrder() { }

    static List<String> vanilla() {
        // Same insertion order and HashMap construction as vanilla shipwreck_supply.json and
        // SetStewEffectLootFunction.Serializer. StatusEffect uses process-specific identity hashes.
        Map<StatusEffect, Boolean> effects = new HashMap<>();
        for (StatusEffect effect : new StatusEffect[]{StatusEffects.JUMP_BOOST, StatusEffects.WEAKNESS,
                StatusEffects.POISON, StatusEffects.NIGHT_VISION, StatusEffects.BLINDNESS, StatusEffects.SATURATION}) {
            effects.put(effect, Boolean.TRUE);
        }
        List<String> order = new ArrayList<>(6);
        for (StatusEffect effect : effects.keySet()) order.add(Registry.STATUS_EFFECT.getId(effect).toString());
        return order;
    }
}
