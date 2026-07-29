package ortero.survivalcreativity.com.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FabricPlatformHelper implements PlatformHelper {
	@Override
	public boolean canSendC2S(CustomPacketPayload.Type<?> type) {
		return ClientPlayNetworking.canSend(type);
	}

	@Override
	public void sendC2S(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
