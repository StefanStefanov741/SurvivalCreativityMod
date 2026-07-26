package ortero.survivalcreativity.com.client.imagination;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.gui.ExitImaginationScreen;
import ortero.survivalcreativity.com.client.gui.ImaginationListScreen;
import ortero.survivalcreativity.com.client.gui.SaveImaginationScreen;

/**
 * Imagination sessions:
 * <ul>
 *   <li><b>Singleplayer</b> — real Creative on the integrated server; snapshot restores the world.</li>
 *   <li><b>Multiplayer</b> — stay in the live world (AFK heartbeat on the server), build only
 *       locally, and revert those local edits on exit so the real world stays in sync.</li>
 * </ul>
 */
public final class ImaginationManager {
	public static final ImaginationManager INSTANCE = new ImaginationManager();

	private static final AtomicInteger GHOST_ENTITY_IDS = new AtomicInteger(-2_000_000);

	private ImaginationMode mode = ImaginationMode.IDLE;
	private Imagination working;
	private Imagination preview;
	private final Set<BlockPos> suppressedPreview = new HashSet<>();
	private List<GhostEntity> ghostEntities = List.of();
	private WorldSnapshot sessionSnapshot;
	/** True when editing on a remote server (no integrated server authority). */
	private boolean remoteSession;
	/** Local block edits during remote sessions — reapplied when the server syncs chunks. */
	private final Map<BlockPos, BlockState> remoteOverlay = new HashMap<>();
	private boolean suppressingRemoteDirty;
	private boolean sendingIdleHeartbeat;
	private GameType previousGameType = GameType.SURVIVAL;
	private ItemStack[] inventorySnapshot = new ItemStack[0];
	private int selectedSlotSnapshot;
	private Vec3 bodyPosition;
	private float bodyYRot;
	private float bodyXRot;

	private ImaginationManager() {
	}

	/** Cached hologram entities — built once when preview starts (not every frame). */
	public record GhostEntity(Entity entity, boolean placement) {
	}

	public List<GhostEntity> ghostEntities() {
		return ghostEntities;
	}

	public ImaginationMode mode() {
		return mode;
	}

	public boolean isEditing() {
		return mode == ImaginationMode.EDITING;
	}

	/** Multiplayer local-build session (live world keeps syncing; edits are an overlay). */
	public boolean isRemoteEditing() {
		return isEditing() && remoteSession;
	}

	public boolean isSendingIdleHeartbeat() {
		return sendingIdleHeartbeat;
	}

	public boolean isPreviewing() {
		return mode == ImaginationMode.PREVIEWING;
	}

	public Imagination preview() {
		return preview;
	}

	public Imagination working() {
		return working;
	}

	public boolean isSuppressed(BlockPos pos) {
		return suppressedPreview.contains(pos);
	}

	public void toggleEdit(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (mode == ImaginationMode.EDITING) {
			requestExitEdit(client);
		} else {
			enterEdit(client);
		}
	}

	/**
	 * Leave edit mode. If there are unsaved changes, ask Save / Discard / Keep editing.
	 */
	public void requestExitEdit(Minecraft client) {
		if (!isEditing() || client.player == null) {
			return;
		}
		Imagination diff = buildDiff(client);
		if (diff == null || diff.isEmpty()) {
			exitEdit(client, false);
			return;
		}
		client.gui.setScreen(new ExitImaginationScreen(
			() -> requestSave(client),
			() -> exitEdit(client, false)
		));
	}

	public void enterEdit(Minecraft client) {
		enterEdit(client, Imagination.create("Untitled Imagination"), false);
	}

	public void enterEdit(Minecraft client, Imagination source) {
		if (source == null) {
			return;
		}
		enterEdit(client, source.copyForEdit(), true);
	}

