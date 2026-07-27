package ortero.survivalcreativity.com.client.imagination;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
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

	public boolean contains(BlockPos pos) {
		return Math.abs(pos.getX() - origin.getX()) <= radius
			&& Math.abs(pos.getY() - origin.getY()) <= radius
			&& Math.abs(pos.getZ() - origin.getZ()) <= radius;
	}

	public @Nullable BlockState getBlock(BlockPos pos) {
		return blocks.get(pos.asLong());
	}

	public @Nullable CompoundTag getBlockEntity(BlockPos pos) {
		return blockEntities.get(pos.asLong());
	}

	/** Paste this snapshot into a client level (used when reapplying blocks locally). */
	public void pasteInto(net.minecraft.client.multiplayer.ClientLevel level) {
		HolderLookup.Provider registries = level.registryAccess();
		for (Map.Entry<Long, BlockState> entry : blocks.entrySet()) {
			BlockState state = entry.getValue();
			if (state.isAir()) {
				continue;
			}
			BlockPos pos = BlockPos.of(entry.getKey());
			level.setBlock(pos, state, 3);
			CompoundTag beTag = blockEntities.get(entry.getKey());
			if (beTag != null) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be != null) {
					try {
						var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, beTag);
						be.loadWithComponents(input);
						be.setChanged();
					} catch (Exception e) {
						SurvivalCreativityMod.LOGGER.warn("Failed to paste block entity at {}", pos, e);
					}
				}
			}
		}
		for (Map.Entry<UUID, CompoundTag> entry : entities.entrySet()) {
			Entity entity = EntityNbt.load(level, entry.getValue());
			if (entity == null) {
				continue;
			}
			entity.setUUID(entry.getKey());
			ClientEntitySpawner.add(level, entity);
		}
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
	 * Restore a specific set of positions to this snapshot (disconnect / exit).
	 * Must run on the server thread for singleplayer.
	 */
	public void restorePositions(Level level, java.util.Set<BlockPos> positions) {
		if (positions == null || positions.isEmpty()) {
			return;
		}
		HolderLookup.Provider registries = level.registryAccess();
		for (BlockPos pos : positions) {
			BlockState original = blocks.get(pos.asLong());
			if (original == null) {
				continue;
			}
			level.setBlock(pos, original, 3);
			CompoundTag beTag = blockEntities.get(pos.asLong());
			if (beTag != null) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be != null) {
					try {
						var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, beTag);
						be.loadWithComponents(input);
						be.setChanged();
					} catch (Exception e) {
						SurvivalCreativityMod.LOGGER.warn("Failed to restore block entity at {}", pos, e);
					}
				}
			}
		}
	}

	/**
	 * Build a revert/hologram diff from tracked dirty positions + a live level (client is fine).
	 * Does not require the integrated server thread.
	 */
	public Imagination createDiffFromPositions(
		Level level,
		java.util.Set<BlockPos> positions,
		String name,
		UUID id,
		long createdAt
	) {
		Map<BlockPos, BlockChange> changes = new LinkedHashMap<>();
		if (positions == null || positions.isEmpty() || level == null) {
			return new Imagination(id, name, createdAt, changes, Map.of());
		}
		HolderLookup.Provider registries = level.registryAccess();
		for (BlockPos pos : positions) {
			BlockState original = blocks.get(pos.asLong());
			if (original == null) {
				continue;
			}
			CompoundTag originalBe = blockEntities.get(pos.asLong());
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
		return new Imagination(id, name, createdAt, changes, Map.of());
	}

	/**
	 * Restore only what changed vs this snapshot (never rewrite the whole cube).
	 */
	public void restore(Minecraft client) {
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null || client.player == null) {
			return;
		}
		UUID playerId = client.player.getUUID();
		server.executeBlocking(() -> {
			var serverPlayer = server.getPlayerList().getPlayer(playerId);
			if (serverPlayer == null) {
				return;
			}
			revertDifferences(serverPlayer.level(), playerId);
		});
	}

	/**
	 * Revert blocks/entities that differ from this snapshot. Must run on the server thread for SP.
	 */
	public void revertDifferences(Level level, @Nullable UUID playerId) {
		Imagination diff = createDiff(level, "restore", UUID.randomUUID(), 0L);
		HolderLookup.Provider registries = level.registryAccess();

		if (!diff.changes().isEmpty()) {
			for (Map.Entry<BlockPos, BlockChange> entry : diff.changes().entrySet()) {
				BlockPos pos = entry.getKey();
				BlockChange change = entry.getValue();
				BlockState original = change.originalState();
				if (original == null) {
					original = blocks.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
				}
				level.setBlock(pos, original, 3);
				CompoundTag beTag = change.originalBlockEntity();
				if (beTag == null) {
					beTag = blockEntities.get(pos.asLong());
				}
				if (beTag != null) {
					BlockEntity be = level.getBlockEntity(pos);
					if (be != null) {
						try {
							var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, beTag);
							be.loadWithComponents(input);
							be.setChanged();
						} catch (Exception e) {
							SurvivalCreativityMod.LOGGER.warn("Failed to restore block entity at {}", pos, e);
						}
					}
				}
			}
		}

		// Always run — hologram diffs ignore mobs/items, but the real world must not keep them.
		revertEntities(level, playerId);
	}

	/**
	 * Discard entities spawned during imagination and re-add ones removed while editing.
	 * Applies to all non-player entities in the snapshot radius (mobs, items, etc.).
	 */
	public void revertEntities(Level level, @Nullable UUID playerId) {
		Map<UUID, Entity> live = new HashMap<>();
		for (Entity entity : level.getEntities(null, bounds())) {
			if (entity instanceof Player) {
				continue;
			}
			if (playerId != null && entity.getUUID().equals(playerId)) {
				continue;
			}
			live.put(entity.getUUID(), entity);
		}

		for (Entity entity : List.copyOf(live.values())) {
			if (!entities.containsKey(entity.getUUID())) {
				entity.discard();
				live.remove(entity.getUUID());
			}
		}

		for (Map.Entry<UUID, CompoundTag> entry : entities.entrySet()) {
			UUID uuid = entry.getKey();
			if (live.containsKey(uuid)) {
				continue;
			}
			Entity entity = EntityNbt.load(level, entry.getValue());
			if (entity == null) {
				continue;
			}
			entity.setUUID(uuid);
			if (level instanceof ServerLevel serverLevel) {
				serverLevel.addFreshEntity(entity);
			} else if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
				ClientEntitySpawner.add(clientLevel, entity);
			}
		}
	}

	/**
	 * Multiplayer exit: revert only locally edited blocks and drop client-only entities.
	 * Never wipe server-synced entities (leashes, mobs, etc.).
	 */
	public void restoreRemoteClient(
		net.minecraft.client.multiplayer.ClientLevel level,
		java.util.Set<BlockPos> dirtyBlocks
	) {
		if (level == null) {
			return;
		}
		// Roll back client block predictions. Place/break packets are blocked in imagination,
		// so the server never ACKs them — without this, predicted "fake" blocks linger until
		// the player interacts and a later ACK corrects the mesh.
		level.handleBlockChangedAck(Integer.MAX_VALUE);

		HolderLookup.Provider registries = level.registryAccess();
		// UPDATE_CLIENTS | UPDATE_NEIGHBORS | UPDATE_KNOWN_SHAPE (same as ClientLevel.syncBlockState)
		final int clientRestoreFlags = 19;
		for (BlockPos pos : dirtyBlocks) {
			BlockState original = blocks.get(pos.asLong());
			if (original == null) {
				continue;
			}
			BlockState before = level.getBlockState(pos);
			if (before.equals(original)) {
				// Still mark dirty — prediction rollback may have left a stale mesh.
				level.setBlocksDirty(pos, before, original);
				level.sendBlockUpdated(pos, before, original, clientRestoreFlags);
				continue;
			}
			level.setBlock(pos, original, clientRestoreFlags);
			level.setBlocksDirty(pos, before, original);
			CompoundTag beTag = blockEntities.get(pos.asLong());
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
			} else if (level.getBlockEntity(pos) != null && original.getBlock() != before.getBlock()) {
				level.removeBlockEntity(pos);
			}
		}

		for (Entity entity : List.copyOf(level.getEntities(null, bounds()))) {
			if (entity instanceof Player) {
				continue;
			}
			// Drop anything that wasn't present when imagination started (local spawns),
			// plus client-only ghosts (negative network IDs).
			if (entity.getId() < 0 || !entities.containsKey(entity.getUUID())) {
				entity.discard();
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
