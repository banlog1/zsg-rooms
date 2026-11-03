package zsgrooms.modid.replay;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import zsgrooms.modid.mixin.ReplayLocalMiningSoundMixin;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ReplayLocalMiningSoundMixinTest {
    @Test void observesConstructorArgumentsWithoutResolvingOrChangingTheSound() throws Exception {
        SoundEvent event = new SoundEvent(new Identifier("minecraft:block.stone.hit"));
        BlockPos pos = new BlockPos(3, 64, -7);
        PositionedSoundInstance sound = new PositionedSoundInstance(event, SoundCategory.BLOCKS, .25F, .5F, pos);
        assertNull(sound.getSound());
        assertThrows(NullPointerException.class, sound::getVolume);
        assertThrows(NullPointerException.class, sound::getPitch);

        Object[] values = {event, SoundCategory.BLOCKS, .25F, .5F, pos};
        Args args = new Args(values) {
            @Override public <T> void set(int index, T value) { fail("Observation must not replace sound arguments"); }
            @Override public void setAll(Object... values) { fail("Observation must not replace sound arguments"); }
        };
        Method hook = ReplayLocalMiningSoundMixin.class.getDeclaredMethod("zsgRooms$localMiningSound", Args.class);
        ModifyArgs annotation = hook.getAnnotation(ModifyArgs.class);
        assertEquals("Lnet/minecraft/client/sound/PositionedSoundInstance;<init>(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFLnet/minecraft/util/math/BlockPos;)V",
                annotation.at().target());
        hook.setAccessible(true);
        hook.invoke(new ReplayLocalMiningSoundMixin() { }, args);
        assertArrayEquals(new Object[]{event, SoundCategory.BLOCKS, .25F, .5F, pos}, values);
        assertNull(sound.getSound());
    }
}
