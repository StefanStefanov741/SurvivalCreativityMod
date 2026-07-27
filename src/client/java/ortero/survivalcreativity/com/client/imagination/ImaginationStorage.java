package ortero.survivalcreativity.com.client.imagination;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

public final class ImaginationStorage {
	private ImaginationStorage() {
	}

	public static Path rootDir(Minecraft client) {
		return client.gameDirectory.toPath().resolve(SurvivalCreativityMod.MOD_ID).resolve("imaginations");
	}

	/**
	 * Stable folder key for the current world/server.
	 * Singleplayer uses the save folder name; multiplayer uses the server address.
	 */
	public static String worldKey(Minecraft client) {
		MinecraftServer integrated = client.getSingleplayerServer();
		if (integrated != null) {
			Path worldRoot = integrated.getWorldPath(LevelResource.ROOT);
			Path folder = worldRoot.getFileName();
			if (folder != null && !folder.toString().isBlank()) {
				return sanitize(folder.toString());
			}
			return sanitize(integrated.getWorldData().getLevelName());
		}
		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return sanitize(server.ip);
		}
		return "unknown";
	}

	/** Human-readable label shown in the imaginations menu. */
	public static String worldLabel(Minecraft client) {
		MinecraftServer integrated = client.getSingleplayerServer();
		if (integrated != null) {
			return integrated.getWorldData().getLevelName();
		}
		ServerData server = client.getCurrentServer();
		if (server != null) {
			if (server.name != null && !server.name.isBlank()) {
				return server.name + " (" + server.ip + ")";
			}
			return server.ip != null ? server.ip : "unknown";
		}
		return "unknown";
	}

	public static Path worldDir(Minecraft client) {
		return rootDir(client).resolve(worldKey(client));
	}

	public static List<Imagination> loadAll(Minecraft client, Level level) {
		Path dir = worldDir(client);
		List<Imagination> result = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return result;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(path -> {
				String name = path.getFileName().toString();
				return name.endsWith(".nbt")
					&& !name.equals("active_session.nbt")
					&& !name.equals("pending_world_revert.nbt");
			}).forEach(path -> read(path, level).ifPresent(result::add));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to list imaginations", e);
		}
		result.sort(Comparator.comparingLong(Imagination::createdAt).reversed());
		return result;
	}

	public static void save(Minecraft client, Imagination imagination) throws IOException {
		Path dir = worldDir(client);
		Files.createDirectories(dir);
		Path file = dir.resolve(imagination.id() + ".nbt");
		NbtIo.writeCompressed(imagination.save(), file);
	}

	public static void delete(Minecraft client, UUID id) throws IOException {
		Path file = worldDir(client).resolve(id + ".nbt");
		Files.deleteIfExists(file);
	}

	private static Optional<Imagination> read(Path path, Level level) {
		try {
			CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			return Optional.of(Imagination.load(tag, level));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to read imagination {}", path, e);
			return Optional.empty();
		}
	}

	private static String sanitize(String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}
}
