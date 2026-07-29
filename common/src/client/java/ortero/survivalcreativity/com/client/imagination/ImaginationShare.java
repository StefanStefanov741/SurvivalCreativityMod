package ortero.survivalcreativity.com.client.imagination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.PlatformHelper;
import ortero.survivalcreativity.com.client.gui.SaveImaginationScreen;
import ortero.survivalcreativity.com.network.ShareImaginationC2SPayload;
import ortero.survivalcreativity.com.network.ShareImaginationS2CPayload;

/**
 * Share imaginations:
 * - Small builds: chat instructions (no server mod).
 * - Large builds: server packet relay (mod required on server / LAN host).
 * Receivers reconstruct from place/break data + their loaded world when near the build.
 */
public final class ImaginationShare {
	public static final Identifier ADD_CLICK_ID = SurvivalCreativityMod.id("add_shared_imagination");

	private static final String CHAT_PREFIX = "#SCM1|";
	private static final int MAX_PARTS = 64;
	private static final int SEND_INTERVAL_TICKS = 4;
	/** Player must be this close to the build center (same ballpark as imagination radius). */
	private static final double MAX_IMPORT_DISTANCE = WorldSnapshot.DEFAULT_RADIUS + 16.0;

	private static final Map<UUID, PendingShare> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, Assembly> ASSEMBLING = new ConcurrentHashMap<>();

	private static final List<String> OUTBOX = new ArrayList<>();
	private static int outboxCooldown;

	private ImaginationShare() {
	}

	public record PendingShare(
		String senderName,
		String imaginationName,
		BlockPos center,
		List<Instruction> instructions
	) {
	}

	public record Instruction(
		BlockPos pos,
		boolean placement,
		BlockState imagined,
		@Nullable CompoundTag imaginedBe
	) {
	}

	private static final class Assembly {
		final String senderName;
		final UUID senderId;
		final String[] parts;
		int received;

		Assembly(String senderName, UUID senderId, int total) {
			this.senderName = senderName;
			this.senderId = senderId;
			this.parts = new String[total];
		}
	}

	public static void clearPending() {
		PENDING.clear();
		ASSEMBLING.clear();
		OUTBOX.clear();
		outboxCooldown = 0;
	}

	public static void registerClient() { }

