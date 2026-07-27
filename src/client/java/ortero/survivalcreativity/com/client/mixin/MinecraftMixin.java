package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

/**
 * Arm quit-guard + restore survival player before any disconnect/save path runs.
 * Clear the in-memory quit-guard after disconnect completes (pending revert file remains).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "disconnectWithSavingScreen", at = @At("HEAD"))
	private void survivalcreativity$beforeSavingQuit(CallbackInfo ci) {
		ImaginationManager.INSTANCE.prepareForWorldSave((Minecraft) (Object) this);
	}

	@Inject(method = "disconnectWithSavingScreen", at = @At("RETURN"))
	private void survivalcreativity$afterSavingQuit(CallbackInfo ci) {
		ImaginationManager.INSTANCE.onDisconnectFinished();
	}

	@Inject(method = "disconnectWithProgressScreen", at = @At("HEAD"))
	private void survivalcreativity$beforeProgressQuit(CallbackInfo ci) {
		ImaginationManager.INSTANCE.prepareForWorldSave((Minecraft) (Object) this);
	}

	@Inject(method = "disconnectWithProgressScreen", at = @At("RETURN"))
	private void survivalcreativity$afterProgressQuit(CallbackInfo ci) {
		ImaginationManager.INSTANCE.onDisconnectFinished();
	}

	@Inject(method = "disconnectWithProgressScreen(Z)V", at = @At("HEAD"))
	private void survivalcreativity$beforeProgressQuitFlag(boolean transfer, CallbackInfo ci) {
		ImaginationManager.INSTANCE.prepareForWorldSave((Minecraft) (Object) this);
	}

	@Inject(method = "disconnectWithProgressScreen(Z)V", at = @At("RETURN"))
	private void survivalcreativity$afterProgressQuitFlag(boolean transfer, CallbackInfo ci) {
		ImaginationManager.INSTANCE.onDisconnectFinished();
	}

	@Inject(method = "disconnectFromWorld", at = @At("HEAD"))
	private void survivalcreativity$beforeDisconnectFromWorld(Component reason, CallbackInfo ci) {
		ImaginationManager.INSTANCE.prepareForWorldSave((Minecraft) (Object) this);
	}

	@Inject(method = "disconnectFromWorld", at = @At("RETURN"))
	private void survivalcreativity$afterDisconnectFromWorld(Component reason, CallbackInfo ci) {
		ImaginationManager.INSTANCE.onDisconnectFinished();
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
	private void survivalcreativity$beforeDisconnect2(Screen screen, boolean glitchlessLeaving, CallbackInfo ci) {
		ImaginationManager.INSTANCE.prepareForWorldSave((Minecraft) (Object) this);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("RETURN"))
	private void survivalcreativity$afterDisconnect2(Screen screen, boolean glitchlessLeaving, CallbackInfo ci) {
		ImaginationManager.INSTANCE.onDisconnectFinished();
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void survivalcreativity$beforeDisconnect3(Screen screen, boolean glitchlessLeaving, boolean transfer, CallbackInfo ci) {
		ImaginationManager.INSTANCE.prepareForWorldSave((Minecraft) (Object) this);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
	private void survivalcreativity$afterDisconnect3(Screen screen, boolean glitchlessLeaving, boolean transfer, CallbackInfo ci) {
		ImaginationManager.INSTANCE.onDisconnectFinished();
	}
}
