package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.ChannelFutureListener;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import ortero.survivalcreativity.com.client.imagination.ImaginationPacketGate;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void survivalcreativity$blockOutbound(
		Packet<?> packet,
		ChannelFutureListener listener,
		boolean flush,
		CallbackInfo ci
	) {
		if (ImaginationPacketGate.shouldBlockOutbound(packet)) {
			ci.cancel();
		}
	}
}
