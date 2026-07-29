package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
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
