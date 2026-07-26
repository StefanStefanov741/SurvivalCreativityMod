package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("RETURN")
	)
	private void survivalcreativity$trackRemoteDirty(
		BlockPos pos,
		BlockState state,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		if (!((Object) this instanceof ClientLevel)) {
			return;
		}
		ImaginationManager.INSTANCE.onRemoteClientBlockChanged(pos, state);
	}
}
