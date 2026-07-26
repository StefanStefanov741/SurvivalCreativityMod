package ortero.survivalcreativity.com.client.imagination;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/**
 * On multiplayer, imagination is client-local: block packets that would change the
 * real server world, and ignore server overwrites of our local sandbox.
 */
public final class ImaginationPacketGate {
	private ImaginationPacketGate() {
	}

	public static boolean shouldBlockOutbound(Packet<?> packet) {
		ImaginationManager manager = ImaginationManager.INSTANCE;
		if (!manager.isRemoteEditing()) {
			return false;
		}
		return packet instanceof ServerboundPlayerActionPacket
			|| packet instanceof ServerboundUseItemOnPacket
			|| packet instanceof ServerboundUseItemPacket
			|| packet instanceof ServerboundInteractPacket
			|| packet instanceof ServerboundSetCreativeModeSlotPacket
			|| packet instanceof ServerboundPlayerAbilitiesPacket
			|| packet instanceof ServerboundContainerClickPacket
			|| packet instanceof ServerboundSignUpdatePacket;
	}

	public static boolean shouldBlockInbound(Packet<?> packet) {
		ImaginationManager manager = ImaginationManager.INSTANCE;
		if (!manager.isRemoteEditing()) {
			return false;
		}
		if (packet instanceof ClientboundBlockUpdatePacket
			|| packet instanceof ClientboundSectionBlocksUpdatePacket
			|| packet instanceof ClientboundBlockEntityDataPacket
			|| packet instanceof ClientboundBlockEventPacket
			|| packet instanceof ClientboundBlockDestructionPacket
			|| packet instanceof ClientboundBlockChangedAckPacket
			|| packet instanceof ClientboundLevelChunkWithLightPacket
			|| packet instanceof ClientboundPlayerPositionPacket
			|| packet instanceof ClientboundPlayerAbilitiesPacket
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
