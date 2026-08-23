package ortero.survivalcreativity.com.client.imagination;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class Imagination {
	private final UUID id;
	private String name;
	private final long createdAt;
	private final Map<BlockPos, BlockChange> changes;
	private final Map<UUID, EntityChange> entityChanges;
	/** Null = legacy save (no player-action materials tracking). */
	private @Nullable PlayerMaterials playerMaterials;
	/**
	 * Positions the player personally edited. Null = unknown (older 1.0.2/1.0.3 saves
	 * that only stored material tallies). Empty set = tracked, no edits yet.
	 */
	private @Nullable Set<BlockPos> playerEditedPositions;

	public Imagination(
		UUID id,
		String name,
		long createdAt,
		Map<BlockPos, BlockChange> changes,
		Map<UUID, EntityChange> entityChanges
	) {
		this(id, name, createdAt, changes, entityChanges, null, null);
	}

	public Imagination(
		UUID id,
		String name,
		long createdAt,
		Map<BlockPos, BlockChange> changes,
		Map<UUID, EntityChange> entityChanges,
		@Nullable PlayerMaterials playerMaterials
	) {
		this(id, name, createdAt, changes, entityChanges, playerMaterials, null);
	}

	public Imagination(
		UUID id,
		String name,
		long createdAt,
		Map<BlockPos, BlockChange> changes,
		Map<UUID, EntityChange> entityChanges,
		@Nullable PlayerMaterials playerMaterials,
		@Nullable Set<BlockPos> playerEditedPositions
	) {
		this.id = id;
		this.name = name;
		this.createdAt = createdAt;
		this.changes = new LinkedHashMap<>(changes);
		this.entityChanges = new LinkedHashMap<>(entityChanges);
		this.playerMaterials = playerMaterials;
		this.playerEditedPositions = playerEditedPositions == null
			? null
			: new LinkedHashSet<>(playerEditedPositions);
	}

	public static Imagination create(String name) {
		return new Imagination(
			UUID.randomUUID(),
			name,
			System.currentTimeMillis(),
			Map.of(),
			Map.of(),
			null,
			new LinkedHashSet<>()
		);
	}

	/** Mutable copy that keeps the same id (for overwrite-on-save when editing). */
	public Imagination copyForEdit() {
		return new Imagination(
			id,
			name,
			createdAt,
			new LinkedHashMap<>(changes),
			new LinkedHashMap<>(entityChanges),
			playerMaterials,
			playerEditedPositions == null ? null : new LinkedHashSet<>(playerEditedPositions)
		);
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long createdAt() {
		return createdAt;
	}

	public Map<BlockPos, BlockChange> changes() {
		return Collections.unmodifiableMap(changes);
	}

	public Map<UUID, EntityChange> entityChanges() {
		return Collections.unmodifiableMap(entityChanges);
	}

	public @Nullable PlayerMaterials playerMaterials() {
		return playerMaterials;
	}

	public void setPlayerMaterials(@Nullable PlayerMaterials playerMaterials) {
		this.playerMaterials = playerMaterials;
	}

	public boolean hasPlayerMaterials() {
		return playerMaterials != null;
	}

	public @Nullable Set<BlockPos> playerEditedPositions() {
		return playerEditedPositions == null ? null : Collections.unmodifiableSet(playerEditedPositions);
	}

	public void setPlayerEditedPositions(@Nullable Set<BlockPos> positions) {
		this.playerEditedPositions = positions == null ? null : new LinkedHashSet<>(positions);
	}

	public void put(BlockPos pos, BlockChange change) {
		changes.put(pos.immutable(), change);
	}

	public void remove(BlockPos pos) {
		changes.remove(pos);
	}

	public BlockChange get(BlockPos pos) {
		return changes.get(pos);
	}

	public void putEntity(EntityChange change) {
		entityChanges.put(change.uuid(), change);
	}

	public void removeEntity(UUID uuid) {
		entityChanges.remove(uuid);
	}

	public EntityChange getEntity(UUID uuid) {
		return entityChanges.get(uuid);
	}

	public boolean isEmpty() {
		return changes.isEmpty() && entityChanges.isEmpty();
	}

	/** Inclusive axis-aligned bounds of all block changes, or null if there are none. */
	public @Nullable BlockBounds blockBounds() {
		if (changes.isEmpty()) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : changes.keySet()) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		return new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public record BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id.toString());
		tag.putString("name", name);
		tag.putLong("createdAt", createdAt);

		ListTag list = new ListTag();
		for (Map.Entry<BlockPos, BlockChange> entry : changes.entrySet()) {
			CompoundTag entryTag = entry.getValue().save();
			BlockPos pos = entry.getKey();
			entryTag.putInt("x", pos.getX());
			entryTag.putInt("y", pos.getY());
			entryTag.putInt("z", pos.getZ());
			list.add(entryTag);
		}
		tag.put("changes", list);

		ListTag entities = new ListTag();
		for (EntityChange change : entityChanges.values()) {
			entities.add(change.save());
		}
		tag.put("entities", entities);

		if (playerMaterials != null) {
			tag.put("playerMaterials", playerMaterials.save());
		}
		if (playerEditedPositions != null) {
			ListTag edited = new ListTag();
			for (BlockPos pos : playerEditedPositions) {
				CompoundTag p = new CompoundTag();
				p.putInt("x", pos.getX());
				p.putInt("y", pos.getY());
				p.putInt("z", pos.getZ());
				edited.add(p);
			}
			tag.put("playerEdited", edited);
		}
		return tag;
	}

	public static Imagination load(CompoundTag tag, Level level) {
		UUID id = UUID.fromString(tag.getStringOr("id", UUID.randomUUID().toString()));
		String name = tag.getStringOr("name", "Untitled");
		long createdAt = tag.getLongOr("createdAt", System.currentTimeMillis());
		HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);

		Map<BlockPos, BlockChange> changes = new LinkedHashMap<>();
		ListTag list = tag.getListOrEmpty("changes");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entryTag = list.getCompoundOrEmpty(i);
			BlockPos pos = new BlockPos(
				entryTag.getIntOr("x", 0),
				entryTag.getIntOr("y", 0),
				entryTag.getIntOr("z", 0)
			);
			changes.put(pos, BlockChange.load(entryTag, blocks));
		}

		Map<UUID, EntityChange> entityChanges = new LinkedHashMap<>();
		ListTag entities = tag.getListOrEmpty("entities");
		for (int i = 0; i < entities.size(); i++) {
			EntityChange change = EntityChange.load(entities.getCompoundOrEmpty(i));
			if (change != null) {
				entityChanges.put(change.uuid(), change);
			}
		}

		PlayerMaterials materials = null;
		if (tag.contains("playerMaterials")) {
			materials = PlayerMaterials.load(tag.getCompoundOrEmpty("playerMaterials"));
		}

		Set<BlockPos> edited = null;
		if (tag.contains("playerEdited")) {
			edited = new LinkedHashSet<>();
			ListTag editedList = tag.getListOrEmpty("playerEdited");
			for (int i = 0; i < editedList.size(); i++) {
				CompoundTag p = editedList.getCompoundOrEmpty(i);
				edited.add(new BlockPos(
					p.getIntOr("x", 0),
					p.getIntOr("y", 0),
					p.getIntOr("z", 0)
				));
			}
		}

		return new Imagination(id, name, createdAt, changes, entityChanges, materials, edited);
	}
}
