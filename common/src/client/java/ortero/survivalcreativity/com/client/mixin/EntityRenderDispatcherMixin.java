package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$skipSuppressed(
		Entity entity,
		Frustum culler,
		double camX,
		double camY,
		double camZ,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed(entity)) {
			cir.setReturnValue(false);
		}
	}
}
