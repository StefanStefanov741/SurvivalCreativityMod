package ortero.survivalcreativity.com.client.imagination;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Hologram visualization helper: draw liquids as still sources.
 * Does not affect imagination edit mode (water may still flow there).
 */
public final class ImaginationFluids {
	private ImaginationFluids() {
	}

	public static BlockState asStill(BlockState state) {
		FluidState fluid = state.getFluidState();
		if (fluid.isEmpty() || fluid.isSource()) {
			return state;
		}
		if (state.getBlock() instanceof LiquidBlock && fluid.getType() instanceof FlowingFluid flowing) {
			return flowing.getSource(false).createLegacyBlock();
		}
		return state;
	}

	public static FluidState asStill(FluidState fluid) {
		if (fluid.isEmpty() || fluid.isSource()) {
			return fluid;
		}
		if (fluid.getType() instanceof FlowingFluid flowing) {
			return flowing.getSource(false);
		}
		return fluid;
	}
}
