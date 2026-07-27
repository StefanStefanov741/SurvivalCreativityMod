package ortero.survivalcreativity.com.client.imagination;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/**
 * Multiplayer imagination stays in the live world: block outbound place/break/move
 * (except AFK heartbeats / teleport ACKs) and ignore inbound packets that would yank
 * creative mode or inventory. Chat and player-list packets are never blocked.
 * Position teleports are ACKed separately (see ImaginationManager.acknowledgeServerTeleport).
 */
public final class ImaginationPacketGate {
	private ImaginationPacketGate() {
	}

	public static boolean shouldBlockOutbound(Packet<?> packet) {
		ImaginationManager manager = ImaginationManager.INSTANCE;
		if (!manager.isRemoteEditing()) {
			return false;
		}
		if (packet instanceof ServerboundMovePlayerPacket) {
			// Allow only the AFK heartbeat / teleport-confirm moves we inject
			return !manager.isSendingIdleHeartbeat();
		}
		// Never block chat, commands, or teleport accepts
		if (packet instanceof ServerboundAcceptTeleportationPacket) {
			return false;
		}
		return packet instanceof ServerboundPlayerActionPacket
			|| packet instanceof ServerboundUseItemOnPacket
			|| packet instanceof ServerboundUseItemPacket
			|| packet instanceof ServerboundInteractPacket
			|| packet instanceof ServerboundSetCreativeModeSlotPacket
			|| packet instanceof ServerboundPlayerAbilitiesPacket
			|| packet instanceof ServerboundContainerClickPacket
			|| packet instanceof ServerboundSignUpdatePacket
			|| packet instanceof ServerboundPickItemFromBlockPacket
			|| packet instanceof ServerboundPickItemFromEntityPacket;
	}

	public static boolean shouldBlockInbound(Packet<?> packet) {
		ImaginationManager manager = ImaginationManager.INSTANCE;
		if (!manager.isRemoteEditing()) {
			return false;
		}
		// Yank-prevention only. Chat, joins, chunks, entities stay live.
		// Player position is handled specially (ACK without applying) in ClientPacketListenerMixin.
		if (packet instanceof ClientboundPlayerAbilitiesPacket
			|| packet instanceof ClientboundSetPlayerInventoryPacket
			|| packet instanceof ClientboundContainerSetSlotPacket
			|| packet instanceof ClientboundContainerSetContentPacket) {
			return true;
		}
		if (packet instanceof ClientboundGameEventPacket gameEvent) {
			return gameEvent.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE;
		}
		return false;
	}
}
