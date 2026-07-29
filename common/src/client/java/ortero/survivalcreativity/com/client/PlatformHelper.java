package ortero.survivalcreativity.com.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface PlatformHelper {
	PlatformHelper INSTANCE = loadHelper();

	boolean canSendC2S(CustomPacketPayload.Type<?> type);
	void sendC2S(CustomPacketPayload payload);

	private static PlatformHelper loadHelper() {
		try {
			return (PlatformHelper) Class.forName("ortero.survivalcreativity.com.client.FabricPlatformHelper")
				.getDeclaredConstructor().newInstance();
		} catch (Exception e1) {
			try {
				return (PlatformHelper) Class.forName("ortero.survivalcreativity.com.client.NeoForgePlatformHelper")
					.getDeclaredConstructor().newInstance();
			} catch (Exception e2) {
				throw new RuntimeException("No platform helper found!", e2);
			}
		}
	}
}