	private void enterEdit(Minecraft client, Imagination session, boolean existing) {
		if (client.player == null || client.gameMode == null || !(client.level instanceof ClientLevel level)) {
			return;
		}
		if (mode == ImaginationMode.EDITING) {
			// Switching imaginations — discard current session after confirm would be complex;
			// require a clean exit first.
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.exit_edit_first"));
			return;
		}
		if (mode == ImaginationMode.PREVIEWING) {
			clearPreview(client, false);
		}

		remoteSession = client.getSingleplayerServer() == null;
		remoteOverlay.clear();
		suppressingRemoteDirty = false;
		working = session;
		previousGameType = client.gameMode.getPlayerMode();
		snapshotInventory(client.player);
		snapshotBody(client.player);
		sessionSnapshot = WorldSnapshot.capture(
			level,
			client.player.position(),
			WorldSnapshot.DEFAULT_RADIUS,
			client.player.getUUID()
		);

		client.gui.setScreen(null);
		setCreativeMode(client, true);

		if (existing && !session.isEmpty()) {
			applyImaginationToWorld(client, session);
		}

		mode = ImaginationMode.EDITING;
		if (existing) {
			client.player.sendOverlayMessage(
				Component.translatable(
					remoteSession
						? "message.survivalcreativitymod.edit_existing_remote"
						: "message.survivalcreativitymod.edit_existing",
					session.name()));
		} else {
			client.player.sendOverlayMessage(Component.translatable(
				remoteSession
					? "message.survivalcreativitymod.edit_enter_remote"
					: "message.survivalcreativitymod.edit_enter"));
		}
	}

	public void requestSave(Minecraft client) {
		if (!isEditing() || working == null || client.player == null || sessionSnapshot == null) {
			return;
		}
		Imagination diff = buildDiff(client);
		if (diff == null || diff.isEmpty()) {
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.empty"));
			return;
		}
		working = diff;
		client.gui.setScreen(new SaveImaginationScreen(working.name(), this::confirmSave));
	}

	private void confirmSave(String name) {
		Minecraft client = Minecraft.getInstance();
		if (!isEditing() || working == null || client.player == null) {
			return;
		}
		// Rebuild diff at confirm time so late edits are included
		Imagination diff = buildDiff(client);
		if (diff == null) {
			return;
		}
		diff.setName(name == null || name.isBlank() ? "Untitled Imagination" : name.trim());
		working = diff;
		try {
			ImaginationStorage.save(client, working);
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.saved", working.name()));
			exitEdit(client, true);
		} catch (IOException e) {
			SurvivalCreativityMod.LOGGER.error("Failed to save imagination", e);
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.save_failed"));
		}
	}

	private Imagination buildDiff(Minecraft client) {
		if (sessionSnapshot == null || working == null || client.level == null) {
			return null;
		}
		return sessionSnapshot.createDiff(
			client.level,
			working.name(),
			working.id(),
			working.createdAt()
		);
	}

	public void exitEdit(Minecraft client, boolean saved) {
		if (mode != ImaginationMode.EDITING) {
			return;
		}

		client.gui.setScreen(null);

		if (remoteSession && sessionSnapshot != null && client.level instanceof ClientLevel clientLevel) {
			suppressingRemoteDirty = true;
			try {
				sessionSnapshot.restoreRemoteClient(clientLevel, Set.copyOf(remoteOverlay.keySet()));
			} finally {
				suppressingRemoteDirty = false;
				remoteOverlay.clear();
			}
			// Match the AFK body the server still has, so we don't desync / clip on exit
			if (client.player != null && bodyPosition != null) {
				client.player.snapTo(bodyPosition.x, bodyPosition.y, bodyPosition.z, bodyYRot, bodyXRot);
				client.player.setDeltaMovement(Vec3.ZERO);
			}
		} else if (sessionSnapshot != null) {
			sessionSnapshot.restore(client);
			restoreBody(client);
		}

		restoreInventory(client.player);
		bodyPosition = null;
		setCreativeMode(client, false);

		working = null;
		sessionSnapshot = null;
		remoteSession = false;
		remoteOverlay.clear();
		mode = ImaginationMode.IDLE;

		if (client.player != null) {
			if (saved) {
				client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.load_hint"));
			} else {
				client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.edit_exit"));
			}
		}
	}

	public void onRemoteClientBlockChanged(BlockPos pos, BlockState state) {
		if (!remoteSession || !isEditing() || suppressingRemoteDirty || sessionSnapshot == null) {
			return;
		}
		if (!sessionSnapshot.contains(pos)) {
			return;
		}
		BlockState original = sessionSnapshot.getBlock(pos);
		BlockPos key = pos.immutable();
		if (original != null && original.equals(state)) {
			remoteOverlay.remove(key);
		} else {
			remoteOverlay.put(key, state);
		}
	}

