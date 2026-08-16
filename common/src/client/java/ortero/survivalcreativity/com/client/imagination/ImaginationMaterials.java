package ortero.survivalcreativity.com.client.imagination;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

	public record Summary(List<Entry> toPlace, List<Entry> toBreak, boolean supported) {
		public static Summary unsupported() {
			return new Summary(List.of(), List.of(), false);
		}

		public boolean isEmpty() {
			return toPlace.isEmpty() && toBreak.isEmpty();
		}

		public String asClipboardText(String hologramName) {
			if (!supported) {
				return hologramName + "\n(block list unavailable — older mod version)";
			}
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
			if (!supported) {
				messages.add("(block list unavailable — older mod version)");
				return messages;
			}
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
		PlayerMaterials materials = imagination.playerMaterials();
		if (materials == null) {
			return Summary.unsupported();
		}
		Summary summary = materials.toSummary();
		return new Summary(summary.toPlace(), summary.toBreak(), true);
	}
}
