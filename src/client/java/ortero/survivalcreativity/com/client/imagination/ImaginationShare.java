package ortero.survivalcreativity.com.client.imagination;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.gui.SaveImaginationScreen;
import ortero.survivalcreativity.com.network.ShareImaginationC2SPayload;
import ortero.survivalcreativity.com.network.ShareImaginationS2CPayload;

/**
 * Client-side share/import of imaginations (Xaero-style chat Add button).
 * Requires the mod on the server (or LAN host) so packets can be relayed.
 */
public final class ImaginationShare {
	public static final Identifier ADD_CLICK_ID = SurvivalCreativityMod.id("add_shared_imagination");

	private static final Map<UUID, PendingShare> PENDING = new ConcurrentHashMap<>();

	private ImaginationShare() {
	}

	public record PendingShare(String senderName, String imaginationName, CompoundTag data) {
	}

	public static void clearPending() {
		PENDING.clear();
	}

	public static void registerClient() {
		ClientPlayNetworking.registerGlobalReceiver(ShareImaginationS2CPayload.TYPE, (payload, context) -> {
			PENDING.put(payload.shareId(), new PendingShare(
				payload.senderName(),
				payload.imaginationName(),
				payload.data().copy()
			));
			if (context.player() != null) {
				context.player().sendSystemMessage(buildShareMessage(
					payload.senderName(),
					payload.imaginationName(),
					payload.shareId()
				));
			}
		});
	}

	public static void share(Minecraft client, Imagination imagination) {
		if (client.player == null || client.getConnection() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(ShareImaginationC2SPayload.TYPE)) {
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_needs_server"));
			return;
		}
		ClientPlayNetworking.send(new ShareImaginationC2SPayload(
			UUID.randomUUID(),
			imagination.name(),
			imagination.save()
		));
		client.player.sendSystemMessage(
			Component.translatable("message.survivalcreativitymod.share_sent", imagination.name())
				.withStyle(ChatFormatting.GRAY)
		);
		client.player.sendOverlayMessage(
			Component.translatable("message.survivalcreativitymod.share_sent_overlay", imagination.name()));
	}

	public static boolean handleAddClick(Minecraft client, ClickEvent.Custom custom) {
		if (!ADD_CLICK_ID.equals(custom.id())) {
			return false;
		}
		Optional<Tag> payload = custom.payload();
		if (payload.isEmpty()) {
			return true;
		}
		String raw = payload.get().asString().orElse("");
		UUID shareId;
		try {
			shareId = UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			return true;
		}
		PendingShare pending = PENDING.get(shareId);
		if (pending == null) {
			if (client.player != null) {
				client.player.sendOverlayMessage(
					Component.translatable("message.survivalcreativitymod.share_expired"));
			}
			return true;
		}
		client.gui.setScreen(new SaveImaginationScreen(
			pending.imaginationName(),
			name -> importPending(client, shareId, name),
			Component.translatable("screen.survivalcreativitymod.import_title")
		));
		return true;
	}

	private static void importPending(Minecraft client, UUID shareId, String name) {
		PendingShare pending = PENDING.remove(shareId);
		if (pending == null || client.level == null || client.player == null) {
			return;
		}
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			trimmed = pending.imaginationName();
		}
		try {
			Imagination imported = copyAsNew(pending.data(), trimmed, client.level);
			ImaginationStorage.save(client, imported);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_imported", trimmed));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to import shared imagination", e);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_import_failed"));
		}
	}

	public static Imagination copyAsNew(CompoundTag data, String name, Level level) {
		Imagination loaded = Imagination.load(data, level);
		return new Imagination(
			UUID.randomUUID(),
			name,
			System.currentTimeMillis(),
			loaded.changes(),
			loaded.entityChanges()
		);
	}

	private static Component buildShareMessage(String senderName, String imaginationName, UUID shareId) {
		MutableComponent add = Component.translatable("message.survivalcreativitymod.share_add")
			.withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.Custom(ADD_CLICK_ID, Optional.of(StringTag.valueOf(shareId.toString()))))
				.withHoverEvent(new HoverEvent.ShowText(
					Component.translatable("message.survivalcreativitymod.share_add_hover"))));

		return Component.translatable(
			"message.survivalcreativitymod.share_chat",
			senderName,
			imaginationName
		).withStyle(ChatFormatting.AQUA).append(" ").append(add);
	}
}
