package ortero.survivalcreativity.com.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import ortero.survivalcreativity.com.client.imagination.ImaginationInteractions;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.render.GhostBlockRenderer;

public class SurvivalCreativityModClient implements ClientModInitializer {
	private boolean wasInWorld;

	@Override
	public void onInitializeClient() {
		ModKeybinds.register();
		ImaginationInteractions.register();
		GhostBlockRenderer.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean inWorld = client.player != null && client.level != null;
			if (wasInWorld && !inWorld) {
				ImaginationManager.INSTANCE.onDisconnect();
			}
			wasInWorld = inWorld;

			if (!inWorld) {
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
