package zsgrooms.modid.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.replay.ReplayPrototype;

import java.net.SocketAddress;

@Mixin(ClientConnection.class)
public abstract class ReplayConnectionMixin {
    @Inject(method = "connectLocal", at = @At("RETURN"))
    private static void zsgRooms$beginReplay(SocketAddress address, CallbackInfoReturnable<ClientConnection> cir) {
        ReplayPrototype.connected(cir.getReturnValue());
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
            at = @At("HEAD"))
    private void zsgRooms$recordPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        ReplayPrototype.received((ClientConnection) (Object) this, packet);
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void zsgRooms$finishReplay(ChannelHandlerContext context, CallbackInfo ci) {
        ReplayPrototype.disconnected((ClientConnection) (Object) this);
    }
}
