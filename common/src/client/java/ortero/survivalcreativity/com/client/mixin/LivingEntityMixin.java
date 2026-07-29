package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$suppressedNotPickable(CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$suppressedNotPushable(CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
