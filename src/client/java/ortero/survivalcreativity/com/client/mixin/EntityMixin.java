package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "hurtClient", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$noDamageWhileImagining(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof LocalPlayer && ImaginationManager.INSTANCE.isEditing()) {
			cir.setReturnValue(false);
		}
	}
}
