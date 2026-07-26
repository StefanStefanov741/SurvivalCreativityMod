package ortero.survivalcreativity.com.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

public final class ModKeybinds {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SurvivalCreativityMod.id("controls"));

	public static KeyMapping TOGGLE_EDIT;
	public static KeyMapping SAVE;
	public static KeyMapping OPEN_MENU;
	public static KeyMapping HIDE_PREVIEW;

	private ModKeybinds() {
	}

	public static void register() {
		TOGGLE_EDIT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.survivalcreativitymod.toggle_edit",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			CATEGORY
		));
		SAVE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.survivalcreativitymod.save",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			CATEGORY
		));
		// H = holograms (avoid O/P which conflict with Friends / Social Interactions)
		OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.survivalcreativitymod.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			CATEGORY
		));
		HIDE_PREVIEW = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.survivalcreativitymod.hide_preview",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_U,
			CATEGORY
		));
	}
}
