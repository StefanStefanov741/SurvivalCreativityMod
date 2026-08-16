package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void survivalcreativity$recordPlayerBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			ImaginationManager.INSTANCE.recordPlayerBlockEdit(pos);
		}
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void survivalcreativity$recordPlayerPlace(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		InteractionResult result = cir.getReturnValue();
		if (result != null && result.consumesAction()) {
			ImaginationManager.INSTANCE.recordPlayerBlockEdit(hit.getBlockPos());
			ImaginationManager.INSTANCE.recordPlayerBlockEdit(hit.getBlockPos().relative(hit.getDirection()));
		}
	}

	@Inject(method = "handlePickItemFromBlock", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$localPickBlock(BlockPos pos, boolean includeData, CallbackInfo ci) {
		if (!ImaginationManager.INSTANCE.isRemoteEditing()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		BlockState state = client.level.getBlockState(pos);
		ItemStack stack = state.getCloneItemStack(client.level, pos, includeData);
		if (!stack.isEmpty()) {
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), stack);
		}
		ci.cancel();
	}

	@Inject(method = "handlePickItemFromEntity", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$localPickEntity(Entity entity, boolean includeData, CallbackInfo ci) {
		if (!ImaginationManager.INSTANCE.isRemoteEditing()) {
			return;
		}
		ci.cancel();
	}
}
