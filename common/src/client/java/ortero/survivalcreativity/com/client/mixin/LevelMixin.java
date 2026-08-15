package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

/**
 * Track every block change during imagination (client + integrated server levels)
 * so disconnect can revert exactly those positions. Positions outside the initial
 * snapshot cube are captured lazily before the change lands.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("HEAD")
	)
	private void survivalcreativity$captureOutsideSnapshot(
		BlockPos pos,
		BlockState state,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		ImaginationManager.INSTANCE.ensureSessionBlockCaptured((Level) (Object) this, pos);
	}

	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("RETURN")
	)
	private void survivalcreativity$trackSessionDirty(
		BlockPos pos,
		BlockState state,
		int flags,
		int recursionLeft,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		ImaginationManager.INSTANCE.onSessionBlockChanged(pos, state);
	}
}
