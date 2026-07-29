package ortero.survivalcreativity.com.client.imagination;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Preview hologram sync only. While editing, vanilla creative handles everything.
 */
public final class ImaginationInteractions {
	private ImaginationInteractions() {
	}

	public static void register() { }

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (!level.isClientSide() || !(player instanceof LocalPlayer)) {
			return InteractionResult.PASS;
		}
		ImaginationManager manager = ImaginationManager.INSTANCE;
		if (manager.isEditing()) {
			return InteractionResult.PASS;
		}
		if (manager.isPreviewing() && hitResult instanceof BlockHitResult blockHit) {
			BlockPos placePos = blockHit.getBlockPos().relative(blockHit.getDirection());
			PendingPreviewPlace.mark(placePos);
		}
		return InteractionResult.PASS;
	}

	public static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state) {
		ImaginationManager.INSTANCE.onRealBlockBroken(pos, state);
	}

	public static final class PendingPreviewPlace {
		private static BlockPos pending;
		private static int ticksLeft;

		private PendingPreviewPlace() {
		}

		public static void mark(BlockPos pos) {
			pending = pos.immutable();
			ticksLeft = 5;
		}

		public static void tick(Minecraft client) {
			if (pending == null || client.level == null) {
				return;
			}
			if (!ImaginationManager.INSTANCE.isPreviewing()) {
				pending = null;
				return;
			}
			BlockState state = client.level.getBlockState(pending);
			if (!state.isAir()) {
				ImaginationManager.INSTANCE.onRealBlockPlaced(pending);
				pending = null;
				ticksLeft = 0;
				return;
			}
			if (--ticksLeft <= 0) {
				pending = null;
			}
		}
	}
}
