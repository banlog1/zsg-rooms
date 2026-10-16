package zsgrooms.modid.mixin;

import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

class ReplayPiglinBarterMixinTest {
    @Test void callsRealOperationExactlyOnceAndReturnsItsUnmodifiedList() throws Throwable {
        List<ItemStack> actual = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        assertSame(actual, invoke(args -> {
            assertEquals(1, args.length);
            assertNull(args[0]);
            calls.incrementAndGet();
            return actual;
        }));
        assertEquals(1, calls.get());
        assertTrue(actual.isEmpty());
    }

    @Test void doesNotCatchOrRetryFailureOfTheRealLootOperation() {
        IllegalArgumentException original = new IllegalArgumentException("loot failure");
        assertSame(original, assertThrows(IllegalArgumentException.class,
                () -> invoke(args -> { throw original; })));
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> invoke(Operation<List<ItemStack>> operation) throws Throwable {
        Method method = ReplayPiglinBarterMixin.class.getDeclaredMethod("zsgRooms$completedBarter",
                net.minecraft.entity.mob.PiglinEntity.class, Operation.class);
        assertTrue(Modifier.isPrivate(method.getModifiers()), "Static Mixin handlers must be private");
        method.setAccessible(true);
        try { return (List<ItemStack>) method.invoke(null, null, operation); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
}