	public static void handleLargeShareReceived(UUID shareId, String senderName, String imaginationName, CompoundTag data) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		try {
			PendingShare pending = fromBlueprint(data, senderName, client.level);
			PENDING.put(shareId, pending);
			client.player.sendSystemMessage(buildShareMessage(
				senderName,
				imaginationName,
				pending.center(),
				shareId
			));
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to accept large shared imagination", e);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_decode_failed"));
		}
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.getConnection() == null) {
			OUTBOX.clear();
			outboxCooldown = 0;
			return;
		}
		if (OUTBOX.isEmpty()) {
			return;
		}
		if (outboxCooldown > 0) {
			outboxCooldown--;
			return;
		}
		String next = OUTBOX.remove(0);
		client.getConnection().sendChat(next);
		outboxCooldown = SEND_INTERVAL_TICKS;
		if (OUTBOX.isEmpty() && client.player != null) {
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_sent_overlay"));
		}
	}

	public static void share(Minecraft client, Imagination imagination) {
		if (client.player == null || client.getConnection() == null) {
			return;
		}
		if (imagination.changes().isEmpty()) {
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_empty"));
			return;
		}
		if (!OUTBOX.isEmpty()) {
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_busy"));
			return;
		}

		try {
			CompoundTag blueprint = toBlueprint(imagination);
			List<String> parts = encodeParts(blueprint);
			if (parts.size() > MAX_PARTS) {
				shareLargeViaServer(client, imagination, blueprint, parts.size());
				return;
			}
			String shareKey = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
			OUTBOX.clear();
			for (int i = 0; i < parts.size(); i++) {
				OUTBOX.add(CHAT_PREFIX + shareKey + "|" + i + "|" + parts.size() + "|" + parts.get(i));
			}
			outboxCooldown = 0;
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_sending", imagination.name(), parts.size()));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to encode imagination share", e);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_encode_failed"));
		}
	}

	private static void shareLargeViaServer(
		Minecraft client,
		Imagination imagination,
		CompoundTag blueprint,
		int chatPartsNeeded
	) {
		String announce = Component.translatable(
			"message.survivalcreativitymod.share_large_chat",
			imagination.name()
		).getString();
		client.getConnection().sendChat(announce);

		if (!PlatformHelper.INSTANCE.canSendC2S(ShareImaginationC2SPayload.TYPE)) {
			client.player.sendSystemMessage(
				Component.translatable("message.survivalcreativitymod.share_needs_server_large", chatPartsNeeded, MAX_PARTS)
					.withStyle(ChatFormatting.RED));
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_needs_server"));
			return;
		}

		PlatformHelper.INSTANCE.sendC2S(new ShareImaginationC2SPayload(
			UUID.randomUUID(),
			imagination.name(),
			blueprint
		));
		client.player.sendSystemMessage(
			Component.translatable("message.survivalcreativitymod.share_large_sent", imagination.name())
				.withStyle(ChatFormatting.GRAY));
		client.player.sendOverlayMessage(
			Component.translatable("message.survivalcreativitymod.share_sent_overlay"));
	}

	/**
	 * @return true if this chat line was a share fragment and should be hidden
	 */
	public static boolean handleIncomingChat(String signedContent, String senderName, UUID senderId) {
		if (signedContent == null || !signedContent.startsWith(CHAT_PREFIX)) {
			return false;
		}
		String[] bits = signedContent.substring(CHAT_PREFIX.length()).split("\\|", 4);
		if (bits.length != 4) {
			return true;
		}
		String shareKey = bits[0];
		int index;
		int total;
		try {
			index = Integer.parseInt(bits[1]);
			total = Integer.parseInt(bits[2]);
		} catch (NumberFormatException e) {
			return true;
		}
		if (total <= 0 || total > MAX_PARTS || index < 0 || index >= total) {
			return true;
		}

		Assembly assembly = ASSEMBLING.computeIfAbsent(shareKey, k -> new Assembly(senderName, senderId, total));
		if (assembly.parts.length != total || assembly.parts[index] != null) {
			return true;
		}
		assembly.parts[index] = bits[3];
		assembly.received++;
		if (assembly.received < total) {
			return true;
		}

		ASSEMBLING.remove(shareKey);
		try {
			CompoundTag blueprint = decodeParts(List.of(assembly.parts));
			PendingShare pending = fromBlueprint(blueprint, assembly.senderName, Minecraft.getInstance().level);
			UUID shareId = UUID.nameUUIDFromBytes(("scm-share-" + shareKey).getBytes());
			PENDING.put(shareId, pending);

			Minecraft client = Minecraft.getInstance();
			boolean self = client.player != null && client.player.getUUID().equals(assembly.senderId);
			if (self) {
				if (client.player != null) {
					client.player.sendSystemMessage(
						Component.translatable("message.survivalcreativitymod.share_sent", pending.imaginationName())
							.withStyle(ChatFormatting.GRAY));
				}
			} else if (client.player != null) {
				client.player.sendSystemMessage(buildShareMessage(
					pending.senderName(),
					pending.imaginationName(),
					pending.center(),
					shareId
				));
			}
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to decode shared imagination", e);
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.sendOverlayMessage(
					Component.translatable("message.survivalcreativitymod.share_decode_failed"));
			}
		}
		return true;
	}

	public static boolean handleAddClick(Minecraft client, ClickEvent.Custom custom) {
		if (!ADD_CLICK_ID.equals(custom.id())) {
			return false;
		}
		Optional<Tag> payload = custom.payload();
		if (payload.isEmpty()) {
			return true;
		}
		UUID shareId;
		try {
			shareId = UUID.fromString(payload.get().asString().orElse(""));
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

		String tooFar = proximityError(client, pending);
		if (tooFar != null) {
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(tooFar).withStyle(ChatFormatting.RED));
				client.player.sendOverlayMessage(
					Component.translatable(
						"message.survivalcreativitymod.share_too_far",
						pending.center().getX(),
						pending.center().getY(),
						pending.center().getZ()
					));
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

	private static @Nullable String proximityError(Minecraft client, PendingShare pending) {
		Player player = client.player;
		Level level = client.level;
		if (player == null || level == null) {
			return Component.translatable("message.survivalcreativitymod.share_import_failed").getString();
		}
		BlockPos center = pending.center();
		double distSq = player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
		if (distSq > MAX_IMPORT_DISTANCE * MAX_IMPORT_DISTANCE) {
			return Component.translatable(
				"message.survivalcreativitymod.share_too_far_detail",
				pending.center().getX(),
				pending.center().getY(),
				pending.center().getZ(),
				(int) Math.sqrt(distSq)
			).getString();
		}
		for (Instruction instruction : pending.instructions()) {
			if (!level.isLoaded(instruction.pos())) {
				return Component.translatable(
					"message.survivalcreativitymod.share_chunks_unloaded",
					pending.center().getX(),
					pending.center().getY(),
					pending.center().getZ()
				).getString();
			}
		}
		return null;
	}

	private static void importPending(Minecraft client, UUID shareId, String name) {
		PendingShare pending = PENDING.get(shareId);
		if (pending == null || client.level == null || client.player == null) {
			return;
		}
		String tooFar = proximityError(client, pending);
		if (tooFar != null) {
			client.player.sendSystemMessage(Component.literal(tooFar).withStyle(ChatFormatting.RED));
			return;
		}
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			trimmed = pending.imaginationName();
		}
		try {
			Imagination imported = reconstruct(pending, trimmed, client.level);
			ImaginationStorage.save(client, imported);
			PENDING.remove(shareId);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_imported", trimmed));
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to import shared imagination", e);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.share_import_failed"));
		}
	}

	static Imagination reconstruct(PendingShare pending, String name, Level level) {
		HolderLookup.Provider registries = level.registryAccess();
		Map<BlockPos, BlockChange> changes = new LinkedHashMap<>();
		for (Instruction instruction : pending.instructions()) {
			BlockPos pos = instruction.pos();
			BlockState original = level.getBlockState(pos);
			CompoundTag originalBe = BlockChange.saveBlockEntity(level.getBlockEntity(pos), registries);
			if (instruction.placement()) {
				changes.put(pos, BlockChange.place(
					instruction.imagined(),
					original,
					instruction.imaginedBe(),
					originalBe
				));
			} else {
				changes.put(pos, BlockChange.remove(original, originalBe));
			}
		}
		return new Imagination(UUID.randomUUID(), name, System.currentTimeMillis(), changes, Map.of());
	}

	private static CompoundTag toBlueprint(Imagination imagination) {
		BlockPos center = computeCenter(imagination.changes().keySet());
		CompoundTag root = new CompoundTag();
		root.putString("n", imagination.name());
		root.putInt("cx", center.getX());
		root.putInt("cy", center.getY());
		root.putInt("cz", center.getZ());
		ListTag list = new ListTag();
		for (Map.Entry<BlockPos, BlockChange> entry : imagination.changes().entrySet()) {
			BlockPos pos = entry.getKey();
			BlockChange change = entry.getValue();
			CompoundTag tag = new CompoundTag();
			tag.putInt("x", pos.getX());
			tag.putInt("y", pos.getY());
			tag.putInt("z", pos.getZ());
			tag.putBoolean("p", change.placement());
			tag.put("i", NbtUtils.writeBlockState(change.imaginedState()));
			if (change.imaginedBlockEntity() != null) {
				tag.put("b", change.imaginedBlockEntity().copy());
			}
			list.add(tag);
		}
		root.put("c", list);
		return root;
	}

	private static PendingShare fromBlueprint(CompoundTag root, String senderName, @Nullable Level level) {
		if (level == null) {
			throw new IllegalStateException("Cannot decode share without a level");
		}
		String name = root.getStringOr("n", "Shared");
		BlockPos center = new BlockPos(
			root.getIntOr("cx", 0),
			root.getIntOr("cy", 0),
			root.getIntOr("cz", 0)
		);
		HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
		List<Instruction> instructions = new ArrayList<>();
		ListTag list = root.getListOrEmpty("c");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompoundOrEmpty(i);
			BlockPos pos = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
			boolean placement = tag.getBooleanOr("p", true);
			BlockState imagined = NbtUtils.readBlockState(blocks, tag.getCompoundOrEmpty("i"));
			CompoundTag be = tag.contains("b") ? tag.getCompoundOrEmpty("b").copy() : null;
			instructions.add(new Instruction(pos, placement, imagined, be));
		}
		return new PendingShare(senderName, name, center, instructions);
	}

	private static BlockPos computeCenter(Iterable<BlockPos> positions) {
		long x = 0;
		long y = 0;
		long z = 0;
		int n = 0;
		for (BlockPos pos : positions) {
			x += pos.getX();
			y += pos.getY();
			z += pos.getZ();
			n++;
		}
		if (n == 0) {
			return BlockPos.ZERO;
		}
		return new BlockPos((int) (x / n), (int) (y / n), (int) (z / n));
	}

	private static List<String> encodeParts(CompoundTag tag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(tag, bytes);
		String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		int overhead = CHAT_PREFIX.length() + 8 + 1 + 3 + 1 + 3 + 1;
		int chunkSize = Math.max(32, SharedConstants.MAX_CHAT_LENGTH - overhead - 4);
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < encoded.length(); i += chunkSize) {
			parts.add(encoded.substring(i, Math.min(encoded.length(), i + chunkSize)));
		}
		if (parts.isEmpty()) {
			parts.add("");
		}
		return parts;
	}

	private static CompoundTag decodeParts(List<String> parts) throws IOException {
		StringBuilder joined = new StringBuilder();
		for (String part : parts) {
			joined.append(part);
		}
		byte[] compressed = Base64.getUrlDecoder().decode(joined.toString());
		return NbtIo.readCompressed(new ByteArrayInputStream(compressed), NbtAccounter.unlimitedHeap());
	}

	private static Component buildShareMessage(String senderName, String imaginationName, BlockPos center, UUID shareId) {
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
			imaginationName,
			center.getX(),
			center.getY(),
			center.getZ()
		).withStyle(ChatFormatting.AQUA).append(" ").append(add);
	}
}
