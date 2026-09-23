package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

/**
 * 26.3 moved clock totals onto {@code ClientClockInstance}; override reads while
 * multiplayer imagination keeps a local day/night timeline.
 */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class ClientClockInstanceMixin {
	@Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$overrideTotalTicks(CallbackInfoReturnable<Long> cir) {
		ImaginationWorldControls controls = ImaginationWorldControls.INSTANCE;
		if (controls.isRemoteOverrideActive()) {
			cir.setReturnValue(controls.getRemoteTotalTicks());
		}
	}
}
