package ortero.survivalcreativity.com.client.imagination;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/**
 * Client preferences for hologram preview (opacity persisted; view filters are session-local).
 */
public final class HologramSettings {
	public static final int DEFAULT_OPACITY_PERCENT = 60;
	public static final int MIN_OPACITY_PERCENT = 10;
	public static final int MAX_OPACITY_PERCENT = 100;

	private static int opacityPercent = DEFAULT_OPACITY_PERCENT;
	private static boolean loaded;

	/** When true, cyan placement ghosts are hidden (mine/break outlines still show). */
	private static boolean hidePlacements;
	/** When true, red mine/break outlines are hidden (placement ghosts still show). */
	private static boolean hideBreaks;

	private static @Nullable Integer sliceMinX;
	private static @Nullable Integer sliceMaxX;
	private static @Nullable Integer sliceMinY;
	private static @Nullable Integer sliceMaxY;
	private static @Nullable Integer sliceMinZ;
	private static @Nullable Integer sliceMaxZ;

	private HologramSettings() {
	}

	public static int opacityPercent() {
		ensureLoaded();
		return opacityPercent;
	}

	/** 0–255 alpha used when drawing hologram blocks/fluids/outlines. */
	public static int opacityByte() {
		return Mth.clamp((int) Math.round(opacityPercent() * 255.0 / 100.0), 0, 255);
	}

	public static void setOpacityPercent(int percent) {
		opacityPercent = Mth.clamp(percent, MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT);
		save();
	}

	public static boolean hidePlacements() {
		return hidePlacements;
	}

	public static void setHidePlacements(boolean hide) {
		hidePlacements = hide;
	}

	public static void toggleHidePlacements() {
		hidePlacements = !hidePlacements;
	}

	public static boolean hideBreaks() {
		return hideBreaks;
	}

	public static void setHideBreaks(boolean hide) {
		hideBreaks = hide;
	}

	public static void toggleHideBreaks() {
		hideBreaks = !hideBreaks;
	}

	public static @Nullable Integer sliceMinX() {
		return sliceMinX;
	}

	public static @Nullable Integer sliceMaxX() {
		return sliceMaxX;
	}

	public static @Nullable Integer sliceMinY() {
		return sliceMinY;
	}

	public static @Nullable Integer sliceMaxY() {
		return sliceMaxY;
	}

	public static @Nullable Integer sliceMinZ() {
		return sliceMinZ;
	}

	public static @Nullable Integer sliceMaxZ() {
		return sliceMaxZ;
	}

	public static void setSliceMinX(@Nullable Integer value) {
		sliceMinX = value;
	}

	public static void setSliceMaxX(@Nullable Integer value) {
		sliceMaxX = value;
	}

	public static void setSliceMinY(@Nullable Integer value) {
		sliceMinY = value;
	}

	public static void setSliceMaxY(@Nullable Integer value) {
		sliceMaxY = value;
	}

	public static void setSliceMinZ(@Nullable Integer value) {
		sliceMinZ = value;
	}

	public static void setSliceMaxZ(@Nullable Integer value) {
		sliceMaxZ = value;
	}

	public static boolean hasSlice() {
		return sliceMinX != null || sliceMaxX != null
			|| sliceMinY != null || sliceMaxY != null
			|| sliceMinZ != null || sliceMaxZ != null;
	}

	public static void clearSlice() {
		sliceMinX = null;
		sliceMaxX = null;
		sliceMinY = null;
		sliceMaxY = null;
		sliceMinZ = null;
		sliceMaxZ = null;
	}

	/** Seed slice fields from a hologram's block extents (full bounds = starting slice). */
	public static void applySliceFromImagination(Imagination imagination) {
		if (imagination == null) {
			clearSlice();
			return;
		}
		Imagination.BlockBounds bounds = imagination.blockBounds();
		if (bounds == null) {
			clearSlice();
			return;
		}
		sliceMinX = bounds.minX();
		sliceMaxX = bounds.maxX();
		sliceMinY = bounds.minY();
		sliceMaxY = bounds.maxY();
		sliceMinZ = bounds.minZ();
		sliceMaxZ = bounds.maxZ();
	}

	/** Whether a block position falls inside the active slice (or no slice is set). */
	public static boolean isInSlice(BlockPos pos) {
		if (sliceMinX != null && pos.getX() < sliceMinX) {
			return false;
		}
		if (sliceMaxX != null && pos.getX() > sliceMaxX) {
			return false;
		}
		if (sliceMinY != null && pos.getY() < sliceMinY) {
			return false;
		}
		if (sliceMaxY != null && pos.getY() > sliceMaxY) {
			return false;
		}
		if (sliceMinZ != null && pos.getZ() < sliceMinZ) {
			return false;
		}
		if (sliceMaxZ != null && pos.getZ() > sliceMaxZ) {
			return false;
		}
		return true;
	}

	public static boolean isInSlice(Vec3 pos) {
		return isInSlice(BlockPos.containing(pos));
	}

	/**
	 * Nudge a slice bound by {@code delta}. Empty fields seed from the player's
	 * block position on that axis so the first click starts near where you stand.
	 */
	public static int nudgeSliceBound(@Nullable Integer current, int delta, char axis) {
		int base = current != null ? current : seedAxis(axis);
		return base + delta;
	}

	private static int seedAxis(char axis) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		BlockPos pos = BlockPos.containing(client.player.position());
		return switch (axis) {
			case 'x' -> pos.getX();
			case 'y' -> pos.getY();
			case 'z' -> pos.getZ();
			default -> 0;
		};
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = configFile();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			String raw = Files.readString(file).trim();
			if (!raw.isEmpty()) {
				opacityPercent = Mth.clamp(Integer.parseInt(raw), MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT);
			}
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to read hologram settings; using defaults", e);
		}
	}

	private static void save() {
		loaded = true;
		Path file = configFile();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, Integer.toString(opacityPercent) + System.lineSeparator());
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to save hologram settings", e);
		}
	}

	private static Path configFile() {
		return Minecraft.getInstance().gameDirectory.toPath()
			.resolve("config")
			.resolve(SurvivalCreativityMod.MOD_ID)
			.resolve("hologram_opacity.txt");
	}

	public static String opacityLabel() {
		return String.format(Locale.ROOT, "%d%%", opacityPercent());
	}
}
