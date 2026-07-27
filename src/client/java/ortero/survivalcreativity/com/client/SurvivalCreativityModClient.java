package ortero.survivalcreativity.com.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;

import ortero.survivalcreativity.com.client.gui.ImaginationControlsScreen;
import ortero.survivalcreativity.com.client.imagination.ImaginationInteractions;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.render.GhostBlockRenderer;

public class SurvivalCreativityModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModKeybinds.register();
		ImaginationInteractions.register();
		GhostBlockRenderer.register();

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof CreativeModeInventoryScreen) || !ImaginationManager.INSTANCE.isEditing()) {
				return;
			}
			int buttonWidth = 110;
			int x = (scaledWidth - buttonWidth) / 2;
			int y = 6;
			Screens.getWidgets(screen).add(Button.builder(
				Component.translatable("screen.survivalcreativitymod.open_controls"),
				b -> client.gui.setScreen(new ImaginationControlsScreen(screen))
			).bounds(x, y, buttonWidth, 20).build());
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ImaginationManager.INSTANCE.abortSessionForDisconnect(client);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			client.execute(() -> ImaginationManager.INSTANCE.applyPendingRecovery(client));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				return;
			}

			while (ModKeybinds.TOGGLE_EDIT.consumeClick()) {
				ImaginationManager.INSTANCE.toggleEdit(client);
			}
			while (ModKeybinds.SAVE.consumeClick()) {
				ImaginationManager.INSTANCE.requestSave(client);
			}
			while (ModKeybinds.OPEN_MENU.consumeClick()) {
				ImaginationManager.INSTANCE.openMenu(client);
			}
			while (ModKeybinds.HIDE_PREVIEW.consumeClick()) {
				ImaginationManager.INSTANCE.clearPreview(client, true);
			}

			ImaginationManager.INSTANCE.tick(client);
			ImaginationInteractions.PendingPreviewPlace.tick(client);
		});
	}
}
