package ortero.survivalcreativity.com.client.imagination;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/**
 * Snapshot of a cubic region so imagination can run as real creative,
 * then restore the survival world and keep only the diff as a hologram.
 */
public final class WorldSnapshot {
	public static final int DEFAULT_RADIUS = 64; // 4 chunks in each direction

	private final BlockPos origin;
	private final int radius;
	private final Map<Long, BlockState> blocks = new HashMap<>();
	private final Map<Long, CompoundTag> blockEntities = new HashMap<>();
	private final Map<UUID, CompoundTag> entities = new LinkedHashMap<>();

	private WorldSnapshot(BlockPos origin, int radius) {
		this.origin = origin.immutable();
		this.radius = radius;
	}

	public BlockPos origin() {
		return origin;
	}

	public int radius() {
		return radius;
	}

	public AABB bounds() {
		return new AABB(
			origin.getX() - radius, origin.getY() - radius, origin.getZ() - radius,
			origin.getX() + radius + 1, origin.getY() + radius + 1, origin.getZ() + radius + 1
		);
	}

	public static WorldSnapshot capture(Level level, Vec3 center, int radius, @Nullable UUID excludePlayer) {
		BlockPos origin = BlockPos.containing(center);
		WorldSnapshot snapshot = new WorldSnapshot(origin, radius);
		HolderLookup.Provider registries = level.registryAccess();

		for (BlockPos pos : BlockPos.betweenClosed(
			origin.getX() - radius, origin.getY() - radius, origin.getZ() - radius,
			origin.getX() + radius, origin.getY() + radius, origin.getZ() + radius
		)) {
			long key = pos.asLong();
			BlockState state = level.getBlockState(pos);
			snapshot.blocks.put(key, state);
			BlockEntity be = level.getBlockEntity(pos);
			CompoundTag beTag = BlockChange.saveBlockEntity(be, registries);
			if (beTag != null) {
				snapshot.blockEntities.put(key, beTag);
			}
		}

		for (Entity entity : level.getEntities(null, snapshot.bounds())) {
			if (entity instanceof Player) {
				continue;
			}
			if (excludePlayer != null && entity.getUUID().equals(excludePlayer)) {
				continue;
			}
			CompoundTag data = EntityNbt.save(entity);
			if (data != null) {
				snapshot.entities.put(entity.getUUID(), data);
			}
		}
		return snapshot;
	}

