// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.loot.UniformLootTableRange;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.function.SetStewEffectLootFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.Map;

@Mixin(SetStewEffectLootFunction.class)
public interface PredictionStewAccessor {
    @Invoker("<init>")
    static SetStewEffectLootFunction zsgViewer$create(LootCondition[] conditions, Map<StatusEffect, UniformLootTableRange> effects) {
        throw new AssertionError();
    }
}
