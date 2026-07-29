package ortero.survivalcreativity.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class NeoForgePlatformHelper implements PlatformHelper {
	@Override
	public boolean canSendC2S(CustomPacketPayload.Type<?> type) {
		Minecraft client = Minecraft.getInstance();
		return client.getConnection() != null;
	}

	@Override
	public void sendC2S(CustomPacketPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			client.getConnection().send(payload);
		}
	}
}
