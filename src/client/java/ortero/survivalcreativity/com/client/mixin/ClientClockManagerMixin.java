package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
	@Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$overrideTotalTicks(
		Holder<WorldClock> definition,
		CallbackInfoReturnable<Long> cir
	) {
		ImaginationWorldControls controls = ImaginationWorldControls.INSTANCE;
		if (!controls.isRemoteOverrideActive()) {
			return;
		}
		if (definition.is(WorldClocks.OVERWORLD)) {
			cir.setReturnValue(controls.getRemoteTotalTicks());
		}
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$overrideTick(long gameTime, CallbackInfo ci) {
		if (ImaginationWorldControls.INSTANCE.tickRemoteOverride(gameTime)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleUpdates", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$ignoreServerTimeWhileEditing(long gameTime, java.util.Map<?, ?> updates, CallbackInfo ci) {
		if (ImaginationWorldControls.INSTANCE.isRemoteOverrideActive()) {
			ImaginationWorldControls.INSTANCE.tickRemoteOverride(gameTime);
			ci.cancel();
		}
	}
}
