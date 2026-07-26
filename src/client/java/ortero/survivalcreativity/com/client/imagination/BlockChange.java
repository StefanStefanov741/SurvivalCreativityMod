package ortero.survivalcreativity.com.client.imagination;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A single block difference versus the real world at the time of imagination.
 * Optional block-entity NBT captures signs, shelves, etc.
 */
public record BlockChange(
	boolean placement,
	BlockState imaginedState,
	BlockState originalState,
	@Nullable CompoundTag imaginedBlockEntity,
	@Nullable CompoundTag originalBlockEntity
) {
	public static BlockChange place(BlockState imagined, BlockState original) {
		return place(imagined, original, null, null);
	}

	public static BlockChange place(
		BlockState imagined,
		BlockState original,
		@Nullable CompoundTag imaginedBe,
		@Nullable CompoundTag originalBe
	) {
		return new BlockChange(true, imagined, original, copyTag(imaginedBe), copyTag(originalBe));
	}

	public static BlockChange remove(BlockState original) {
		return remove(original, null);
	}

	public static BlockChange remove(BlockState original, @Nullable CompoundTag originalBe) {
		return new BlockChange(false, Blocks.AIR.defaultBlockState(), original, null, copyTag(originalBe));
	}

	public BlockChange withImagined(BlockState state, @Nullable CompoundTag be) {
		return new BlockChange(placement, state, originalState, copyTag(be), originalBlockEntity);
	}

	public boolean matchesWorld(BlockState state, @Nullable CompoundTag be) {
		return imaginedState.equals(state) && Objects.equals(imaginedBlockEntity, be);
	}

	public boolean equalsOriginal(BlockState state, @Nullable CompoundTag be) {
		return originalState.equals(state) && Objects.equals(originalBlockEntity, be);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("placement", placement);
		tag.put("imagined", NbtUtils.writeBlockState(imaginedState));
		tag.put("original", NbtUtils.writeBlockState(originalState));
		if (imaginedBlockEntity != null) {
			tag.put("imaginedBe", imaginedBlockEntity.copy());
		}
		if (originalBlockEntity != null) {
			tag.put("originalBe", originalBlockEntity.copy());
		}
		return tag;
	}

	public static BlockChange load(CompoundTag tag, HolderGetter<Block> blocks) {
		boolean placement = tag.getBooleanOr("placement", true);
		BlockState imagined = NbtUtils.readBlockState(blocks, tag.getCompoundOrEmpty("imagined"));
		BlockState original = NbtUtils.readBlockState(blocks, tag.getCompoundOrEmpty("original"));
		CompoundTag imaginedBe = tag.contains("imaginedBe") ? tag.getCompoundOrEmpty("imaginedBe").copy() : null;
		CompoundTag originalBe = tag.contains("originalBe") ? tag.getCompoundOrEmpty("originalBe").copy() : null;
		return new BlockChange(placement, imagined, original, imaginedBe, originalBe);
	}

	public static @Nullable CompoundTag saveBlockEntity(BlockEntity be, HolderLookup.Provider registries) {
		if (be == null) {
			return null;
		}
		CompoundTag tag = be.saveWithoutMetadata(registries);
		return tag.isEmpty() ? null : tag;
	}

	private static @Nullable CompoundTag copyTag(@Nullable CompoundTag tag) {
		return tag == null ? null : tag.copy();
	}
}