	public void reapplyRemoteOverlay(BlockPos pos) {
		if (!isRemoteEditing() || remoteOverlay.isEmpty()) {
			return;
		}
		BlockState overlay = remoteOverlay.get(pos);
		if (overlay == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!(client.level instanceof ClientLevel level)) {
			return;
		}
		suppressingRemoteDirty = true;
		try {
			level.setBlock(pos, overlay, 3);
		} finally {
			suppressingRemoteDirty = false;
		}
	}

	public void reapplyRemoteOverlayAll() {
		if (!isRemoteEditing() || remoteOverlay.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!(client.level instanceof ClientLevel level)) {
			return;
		}
		suppressingRemoteDirty = true;
		try {
			for (Map.Entry<BlockPos, BlockState> entry : remoteOverlay.entrySet()) {
				level.setBlock(entry.getKey(), entry.getValue(), 3);
			}
		} finally {
			suppressingRemoteDirty = false;
		}
	}

	public void openMenu(Minecraft client) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (mode == ImaginationMode.EDITING) {
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.exit_edit_first"));
			return;
		}
		List<Imagination> list = ImaginationStorage.loadAll(client, client.level);
		client.gui.setScreen(new ImaginationListScreen(list));
	}

	public void startPreview(Minecraft client, Imagination imagination) {
		if (client.player == null) {
			return;
		}
		if (mode == ImaginationMode.EDITING) {
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.exit_edit_first"));
			return;
		}
		preview = imagination;
		suppressedPreview.clear();
		rebuildGhostEntities(client.level);
		mode = ImaginationMode.PREVIEWING;
		client.gui.setScreen(null);
		client.player.sendOverlayMessage(
			Component.translatable("message.survivalcreativitymod.preview_start", imagination.name()));
	}

	public void clearPreview(Minecraft client, boolean notify) {
		if (mode != ImaginationMode.PREVIEWING) {
			return;
		}
		preview = null;
		suppressedPreview.clear();
		ghostEntities = List.of();
		mode = ImaginationMode.IDLE;
		if (notify && client.player != null) {
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.preview_hide"));
		}
	}

	private void rebuildGhostEntities(ClientLevel level) {
		if (preview == null || level == null) {
			ghostEntities = List.of();
			return;
		}
		List<GhostEntity> built = new ArrayList<>();
		for (EntityChange change : preview.entityChanges().values()) {
			if (!ImaginedEntities.isTrackedData(change.data(), level)) {
				continue;
			}
			Entity entity = EntityNbt.load(level, change.data());
			if (entity == null) {
				continue;
			}
			entity.setId(GHOST_ENTITY_IDS.getAndDecrement());
			EntityNbt.freezeForHologram(entity);
			built.add(new GhostEntity(entity, change.placement()));
		}
		ghostEntities = Collections.unmodifiableList(built);
	}

	public void onRealBlockPlaced(BlockPos pos) {
		if (!isPreviewing() || preview == null) {
			return;
		}
		BlockChange change = preview.get(pos);
		if (change != null && change.placement()) {
			suppressedPreview.add(pos.immutable());
		}
	}

	public void onRealBlockBroken(BlockPos pos, BlockState brokenState) {
		if (!isPreviewing() || preview == null) {
			return;
		}
		BlockChange change = preview.get(pos);
		if (change == null) {
			return;
		}
		if (change.placement()) {
			suppressedPreview.remove(pos);
		} else {
			suppressedPreview.add(pos.immutable());
		}
	}

	public void tick(Minecraft client) {
		if (!isEditing() || client.gameMode == null || client.player == null) {
			return;
		}
		if (client.gameMode.getPlayerMode() != GameType.CREATIVE) {
			client.gameMode.setLocalMode(GameType.CREATIVE);
		}
		if (remoteSession) {
			applyLocalCreativeAbilities(client.player, true);
			keepServerBodyIdle(client);
		}
	}

	/** Stay AFK on the server at the enter position while moving freely locally. */
	private void keepServerBodyIdle(Minecraft client) {
		if (bodyPosition == null || client.getConnection() == null) {
			return;
		}
		sendingIdleHeartbeat = true;
		try {
			client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
				bodyPosition.x,
				bodyPosition.y,
				bodyPosition.z,
				bodyYRot,
				bodyXRot,
				true,
				false
			));
		} finally {
			sendingIdleHeartbeat = false;
		}
	}

	public void onDisconnect() {
		working = null;
		preview = null;
		ghostEntities = List.of();
		sessionSnapshot = null;
		remoteSession = false;
		remoteOverlay.clear();
		suppressingRemoteDirty = false;
		sendingIdleHeartbeat = false;
		suppressedPreview.clear();
		inventorySnapshot = new ItemStack[0];
		bodyPosition = null;
		mode = ImaginationMode.IDLE;
	}

	private void setCreativeMode(Minecraft client, boolean creative) {
		GameType target = creative ? GameType.CREATIVE : previousGameType;
		if (client.gameMode != null) {
			client.gameMode.setLocalMode(target);
		}
		if (!remoteSession) {
			MinecraftServer server = client.getSingleplayerServer();
			if (server != null && client.player != null) {
				UUID uuid = client.player.getUUID();
				server.execute(() -> {
					var serverPlayer = server.getPlayerList().getPlayer(uuid);
					if (serverPlayer != null) {
						serverPlayer.setGameMode(target);
					}
				});
			}
		}
		if (client.player != null) {
			if (creative) {
				applyLocalCreativeAbilities(client.player, true);
			} else {
				var abilities = client.player.getAbilities();
				previousGameType.updatePlayerAbilities(abilities);
				abilities.flying = false;
				// Avoid syncing fake abilities to a remote server
				if (!remoteSession) {
					client.player.onUpdateAbilities();
				}
			}
		}
	}

	private static void applyLocalCreativeAbilities(LocalPlayer player, boolean creative) {
		var abilities = player.getAbilities();
		if (creative) {
			abilities.mayfly = true;
			abilities.instabuild = true;
			abilities.invulnerable = true;
			abilities.mayBuild = true;
		}
	}

	private void applyImaginationToWorld(Minecraft client, Imagination imagination) {
		if (!remoteSession) {
			MinecraftServer server = client.getSingleplayerServer();
			if (server == null || client.player == null) {
				return;
			}
			UUID uuid = client.player.getUUID();
			server.execute(() -> {
				var serverPlayer = server.getPlayerList().getPlayer(uuid);
				if (serverPlayer == null) {
					return;
				}
				WorldSnapshot.applyImagination(serverPlayer.level(), imagination);
			});
			return;
		}
		if (client.level != null) {
			WorldSnapshot.applyImagination(client.level, imagination);
		}
	}

	private void snapshotBody(LocalPlayer player) {
		bodyPosition = player.position();
		bodyYRot = player.getYRot();
		bodyXRot = player.getXRot();
	}

	private void restoreBody(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || bodyPosition == null) {
			return;
		}
		double x = bodyPosition.x;
		double y = bodyPosition.y;
		double z = bodyPosition.z;
		player.snapTo(x, y, z, bodyYRot, bodyXRot);
		player.setDeltaMovement(Vec3.ZERO);

		MinecraftServer server = client.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> {
				var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
				if (serverPlayer != null) {
					serverPlayer.teleportTo(x, y, z);
					serverPlayer.setYRot(bodyYRot);
					serverPlayer.setXRot(bodyXRot);
					serverPlayer.setDeltaMovement(Vec3.ZERO);
				}
			});
		}
		bodyPosition = null;
	}

	private void snapshotInventory(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		inventorySnapshot = new ItemStack[inventory.getContainerSize()];
		for (int i = 0; i < inventorySnapshot.length; i++) {
			inventorySnapshot[i] = inventory.getItem(i).copy();
		}
		selectedSlotSnapshot = inventory.getSelectedSlot();
	}

	private void restoreInventory(LocalPlayer player) {
		if (player == null || inventorySnapshot.length == 0) {
			return;
		}
		Inventory inventory = player.getInventory();
		int size = Math.min(inventorySnapshot.length, inventory.getContainerSize());
		for (int i = 0; i < size; i++) {
			inventory.setItem(i, inventorySnapshot[i].copy());
		}
		inventory.setSelectedSlot(selectedSlotSnapshot);
		inventorySnapshot = new ItemStack[0];
	}
}
