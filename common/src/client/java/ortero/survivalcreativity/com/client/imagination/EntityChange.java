package ortero.survivalcreativity.com.client.imagination;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * Entity difference for the imagination sandbox (item frames, paintings, armor stands, …).
 * <ul>
 *   <li>{@code placement == true} — entity exists only in the imagination</li>
 *   <li>{@code placement == false} — a real entity was removed while imagining (restored on exit)</li>
 * </ul>
 */
public record EntityChange(UUID uuid, boolean placement, CompoundTag data) {
	public static EntityChange place(UUID uuid, CompoundTag data) {
		return new EntityChange(uuid, true, data.copy());
	}

	public static EntityChange remove(UUID uuid, CompoundTag data) {
		return new EntityChange(uuid, false, data.copy());
	}

	public EntityChange withData(CompoundTag data) {
		return new EntityChange(uuid, placement, data.copy());
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("uuid", uuid.toString());
		tag.putBoolean("placement", placement);
		tag.put("data", data.copy());
		return tag;
	}

	public static @Nullable EntityChange load(CompoundTag tag) {
		String id = tag.getStringOr("uuid", "");
		if (id.isEmpty() || !tag.contains("data")) {
			return null;
		}
		try {
			return new EntityChange(
				UUID.fromString(id),
				tag.getBooleanOr("placement", true),
				tag.getCompoundOrEmpty("data").copy()
			);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
