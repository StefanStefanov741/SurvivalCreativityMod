package ortero.survivalcreativity.com.client.imagination;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tallies blocks to place (−) and blocks to mine (+) for a hologram, kept separate.
 */
public final class ImaginationMaterials {
	private ImaginationMaterials() {
	}

	public record Entry(Component name, int count, boolean placement, ItemStack icon) {
		public String line() {
			String sign = placement ? "-" : "+";
			return sign + count + " " + name.getString();
		}
	}

	public record Summary(List<Entry> toPlace, List<Entry> toBreak) {
		public boolean isEmpty() {
			return toPlace.isEmpty() && toBreak.isEmpty();
		}

		public String asClipboardText(String hologramName) {
			StringBuilder sb = new StringBuilder();
			sb.append(hologramName).append('\n');
			if (!toPlace.isEmpty()) {
				sb.append("Place:\n");
				for (Entry entry : toPlace) {
					sb.append(entry.line()).append('\n');
				}
			}
			if (!toBreak.isEmpty()) {
				sb.append("Break:\n");
				for (Entry entry : toBreak) {
					sb.append(entry.line()).append('\n');
				}
			}
			return sb.toString().trim();
		}

		public List<String> asChatMessages(String hologramName) {
			List<String> messages = new ArrayList<>();
			String header = "[Imagination] " + hologramName;
			messages.add(header);
			StringBuilder current = new StringBuilder();
			Runnable flush = () -> {
				if (!current.isEmpty()) {
					messages.add(current.toString().trim());
					current.setLength(0);
				}
			};
			for (Entry entry : toPlace) {
				appendChunk(current, entry.line(), flush);
			}
			for (Entry entry : toBreak) {
				appendChunk(current, entry.line(), flush);
			}
			flush.run();
			if (messages.size() == 1 && isEmpty()) {
				messages.add("(no block changes)");
			}
			return messages;
		}

		private static void appendChunk(StringBuilder current, String line, Runnable flush) {
			if (current.isEmpty()) {
				if (line.length() > 240) {
					current.append(line, 0, 240);
					flush.run();
				} else {
					current.append(line);
				}
				return;
			}
			if (current.length() + 1 + line.length() > 240) {
				flush.run();
				current.append(line.length() > 240 ? line.substring(0, 240) : line);
			} else {
				current.append(' ').append(line);
			}
		}
	}

	public static Summary summarize(Imagination imagination) {
		Map<Block, Integer> place = new LinkedHashMap<>();
		Map<Block, Integer> brk = new LinkedHashMap<>();

		for (BlockChange change : imagination.changes().values()) {
			if (change.placement()) {
				add(place, change.imaginedState());
				// Replacing a solid block also means mining the original.
				if (!change.originalState().isAir()) {
					add(brk, change.originalState());
				}
			} else if (!change.originalState().isAir()) {
				add(brk, change.originalState());
			}
		}

		return new Summary(toEntries(place, true), toEntries(brk, false));
	}

	private static void add(Map<Block, Integer> counts, BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
			return;
		}
		counts.merge(block, 1, Integer::sum);
	}

	private static List<Entry> toEntries(Map<Block, Integer> counts, boolean placement) {
		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
			Block block = entry.getKey();
			ItemStack icon = new ItemStack(block.asItem());
			entries.add(new Entry(block.getName(), entry.getValue(), placement, icon));
		}
		entries.sort(Comparator.comparing(e -> e.name().getString(), String.CASE_INSENSITIVE_ORDER));
		return entries;
	}
}