	/**
	 * Restore this snapshot on the integrated server (authoritative), then client follows via packets.
	 */
	public void restore(Minecraft client) {
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null || client.player == null) {
			restoreLocal(client.level);
			return;
		}
		UUID playerId = client.player.getUUID();
		server.execute(() -> {
			var serverPlayer = server.getPlayerList().getPlayer(playerId);
			if (serverPlayer == null) {
				return;
			}
			ServerLevel level = serverPlayer.level();
			restoreOnLevel(level, playerId);
		});
	}

	private void restoreLocal(Level level) {
		if (level == null) {
			return;
		}
		restoreOnLevel(level, null);
	}

	private void restoreOnLevel(Level level, @Nullable UUID playerId) {
		HolderLookup.Provider registries = level.registryAccess();

		for (Map.Entry<Long, BlockState> entry : blocks.entrySet()) {
			BlockPos pos = BlockPos.of(entry.getKey());
			level.setBlock(pos, entry.getValue(), 3);
			CompoundTag beTag = blockEntities.get(entry.getKey());
			if (beTag != null) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be != null) {
					try {
						var input = net.minecraft.world.level.storage.TagValueInput.create(
							net.minecraft.util.ProblemReporter.DISCARDING, registries, beTag);
						be.loadWithComponents(input);
						be.setChanged();
					} catch (Exception e) {
						SurvivalCreativityMod.LOGGER.warn("Failed to restore block entity at {}", pos, e);
					}
				}
			}
		}

		// Remove entities currently in the region (except players), then respawn snapshot entities
		for (Entity entity : level.getEntities(null, bounds())) {
			if (entity instanceof Player) {
				continue;
			}
			if (playerId != null && entity.getUUID().equals(playerId)) {
				continue;
			}
			entity.discard();
		}

		for (Map.Entry<UUID, CompoundTag> entry : entities.entrySet()) {
			Entity entity = EntityNbt.load(level, entry.getValue());
			if (entity == null) {
				continue;
			}
			entity.setUUID(entry.getKey());
			if (level instanceof ServerLevel serverLevel) {
				serverLevel.addFreshEntity(entity);
			} else if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
				ClientEntitySpawner.add(clientLevel, entity);
			}
		}
	}

	/**
	 * Apply an existing imagination onto the live world (for edit-existing).
	 */
	public static void applyImagination(Level level, Imagination imagination) {
		HolderLookup.Provider registries = level.registryAccess();
		for (Map.Entry<BlockPos, BlockChange> entry : imagination.changes().entrySet()) {
			BlockChange change = entry.getValue();
			BlockPos pos = entry.getKey();
			if (change.placement()) {
				level.setBlock(pos, change.imaginedState(), 3);
				if (change.imaginedBlockEntity() != null) {
					BlockEntity be = level.getBlockEntity(pos);
					if (be != null) {
						try {
							var input = net.minecraft.world.level.storage.TagValueInput.create(
								net.minecraft.util.ProblemReporter.DISCARDING, registries, change.imaginedBlockEntity());
							be.loadWithComponents(input);
							be.setChanged();
						} catch (Exception e) {
							SurvivalCreativityMod.LOGGER.warn("Failed to apply imagined BE at {}", pos, e);
						}
					}
				}
			} else {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}

		for (EntityChange change : imagination.entityChanges().values()) {
			if (!change.placement()) {
				Entity existing = findEntity(level, change.uuid());
				if (existing != null) {
					existing.discard();
				}
				continue;
			}
			if (findEntity(level, change.uuid()) != null) {
				continue;
			}
			Entity entity = EntityNbt.load(level, change.data());
			if (entity == null) {
				continue;
			}
			entity.setUUID(change.uuid());
			if (level instanceof ServerLevel serverLevel) {
				serverLevel.addFreshEntity(entity);
			} else if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
				ClientEntitySpawner.add(clientLevel, entity);
			}
		}
	}

	/**
	 * Build an imagination from differences between this snapshot and the live world.
	 */
	public Imagination createDiff(Level level, String name, UUID id, long createdAt) {
		Map<BlockPos, BlockChange> changes = new LinkedHashMap<>();
		Map<UUID, EntityChange> entityChanges = new LinkedHashMap<>();
		HolderLookup.Provider registries = level.registryAccess();

		for (Map.Entry<Long, BlockState> entry : blocks.entrySet()) {
			BlockPos pos = BlockPos.of(entry.getKey());
			BlockState original = entry.getValue();
			CompoundTag originalBe = blockEntities.get(entry.getKey());
			BlockState now = level.getBlockState(pos);
			CompoundTag nowBe = BlockChange.saveBlockEntity(level.getBlockEntity(pos), registries);

			if (original.equals(now) && Objects.equals(originalBe, nowBe)) {
				continue;
			}
			if (now.isAir()) {
				if (!original.isAir() || originalBe != null) {
					changes.put(pos.immutable(), BlockChange.remove(original, originalBe));
				}
			} else {
				changes.put(pos.immutable(), BlockChange.place(now, original, nowBe, originalBe));
			}
		}

		Map<UUID, Entity> live = new HashMap<>();
		for (Entity entity : level.getEntities(null, bounds())) {
			if (!ImaginedEntities.isTracked(entity)) {
				continue;
			}
			live.put(entity.getUUID(), entity);
		}

		for (Map.Entry<UUID, CompoundTag> entry : entities.entrySet()) {
			UUID uuid = entry.getKey();
			Entity now = live.remove(uuid);
			if (now == null) {
				if (ImaginedEntities.isTrackedData(entry.getValue(), level)) {
					entityChanges.put(uuid, EntityChange.remove(uuid, entry.getValue()));
				}
			} else {
				CompoundTag nowData = EntityNbt.save(now);
				if (nowData != null
					&& !ImaginedEntities.normalizeForCompare(nowData)
						.equals(ImaginedEntities.normalizeForCompare(entry.getValue()))) {
					entityChanges.put(uuid, EntityChange.place(uuid, nowData));
				}
			}
		}
		for (Map.Entry<UUID, Entity> entry : live.entrySet()) {
			CompoundTag data = EntityNbt.save(entry.getValue());
			if (data != null) {
				entityChanges.put(entry.getKey(), EntityChange.place(entry.getKey(), data));
			}
		}

		return new Imagination(id, name, createdAt, changes, entityChanges);
	}

	private static @Nullable Entity findEntity(Level level, UUID uuid) {
		if (level instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntityInAnyDimension(uuid);
			if (entity != null) {
				return entity;
			}
		}
		for (Entity entity : level.getEntities(null, new AABB(
			level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
			level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ()
		))) {
			if (entity.getUUID().equals(uuid)) {
				return entity;
			}
		}
		return null;
	}
}
