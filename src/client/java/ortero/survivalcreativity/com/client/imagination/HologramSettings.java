package ortero.survivalcreativity.com.client.imagination;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/**
 * Client preferences for hologram preview (persisted under the mod config folder).
 */
public final class HologramSettings {
	public static final int DEFAULT_OPACITY_PERCENT = 60;
	public static final int MIN_OPACITY_PERCENT = 10;
	public static final int MAX_OPACITY_PERCENT = 100;

	private static int opacityPercent = DEFAULT_OPACITY_PERCENT;
	private static boolean loaded;

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
