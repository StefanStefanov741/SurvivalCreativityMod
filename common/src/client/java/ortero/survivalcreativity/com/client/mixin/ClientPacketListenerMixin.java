package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.imagination.ImaginationPacketGate;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$ignoreServerTime(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo ci) {
		if (ImaginationManager.INSTANCE.isRemoteEditing()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$movePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		if (!ImaginationManager.INSTANCE.isRemoteEditing()) {
			return;
		}
		ImaginationManager.INSTANCE.acknowledgeServerTeleport(packet);
		ci.cancel();
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

	@Inject(method = "handleSetHealth", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$setHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleHurtAnimation", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$hurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$damageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		cancelIfNeeded(packet, ci);
	}

	@Inject(method = "handleBlockUpdate", at = @At("RETURN"))
	private void survivalcreativity$reapplyAfterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		ImaginationManager.INSTANCE.reapplyRemoteOverlay(packet.getPos());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
	private void survivalcreativity$reapplyAfterSection(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		ImaginationManager.INSTANCE.reapplyRemoteOverlayAll();
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
	private void survivalcreativity$reapplyAfterChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		ImaginationManager.INSTANCE.reapplyRemoteOverlayAll();
	}

	private static void cancelIfNeeded(Packet<?> packet, CallbackInfo ci) {
		if (ImaginationPacketGate.shouldBlockInbound(packet)) {
			ci.cancel();
		}
	}
}
