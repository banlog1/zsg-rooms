package zsgrooms.modid.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SaveProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.NaturalSpawnRngAccess;
import zsgrooms.modid.rng.NaturalSpawnRngManager;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerRngMixin implements NaturalSpawnRngAccess {
    @Shadow
    @Final
    protected SaveProperties saveProperties;

    @Unique
    private NaturalSpawnRngManager zsgRooms$naturalSpawnRngManager;

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void zsgRooms$createNaturalSpawnRngManager(CallbackInfo ci) {
        this.zsgRooms$naturalSpawnRngManager = new NaturalSpawnRngManager(
                this.saveProperties.getGeneratorOptions().getSeed());
    }

    @Override
    public NaturalSpawnRngManager zsgRooms$getNaturalSpawnRngManager() {
        return this.zsgRooms$naturalSpawnRngManager;
    }
}
