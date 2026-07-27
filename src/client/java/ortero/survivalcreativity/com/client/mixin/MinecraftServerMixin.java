package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.MinecraftServer;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

/**
 * Last line of defence: whenever the integrated server saves, strip imagination
 * edits first. Quit-time restore from the client thread is unreliable.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Inject(method = "saveEverything", at = @At("HEAD"))
	private void survivalcreativity$beforeSaveEverything(
		boolean suppressLogs,
		boolean flush,
		boolean force,
		CallbackInfoReturnable<Boolean> cir
	) {
		ImaginationManager.INSTANCE.ensureWorldRevertedBeforeSave((MinecraftServer) (Object) this);
	}

	@Inject(method = "saveAllChunks", at = @At("HEAD"))
	private void survivalcreativity$beforeSaveAllChunks(
		boolean suppressLogs,
		boolean flush,
		boolean force,
		CallbackInfoReturnable<Boolean> cir
	) {
		ImaginationManager.INSTANCE.ensureWorldRevertedBeforeSave((MinecraftServer) (Object) this);
	}
}
