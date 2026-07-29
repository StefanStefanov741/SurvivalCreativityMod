package ortero.survivalcreativity.com.neoforge.client;

import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import ortero.survivalcreativity.com.SurvivalCreativityMod;

public final class ModKeybindsNeoForge {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SurvivalCreativityMod.id("controls"));

	public static KeyMapping TOGGLE_EDIT;
	public static KeyMapping SAVE;
	public static KeyMapping OPEN_MENU;
	public static KeyMapping HIDE_PREVIEW;

	private ModKeybindsNeoForge() {
	}

	public static void register(RegisterKeyMappingsEvent event) {
		TOGGLE_EDIT = new KeyMapping(
			"key.survivalcreativitymod.toggle_edit",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			CATEGORY
		);
		SAVE = new KeyMapping(
			"key.survivalcreativitymod.save",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			CATEGORY
		);
		OPEN_MENU = new KeyMapping(
			"key.survivalcreativitymod.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			CATEGORY
		);
		HIDE_PREVIEW = new KeyMapping(
			"key.survivalcreativitymod.hide_preview",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_U,
			CATEGORY
		);
		event.register(TOGGLE_EDIT);
		event.register(SAVE);
		event.register(OPEN_MENU);
		event.register(HIDE_PREVIEW);
		event.registerCategory(CATEGORY);
	}
}
