package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

/**
 * Imagination "fire spread off" — stop fire from spreading without freezing fire age/out.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "canSpreadFireAround", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$disableFireSpread(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isFireSpreadDisabled()) {
			cir.setReturnValue(false);
		}
	}
}
