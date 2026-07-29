package ortero.survivalcreativity.com.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.gui.ImaginationControlsScreen;
import ortero.survivalcreativity.com.client.imagination.ImaginationInteractions;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;
import ortero.survivalcreativity.com.client.imagination.ImaginationShare;
public final class ClientEvents {
	private ClientEvents() {
	}

	@EventBusSubscriber(modid = SurvivalCreativityMod.MOD_ID, value = Dist.CLIENT)
	public static class ModBusEvents {
		@SubscribeEvent
		public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
			ModKeybindsNeoForge.register(event);
		}
	}

	@EventBusSubscriber(modid = SurvivalCreativityMod.MOD_ID, value = Dist.CLIENT)
	public static class GameBusEvents {
		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.level == null) {
				return;
			}

			while (ModKeybindsNeoForge.TOGGLE_EDIT.consumeClick()) {
				ImaginationManager.INSTANCE.toggleEdit(client);
			}
			while (ModKeybindsNeoForge.SAVE.consumeClick()) {
				ImaginationManager.INSTANCE.requestSave(client);
			}
			while (ModKeybindsNeoForge.OPEN_MENU.consumeClick()) {
				ImaginationManager.INSTANCE.openMenu(client);
			}
			while (ModKeybindsNeoForge.HIDE_PREVIEW.consumeClick()) {
				ImaginationManager.INSTANCE.clearPreview(client, true);
			}

			ImaginationManager.INSTANCE.tick(client);
			ImaginationShare.tick(client);
			ImaginationInteractions.PendingPreviewPlace.tick(client);
		}

		@SubscribeEvent
		public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
			ImaginationInteractions.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
		}

		@SubscribeEvent
		public static void onScreenInit(ScreenEvent.Init.Post event) {
			if (!(event.getScreen() instanceof CreativeModeInventoryScreen) || !ImaginationManager.INSTANCE.isEditing()) {
				return;
			}
			int buttonWidth = 110;
			int x = (event.getScreen().width - buttonWidth) / 2;
			int y = 6;
			event.addListener(Button.builder(
				Component.translatable("screen.survivalcreativitymod.open_controls"),
				b -> Minecraft.getInstance().gui.setScreen(new ImaginationControlsScreen(event.getScreen()))
			).bounds(x, y, buttonWidth, 20).build());
		}

		@SubscribeEvent
		public static void onPlayerDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
			Minecraft client = Minecraft.getInstance();
			ImaginationManager.INSTANCE.abortSessionForDisconnect(client);
			ImaginationShare.clearPending();
		}

		@SubscribeEvent
		public static void onPlayerConnect(ClientPlayerNetworkEvent.LoggingIn event) {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> ImaginationManager.INSTANCE.applyPendingRecovery(client));
		}
	}
}
