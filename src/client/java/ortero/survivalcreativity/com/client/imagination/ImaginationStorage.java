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

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

public final class ImaginationStorage {
	private ImaginationStorage() {
	}

	public static Path rootDir(Minecraft client) {
		return client.gameDirectory.toPath().resolve(SurvivalCreativityMod.MOD_ID).resolve("imaginations");
	}

	public static String worldKey(Minecraft client) {
		if (client.getSingleplayerServer() != null) {
			return sanitize(client.getSingleplayerServer().getWorldData().getLevelName());
		}
		ServerData server = client.getCurrentServer();
		if (server != null) {
			return sanitize(server.ip);
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
		try (var stream = Files.list(dir)) {
			stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
				.forEach(path -> read(path, level).ifPresent(result::add));
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
