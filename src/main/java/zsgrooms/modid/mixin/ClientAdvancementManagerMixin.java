package zsgrooms.modid.mixin;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementManager;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ZsgRoomsClient;

import java.util.Map;

@Mixin(ClientAdvancementManager.class)
public abstract class ClientAdvancementManagerMixin {
    @Shadow @Final private AdvancementManager manager;

    @Inject(method = "onAdvancements", at = @At("TAIL"))
    private void zsgRooms$onAdvancements(AdvancementUpdateS2CPacket packet, CallbackInfo ci) {
        if (packet.shouldClearCurrent()) {
            return;
        }
        for (Map.Entry<Identifier, AdvancementProgress> entry : packet.getAdvancementsToProgress().entrySet()) {
            if (!entry.getValue().isDone()) {
                continue;
            }
            Advancement advancement = this.manager.get(entry.getKey());
            if (advancement == null || advancement.getDisplay() == null
                    || entry.getKey().getPath().startsWith("recipes/")) {
                continue;
            }
            String title = advancement.getDisplay().getTitle().getString();
            ZsgRoomsClient.onAdvancementCompleted(entry.getKey().toString(), title);
        }
    }
}
