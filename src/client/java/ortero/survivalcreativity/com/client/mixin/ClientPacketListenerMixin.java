package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
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

import ortero.survivalcreativity.com.client.imagination.ImaginationPacketGate;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleBlockEntityData", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$blockEntity(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleBlockEvent", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$blockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleBlockDestruction", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$blockDestruction(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleBlockChangedAck", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$blockAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$movePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$abilities(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleSetPlayerInventory", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$inventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleContainerSetSlot", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$setSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleContainerContent", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$setContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$gameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	private static void cancelIfNeeded(Packet<?> packet, CallbackInfo ci) {
		if (ImaginationPacketGate.shouldBlockInbound(packet)) {
			ci.cancel();
		}
	}
}
