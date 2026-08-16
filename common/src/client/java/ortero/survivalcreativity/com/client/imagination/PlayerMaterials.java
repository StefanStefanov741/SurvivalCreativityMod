package ortero.survivalcreativity.com.client.imagination;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material list from blocks the player personally placed/broke while imagining.
 * Absent ({@code null} on {@link Imagination}) means a legacy save without this data.
 */
public final class PlayerMaterials {
	private final Map<String, Integer> placed;
	private final Map<String, Integer> broken;

	public PlayerMaterials(Map<String, Integer> placed, Map<String, Integer> broken) {
		this.placed = new LinkedHashMap<>(placed);
		this.broken = new LinkedHashMap<>(broken);
	}

	public static PlayerMaterials empty() {
		return new PlayerMaterials(Map.of(), Map.of());
	}

	/**
	 * Build materials from final hologram changes, limited to positions the player edited.
	 */
	public static PlayerMaterials fromTouchedPositions(Set<BlockPos> touched, Imagination imagination) {
		Map<String, Integer> place = new LinkedHashMap<>();
		Map<String, Integer> brk = new LinkedHashMap<>();
		if (touched == null || touched.isEmpty() || imagination == null) {
			return empty();
		}
		for (BlockPos pos : touched) {
			BlockChange change = imagination.get(pos);
			if (change == null) {
				continue;
			}
			if (change.placement()) {
				add(place, change.imaginedState());
				if (!change.originalState().isAir()) {
					add(brk, change.originalState());
				}
			} else if (!change.originalState().isAir()) {
				add(brk, change.originalState());
			}
		}
		return new PlayerMaterials(place, brk);
	}

	public boolean isEmpty() {
		return placed.isEmpty() && broken.isEmpty();
	}

	public ImaginationMaterials.Summary toSummary() {
		return new ImaginationMaterials.Summary(toEntries(placed, true), toEntries(broken, false), true);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("tracked", true);
		tag.put("placed", saveCounts(placed));
		tag.put("broken", saveCounts(broken));
		return tag;
	}

	public static @Nullable PlayerMaterials load(@Nullable CompoundTag tag) {
		if (tag == null || !tag.getBooleanOr("tracked", false)) {
			return null;
		}
		return new PlayerMaterials(loadCounts(tag.getListOrEmpty("placed")), loadCounts(tag.getListOrEmpty("broken")));
	}

	private static void add(Map<String, Integer> counts, BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
			return;
		}
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		counts.merge(id, 1, Integer::sum);
	}

	private static ListTag saveCounts(Map<String, Integer> counts) {
		ListTag list = new ListTag();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			CompoundTag row = new CompoundTag();
			row.putString("id", entry.getKey());
			row.putInt("n", entry.getValue());
			list.add(row);
		}
		return list;
	}

	private static Map<String, Integer> loadCounts(ListTag list) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag row = list.getCompoundOrEmpty(i);
			String id = row.getStringOr("id", "");
			int n = row.getIntOr("n", 0);
			if (!id.isEmpty() && n > 0) {
				counts.merge(id, n, Integer::sum);
			}
		}
		return counts;
	}

	private static List<ImaginationMaterials.Entry> toEntries(Map<String, Integer> counts, boolean placement) {
		List<ImaginationMaterials.Entry> entries = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (block == null || block == Blocks.AIR) {
				continue;
			}
			ItemStack icon = new ItemStack(block.asItem());
			entries.add(new ImaginationMaterials.Entry(block.getName(), entry.getValue(), placement, icon));
		}
		entries.sort(Comparator.comparing(e -> e.name().getString(), String.CASE_INSENSITIVE_ORDER));
		return entries;
	}
}
