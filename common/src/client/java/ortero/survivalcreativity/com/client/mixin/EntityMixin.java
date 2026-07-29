package ortero.survivalcreativity.com.client.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "hurtClient", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$noDamageWhileImagining(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof LocalPlayer && ImaginationManager.INSTANCE.isEditing()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$hideSuppressed(CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$hideSuppressedFromPlayer(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$suppressedNotAttackable(CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$suppressedNoCollision(@Nullable Entity other, CallbackInfoReturnable<Boolean> cir) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
