package ortero.survivalcreativity.com.client.imagination;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/**
 * Survival inventory + gamemode + enter-position checkpoint written when entering imagination.
 * Applied on quit-before-save and again on rejoin if a force-quit skipped restore.
 */
public final class ImaginationRecovery {
	private static final String FILE_NAME = "active_session.nbt";

	private ImaginationRecovery() {
	}

	public static Path file(Minecraft client) {
		return ImaginationStorage.worldDir(client).resolve(FILE_NAME);
	}

	public static void write(
		Minecraft client,
		UUID playerId,
		GameType previousGameType,
		ItemStack[] inventory,
		int selectedSlot,
		@Nullable Vec3 bodyPosition,
		float bodyYRot,
		float bodyXRot
	) {
		if (client.level == null) {
			return;
		}
		try {
			Path dir = ImaginationStorage.worldDir(client);
			Files.createDirectories(dir);
			HolderLookup.Provider registries = client.level.registryAccess();
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);

			CompoundTag root = new CompoundTag();
			root.putString("player", playerId.toString());
			root.putString("gameType", previousGameType.getSerializedName());
			root.putInt("selectedSlot", selectedSlot);
			if (bodyPosition != null) {
				root.putDouble("x", bodyPosition.x);
				root.putDouble("y", bodyPosition.y);
				root.putDouble("z", bodyPosition.z);
				root.putFloat("yRot", bodyYRot);
				root.putFloat("xRot", bodyXRot);
			}

			ListTag items = new ListTag();
			for (ItemStack stack : inventory) {
				ItemStack safe = stack == null ? ItemStack.EMPTY : stack;
				Tag encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, safe).getOrThrow();
				items.add(encoded);
			}
			root.put("inventory", items);

			NbtIo.writeCompressed(root, file(client));
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to write imagination recovery checkpoint", e);
		}
	}

	public static void clear(Minecraft client) {
		try {
			Files.deleteIfExists(file(client));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to clear imagination recovery checkpoint", e);
		}
	}

	private static final String WORLD_REVERT_FILE = "pending_world_revert.nbt";

	public static Path worldRevertFile(Minecraft client) {
		return ImaginationStorage.worldDir(client).resolve(WORLD_REVERT_FILE);
	}

	public static boolean hasPendingWorldRevert(Minecraft client) {
		return Files.isRegularFile(worldRevertFile(client));
	}

	public static void writePendingWorldRevert(Minecraft client, Imagination diff) {
		if (diff == null || diff.isEmpty() || client.level == null) {
			return;
		}
		try {
			Files.createDirectories(ImaginationStorage.worldDir(client));
			NbtIo.writeCompressed(diff.save(), worldRevertFile(client));
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to write pending world revert", e);
		}
	}

	public static void clearPendingWorldRevert(Minecraft client) {
		try {
			Files.deleteIfExists(worldRevertFile(client));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to clear pending world revert", e);
		}
	}

	/**
	 * Undo imagined placements left in the real world after an interrupted session.
	 * Must run on the server thread for singleplayer.
	 * @return true if a pending file was present and applied (or was empty/invalid after read attempt)
	 */
	public static boolean applyPendingWorldRevert(net.minecraft.world.level.Level level, Minecraft client) {
		Path path = worldRevertFile(client);
		if (!Files.isRegularFile(path)) {
			return false;
		}
		try {
			CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			Imagination diff = Imagination.load(tag, level);
			HolderLookup.Provider registries = level.registryAccess();
			for (var entry : diff.changes().entrySet()) {
				net.minecraft.core.BlockPos pos = entry.getKey();
				BlockChange change = entry.getValue();
				var original = change.originalState();
				if (original == null) {
					original = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
				}
				level.setBlock(pos, original, 3);
				CompoundTag beTag = change.originalBlockEntity();
				if (beTag != null) {
					var be = level.getBlockEntity(pos);
					if (be != null) {
						try {
							var input = net.minecraft.world.level.storage.TagValueInput.create(
								net.minecraft.util.ProblemReporter.DISCARDING, registries, beTag);
							be.loadWithComponents(input);
							be.setChanged();
						} catch (Exception e) {
							SurvivalCreativityMod.LOGGER.warn("Failed to restore BE at {}", pos, e);
						}
					}
				}
			}
			for (EntityChange change : diff.entityChanges().values()) {
				if (change.placement()) {
					if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
						var entity = serverLevel.getEntityInAnyDimension(change.uuid());
						if (entity != null && !(entity instanceof Player)) {
							entity.discard();
						}
					}
				} else {
					Entity entity = EntityNbt.load(level, change.data());
					if (entity != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
						entity.setUUID(change.uuid());
						serverLevel.addFreshEntity(entity);
					}
				}
			}
			SurvivalCreativityMod.LOGGER.info("Applied pending world revert ({} blocks)", diff.changes().size());
			return true;
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to apply pending world revert", e);
			return false;
		}
	}

	public static boolean exists(Minecraft client) {
		return Files.isRegularFile(file(client));
	}

	public static boolean applyTo(Player player, Minecraft client) {
		Path path = file(client);
		if (!Files.isRegularFile(path) || player.level() == null) {
			return false;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			if (root.contains("player")) {
				UUID expected = UUID.fromString(root.getString("player").orElse(""));
				if (!expected.equals(player.getUUID())) {
					return false;
				}
			}

			HolderLookup.Provider registries = player.level().registryAccess();
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);

			GameType gameType = GameType.byName(
				root.getString("gameType").orElse("survival"),
				GameType.SURVIVAL
			);

			ListTag items = root.getListOrEmpty("inventory");
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
			int size = Math.min(items.size(), inventory.getContainerSize());
			for (int i = 0; i < size; i++) {
				Tag tag = items.get(i);
				ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
				inventory.setItem(i, stack);
			}
			inventory.setSelectedSlot(root.getIntOr("selectedSlot", 0));

			if (root.contains("x") && root.contains("y") && root.contains("z")) {
				double x = root.getDoubleOr("x", player.getX());
				double y = root.getDoubleOr("y", player.getY());
				double z = root.getDoubleOr("z", player.getZ());
				float yRot = root.getFloatOr("yRot", player.getYRot());
				float xRot = root.getFloatOr("xRot", player.getXRot());
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.teleportTo(x, y, z);
					serverPlayer.setYRot(yRot);
					serverPlayer.setXRot(xRot);
					serverPlayer.setDeltaMovement(Vec3.ZERO);
				} else {
					player.snapTo(x, y, z, yRot, xRot);
					player.setDeltaMovement(Vec3.ZERO);
				}
			}

			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.setGameMode(gameType);
			} else {
				Minecraft mc = Minecraft.getInstance();
				if (mc.gameMode != null) {
					mc.gameMode.setLocalMode(gameType);
				}
				var abilities = player.getAbilities();
				gameType.updatePlayerAbilities(abilities);
				abilities.flying = false;
			}

			SurvivalCreativityMod.LOGGER.info(
				"Restored survival checkpoint for {} ({})",
				player.getPlainTextName(),
				gameType.getSerializedName()
			);
			return true;
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to apply imagination recovery checkpoint", e);
			return false;
		}
	}

	public static Optional<GameType> peekGameType(Minecraft client) {
		Path path = file(client);
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			return Optional.of(GameType.byName(root.getString("gameType").orElse("survival"), GameType.SURVIVAL));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
