package ortero.survivalcreativity.com.client.imagination;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Only player-placed “build” entities belong in holograms.
 * Drops, roaming mobs, projectiles, etc. are ignored for diffs.
 */
public final class ImaginedEntities {
	private ImaginedEntities() {
	}

	public static boolean isTracked(@Nullable Entity entity) {
		if (entity == null || entity instanceof Player) {
			return false;
		}
		return isTrackedType(entity.getType());
	}

	public static boolean isTrackedType(EntityType<?> type) {
		return type == EntityTypes.ARMOR_STAND
			|| type == EntityTypes.ITEM_FRAME
			|| type == EntityTypes.GLOW_ITEM_FRAME
			|| type == EntityTypes.PAINTING
			|| type == EntityTypes.LEASH_KNOT
			|| type == EntityTypes.BLOCK_DISPLAY
			|| type == EntityTypes.ITEM_DISPLAY
			|| type == EntityTypes.TEXT_DISPLAY;
	}

	public static boolean isTrackedData(CompoundTag tag, Level level) {
		try {
			HolderLookup.Provider registries = level.registryAccess();
			var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
			return EntityType.by(input).map(ImaginedEntities::isTrackedType).orElse(false);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Drop volatile fields so idle entities (air ticks, motion, etc.) are not treated as edits.
	 */
	public static CompoundTag normalizeForCompare(CompoundTag tag) {
		CompoundTag copy = tag.copy();
		copy.remove("Air");
		copy.remove("Fire");
		copy.remove("HurtTime");
		copy.remove("HurtByTimestamp");
		copy.remove("DeathTime");
		copy.remove("FallDistance");
		copy.remove("Motion");
		copy.remove("OnGround");
		copy.remove("PortalCooldown");
		copy.remove("TicksFrozen");
		copy.remove("Brain");
		copy.remove("attributes");
		copy.remove("Attributes");
		return copy;
	}
}
