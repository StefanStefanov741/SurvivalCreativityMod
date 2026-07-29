package ortero.survivalcreativity.com.client.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;

import ortero.survivalcreativity.com.client.imagination.ImaginationShare;

@Mixin(ChatListener.class)
public class ChatListenerMixin {
	@Inject(method = "handlePlayerChatMessage", at = @At("HEAD"), cancellable = true)
	private void survivalcreativity$absorbShareChat(
		PlayerChatMessage message,
		GameProfile sender,
		ChatType.Bound boundChatType,
		CallbackInfo ci
	) {
		String content = message.signedContent();
		String senderName = sender.name();
		UUID senderId = sender.id();
		if (ImaginationShare.handleIncomingChat(content, senderName, senderId)) {
			ci.cancel();
		}
	}
}
