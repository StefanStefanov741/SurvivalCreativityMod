package ortero.survivalcreativity.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;

import ortero.survivalcreativity.com.client.imagination.ImaginationShare;

@Mixin(Screen.class)
public class ScreenMixin {
	@Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
	private static void survivalcreativity$handleShareAdd(
		ClickEvent event,
		Minecraft minecraft,
		Screen activeScreen,
		CallbackInfo ci
	) {
		if (event instanceof ClickEvent.Custom custom && ImaginationShare.handleAddClick(minecraft, custom)) {
			ci.cancel();
		}
	}
}
