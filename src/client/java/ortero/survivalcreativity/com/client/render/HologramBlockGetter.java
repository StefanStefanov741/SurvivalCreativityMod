package ortero.survivalcreativity.com.client.render;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import ortero.survivalcreativity.com.client.imagination.BlockChange;
import ortero.survivalcreativity.com.client.imagination.Imagination;
import ortero.survivalcreativity.com.client.imagination.ImaginationFluids;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

/**
 * Block view for hologram fluid meshing: only imagined placement blocks exist,
 * so flowing water heights and face culling use the hologram neighborhood.
 */
final class HologramBlockGetter implements BlockAndTintGetter {
	private final ClientLevel level;
	private final Imagination imagination;
	private final ImaginationManager manager;

	HologramBlockGetter(ClientLevel level, Imagination imagination, ImaginationManager manager) {
		this.level = level;
		this.imagination = imagination;
		this.manager = manager;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		if (manager.isSuppressed(pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		BlockChange change = imagination.get(pos);
		if (change != null && change.placement()) {
			BlockState real = level.getBlockState(pos);
			// Already built — treat as empty for hologram meshing so fluids/faces don't glow through.
			if (real.equals(change.imaginedState())) {
				return Blocks.AIR.defaultBlockState();
			}
			return ImaginationFluids.asStill(change.imaginedState());
		}
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public int getHeight() {
		return level.getHeight();
	}

	@Override
	public int getMinY() {
		return level.getMinY();
	}

	@Override
	public CardinalLighting cardinalLighting() {
		return level.cardinalLighting();
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return level.getLightEngine();
	}

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver color) {
		return level.getBlockTint(pos, color);
	}
}
