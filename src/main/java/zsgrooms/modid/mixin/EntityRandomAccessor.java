package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Random;

@Mixin(Entity.class)
public interface EntityRandomAccessor {
    @Accessor("random")
    Random zsgRooms$getRandom();
}
