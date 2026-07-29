package ortero.survivalcreativity.com.neoforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.network.ShareImaginationC2SPayload;
import ortero.survivalcreativity.com.network.ShareImaginationS2CPayload;

public final class ModNetworkingNeoForge {
	private ModNetworkingNeoForge() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(ModNetworkingNeoForge::onRegisterPayloads);
	}

	private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("1");
		registrar.playToServer(
			ShareImaginationC2SPayload.TYPE,
			ShareImaginationC2SPayload.CODEC,
			ModNetworkingNeoForge::handleC2S
		);
		registrar.playToClient(
			ShareImaginationS2CPayload.TYPE,
			ShareImaginationS2CPayload.CODEC,
			(payload, context) -> {
				ortero.survivalcreativity.com.client.imagination.ImaginationShare.handleLargeShareReceived(
					payload.shareId(),
					payload.senderName(),
					payload.imaginationName(),
					payload.data()
				);
			}
		);
	}

	private static void handleC2S(ShareImaginationC2SPayload payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer sender)) {
			return;
		}
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
			PacketDistributor.sendToPlayer(player, outbound);
		}
		SurvivalCreativityMod.LOGGER.info(
			"{} shared large imagination \"{}\" ({} bytes nbt)",
			sender.getGameProfile().name(),
			payload.imaginationName(),
			payload.data().sizeInBytes()
		);
	}
}
