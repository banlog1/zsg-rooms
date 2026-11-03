// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.replayviewer.mixin.PresentationHeldMixin;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class PresentationMixinContractTest {
    @Test void everyHandInvocationDescriptorHasAnExplicitOwnerBoundary() {
        int checked = 0;
        for (Method method : PresentationHeldMixin.class.getDeclaredMethods()) {
            Redirect redirect = method.getAnnotation(Redirect.class);
            if (redirect == null) continue;
            String target = redirect.at().target();
            assertTrue(target.matches("^L[^;.]+;[A-Za-z_$][A-Za-z0-9_$]*\\(.*\\).+$"),
                    method.getName() + " has an invalid invocation descriptor: " + target);
            checked++;
        }
        assertEquals(6, checked);
    }
}
