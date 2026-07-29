package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import ortero.survivalcreativity.com.client.imagination.ImaginationWorldControls;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$skipSuppressedPickup(Player player, CallbackInfo ci) {
		if (ImaginationWorldControls.INSTANCE.isEntitySuppressed((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
