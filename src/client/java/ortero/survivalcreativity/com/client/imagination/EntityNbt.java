package ortero.survivalcreativity.com.client.imagination;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

public final class EntityNbt {
	private static final Logger LOGGER = SurvivalCreativityMod.LOGGER;

	private EntityNbt() {
	}

	public static @Nullable CompoundTag save(Entity entity) {
		try {
			HolderLookup.Provider registries = entity.level().registryAccess();
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			if (!entity.save(output)) {
				return null;
			}
			return output.buildResult();
		} catch (Exception e) {
			LOGGER.warn("Failed to save imagined entity {}", entity, e);
			return null;
		}
	}

	public static @Nullable Entity load(Level level, CompoundTag tag) {
		try {
			HolderLookup.Provider registries = level.registryAccess();
			var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
			Entity entity = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
			return entity;
		} catch (Exception e) {
			LOGGER.warn("Failed to load imagined entity", e);
			return null;
		}
	}

	/**
	 * Sync previous/current pose so hologram extract doesn't rotLerp every frame.
	 * ArmorStand overrides {@code setYBodyRot}/{@code setYHeadRot} and does not write
	 * {@code yBodyRot} — set the fields directly or holograms face the wrong way (often 180°).
	 */
	public static void freezeForHologram(Entity entity) {
		entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		float yRot = entity.getYRot();
		float xRot = entity.getXRot();
		entity.yRotO = yRot;
		entity.xRotO = xRot;
		if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
			living.yBodyRot = yRot;
			living.yBodyRotO = yRot;
			living.yHeadRot = yRot;
			living.yHeadRotO = yRot;
		}
	}
}
