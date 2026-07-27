package ortero.survivalcreativity.com.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(
			ShareImaginationC2SPayload.TYPE, ShareImaginationC2SPayload.CODEC, 2 * 1024 * 1024);
		PayloadTypeRegistry.clientboundPlay().registerLarge(
			ShareImaginationS2CPayload.TYPE, ShareImaginationS2CPayload.CODEC, 2 * 1024 * 1024);

		ServerPlayNetworking.registerGlobalReceiver(ShareImaginationC2SPayload.TYPE, (payload, context) -> {
			ServerPlayer sender = context.player();
			ShareImaginationS2CPayload outbound = new ShareImaginationS2CPayload(
				payload.shareId(),
				sender.getGameProfile().name(),
				payload.imaginationName(),
				payload.data()
			);
			for (ServerPlayer player : sender.level().getServer().getPlayerList().getPlayers()) {
				if (player.getUUID().equals(sender.getUUID())) {
					continue;
				}
				if (ServerPlayNetworking.canSend(player, ShareImaginationS2CPayload.TYPE)) {
					ServerPlayNetworking.send(player, outbound);
				}
			}
			SurvivalCreativityMod.LOGGER.info(
				"{} shared large imagination \"{}\" ({} bytes nbt)",
				sender.getGameProfile().name(),
				payload.imaginationName(),
				payload.data().sizeInBytes()
			);
		});
	}
}
