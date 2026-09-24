// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

@Mixin(LootContext.class)
public interface PredictionLootContextAccessor {
    @Invoker("<init>")
    static LootContext zsgViewer$create(Random random, float luck, ServerWorld world,
            Function<Identifier, LootTable> tables, Function<Identifier, LootCondition> conditions,
            Map<LootContextParameter<?>, Object> parameters, Map<Identifier, LootContext.Dropper> drops) {
        throw new AssertionError();
    }
}
