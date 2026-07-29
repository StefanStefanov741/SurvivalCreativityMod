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

import org.jetbrains.annotations.Nullable;

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
	/** Every block touched while editing (SP + MP) — used to revert on disconnect. */
	private final Set<BlockPos> sessionDirty = new HashSet<>();
	/**
	 * Kept after session clear so {@link #ensureWorldRevertedBeforeSave} can strip
	 * imagination blocks on the server thread right before disk write.
	 */
	private boolean quitGuardActive;
	private WorldSnapshot quitGuardSnapshot;
	private Set<BlockPos> quitGuardDirty = Set.of();
	private UUID quitGuardPlayer;
	private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> quitGuardDimension;
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

	/** Active edit snapshot, or null when not editing. */
	public @Nullable WorldSnapshot sessionSnapshot() {
		return sessionSnapshot;
	}

	/** Multiplayer local-build session (live world keeps syncing; edits are an overlay). */
	public boolean isRemoteEditing() {
		return isEditing() && remoteSession;
	}

	/** AFK body the server still tracks while you build locally. */
	public Vec3 bodyPosition() {
		return bodyPosition;
	}

	/**
	 * Server sent a teleport correction. We must ACK it (or the connection desyncs — worse
	 * with other players online, and chat/signing can break) without yanking the local
	 * imagination camera/player away from where you're building.
	 */
	public void acknowledgeServerTeleport(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null || bodyPosition == null) {
			return;
		}
		var current = new net.minecraft.world.entity.PositionMoveRotation(
			bodyPosition, Vec3.ZERO, bodyYRot, bodyXRot);
		var absolute = net.minecraft.world.entity.PositionMoveRotation.calculateAbsolute(
			current, packet.change(), packet.relatives());
		bodyPosition = absolute.position();
		bodyYRot = absolute.yRot();
		bodyXRot = absolute.xRot();

		client.getConnection().send(
			new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(packet.id()));

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
			client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.exit_edit_first"));
			return;
		}
		if (mode == ImaginationMode.PREVIEWING) {
			clearPreview(client, false);
		}

		remoteSession = client.getSingleplayerServer() == null;
		remoteOverlay.clear();
		sessionDirty.clear();
		suppressingRemoteDirty = false;
		working = session;
		previousGameType = client.gameMode.getPlayerMode();
		snapshotInventory(client.player);
		snapshotBody(client.player);

		if (!remoteSession) {
			MinecraftServer server = client.getSingleplayerServer();
			UUID uuid = client.player.getUUID();
			Vec3 center = client.player.position();
			WorldSnapshot[] holder = new WorldSnapshot[1];
			if (server != null) {
				server.executeBlocking(() -> {
					var serverPlayer = server.getPlayerList().getPlayer(uuid);
					if (serverPlayer != null) {
						holder[0] = WorldSnapshot.capture(
							serverPlayer.level(),
							center,
							WorldSnapshot.DEFAULT_RADIUS,
							uuid
						);
					}
				});
			}
			sessionSnapshot = holder[0];
			if (sessionSnapshot == null) {
				client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.save_failed"));
				working = null;
				inventorySnapshot = new ItemStack[0];
				bodyPosition = null;
				return;
			}
		} else {
			sessionSnapshot = WorldSnapshot.capture(
				level,
				client.player.position(),
				WorldSnapshot.DEFAULT_RADIUS,
				client.player.getUUID()
			);
		}

		if (!remoteSession) {
			ImaginationRecovery.write(
				client,
				client.player.getUUID(),
				previousGameType,
				copyInventorySnapshot(),
				selectedSlotSnapshot,
				bodyPosition,
				bodyYRot,
				bodyXRot
			);
		}

		client.gui.setScreen(null);
		setCreativeMode(client, true);

		mode = ImaginationMode.EDITING;
		if (existing && !session.isEmpty()) {
			applyImaginationToWorld(client, session);
			if (remoteSession) {
				seedRemoteDirtyFromImagination(client, session);
			}
		}

		ImaginationWorldControls.INSTANCE.beginSession(client, remoteSession);
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
		if (!remoteSession) {
			MinecraftServer server = client.getSingleplayerServer();
			if (server == null || client.player == null) {
				return null;
			}
			UUID uuid = client.player.getUUID();
			String name = working.name();
			UUID id = working.id();
			long createdAt = working.createdAt();
			Imagination[] holder = new Imagination[1];
			server.executeBlocking(() -> {
				var serverPlayer = server.getPlayerList().getPlayer(uuid);
				if (serverPlayer != null) {
					holder[0] = sessionSnapshot.createDiff(serverPlayer.level(), name, id, createdAt);
				}
			});
			return holder[0];
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
				sessionSnapshot.restoreRemoteClient(clientLevel, remoteDirtyPositions());
			} finally {
				suppressingRemoteDirty = false;
				remoteOverlay.clear();
				sessionDirty.clear();
			}
			if (client.player != null && bodyPosition != null) {
				client.player.snapTo(bodyPosition.x, bodyPosition.y, bodyPosition.z, bodyYRot, bodyXRot);
				client.player.setDeltaMovement(Vec3.ZERO);
				client.player.refreshChatAbilities();
			}
		} else if (sessionSnapshot != null) {
			sessionSnapshot.restore(client);
			restoreBody(client);
		}

		restoreInventory(client.player);
		setCreativeMode(client, false);
		ImaginationWorldControls.INSTANCE.endSession(client);
		ImaginationRecovery.clear(client);
		ImaginationRecovery.clearPendingWorldRevert(client);
		clearSessionState();

		if (client.player != null) {
			if (saved) {
				client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.load_hint"));
			} else {
				client.player.sendOverlayMessage(Component.translatable("message.survivalcreativitymod.edit_exit"));
			}
		}
	}

	public void prepareForWorldSave(Minecraft client) {
		if (mode != ImaginationMode.EDITING || remoteSession) {
			return;
		}

		GameType restoreMode = previousGameType != null ? previousGameType : GameType.SURVIVAL;
		ItemStack[] invCopy = copyInventorySnapshot();
		int selected = selectedSlotSnapshot;
		Vec3 body = bodyPosition;
		float yRot = bodyYRot;
		float xRot = bodyXRot;
		WorldSnapshot snapshot = sessionSnapshot;
		Set<BlockPos> dirty = Set.copyOf(sessionDirty);
		MinecraftServer server = client.getSingleplayerServer();
		UUID uuid = client.player != null ? client.player.getUUID() : null;

		if (snapshot != null) {
			quitGuardActive = true;
			quitGuardSnapshot = snapshot;
			quitGuardDirty = dirty;
			quitGuardPlayer = uuid;
			quitGuardDimension = client.level != null ? client.level.dimension() : null;
		}

		autoSaveDisconnected(client);

		ImaginationWorldControls.INSTANCE.endSession(client);

		if (uuid != null) {
			ImaginationRecovery.write(
				client,
				uuid,
				restoreMode,
				invCopy,
				selected,
				body,
				yRot,
				xRot
			);
		}

		if (server != null && snapshot != null) {
			Runnable restoreWorldAndPlayer = () -> {
				ensureWorldRevertedBeforeSave(server);
				var serverPlayer = uuid != null ? server.getPlayerList().getPlayer(uuid) : null;
				if (serverPlayer == null) {
					SurvivalCreativityMod.LOGGER.warn(
						"prepareForWorldSave: ServerPlayer missing — quit-guard + pending revert kept");
					return;
				}
				serverPlayer.setGameMode(restoreMode);
				if (invCopy.length > 0) {
					Inventory inventory = serverPlayer.getInventory();
					int size = Math.min(invCopy.length, inventory.getContainerSize());
					for (int i = 0; i < size; i++) {
						inventory.setItem(i, invCopy[i].copy());
					}
					inventory.setSelectedSlot(selected);
				}
				if (body != null) {
					serverPlayer.teleportTo(body.x, body.y, body.z);
					serverPlayer.setYRot(yRot);
					serverPlayer.setXRot(xRot);
					serverPlayer.setDeltaMovement(Vec3.ZERO);
				}
				server.getPlayerList().saveAll();
			};
			try {
				if (server.isSameThread()) {
					restoreWorldAndPlayer.run();
				} else {
					server.executeBlocking(restoreWorldAndPlayer);
				}
			} catch (Exception e) {
				SurvivalCreativityMod.LOGGER.warn(
					"prepareForWorldSave immediate restore failed — relying on save mixin / join revert", e);
			}
		}

		if (client.player != null) {
			if (body != null) {
				client.player.snapTo(body.x, body.y, body.z, yRot, xRot);
				client.player.setDeltaMovement(Vec3.ZERO);
			}
			if (invCopy.length > 0) {
				Inventory inventory = client.player.getInventory();
				int size = Math.min(invCopy.length, inventory.getContainerSize());
				for (int i = 0; i < size; i++) {
					inventory.setItem(i, invCopy[i].copy());
				}
				inventory.setSelectedSlot(selected);
			}
			var abilities = client.player.getAbilities();
			restoreMode.updatePlayerAbilities(abilities);
			abilities.flying = false;
		}
		if (client.gameMode != null) {
			client.gameMode.setLocalMode(restoreMode);
		}

		clearSessionState();
		preview = null;
		ghostEntities = List.of();
		suppressedPreview.clear();
	}

	public void ensureWorldRevertedBeforeSave(MinecraftServer server) {
		if (!quitGuardActive || quitGuardSnapshot == null || server == null) {
			return;
		}
		suppressingRemoteDirty = true;
		try {
			for (var level : server.getAllLevels()) {
				if (quitGuardDimension != null && !level.dimension().equals(quitGuardDimension)) {
					continue;
				}
				if (!quitGuardDirty.isEmpty()) {
					quitGuardSnapshot.restorePositions(level, quitGuardDirty);
				}
				quitGuardSnapshot.revertDifferences(level, quitGuardPlayer);
			}
			SurvivalCreativityMod.LOGGER.info(
				"Quit-guard reverted imagination blocks before world save ({} dirty tracked)",
				quitGuardDirty.size());
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Quit-guard world revert failed", e);
		} finally {
			suppressingRemoteDirty = false;
		}
	}

	private void clearQuitGuard() {
		quitGuardActive = false;
		quitGuardSnapshot = null;
		quitGuardDirty = Set.of();
		quitGuardPlayer = null;
		quitGuardDimension = null;
	}

	private void autoSaveDisconnected(Minecraft client) {
		if (sessionSnapshot == null && quitGuardSnapshot == null) {
			return;
		}
		WorldSnapshot snapshot = sessionSnapshot != null ? sessionSnapshot : quitGuardSnapshot;
		Set<BlockPos> dirty = !sessionDirty.isEmpty()
			? Set.copyOf(sessionDirty)
			: quitGuardDirty;
		if (working == null || client.level == null || snapshot == null) {
			return;
		}
		try {
			Imagination diff;
			if (dirty != null && !dirty.isEmpty()) {
				diff = snapshot.createDiffFromPositions(
					client.level,
					dirty,
					working.name(),
					UUID.randomUUID(),
					System.currentTimeMillis()
				);
				Imagination full = snapshot.createDiff(
					client.level,
					working.name(),
					diff.id(),
					diff.createdAt()
				);
				if (full != null && !full.isEmpty()) {
					diff = full;
				}
			} else {
				diff = snapshot.createDiff(
					client.level,
					working.name(),
					UUID.randomUUID(),
					System.currentTimeMillis()
				);
			}
			if (diff == null || diff.isEmpty()) {
				return;
			}
			String stamp = java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			diff.setName("Disconnected " + stamp);
			diff = new Imagination(
				UUID.randomUUID(),
				diff.name(),
				System.currentTimeMillis(),
				diff.changes(),
				diff.entityChanges()
			);
			ImaginationStorage.save(client, diff);
			ImaginationRecovery.writePendingWorldRevert(client, diff);
			SurvivalCreativityMod.LOGGER.info("Auto-saved interrupted imagination as \"{}\"", diff.name());
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.error("Failed to auto-save disconnected imagination", e);
		}
	}

	public void applyPendingRecovery(Minecraft client) {
		if (client.player == null) {
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null) {
			ImaginationRecovery.clear(client);
			ImaginationRecovery.clearPendingWorldRevert(client);
			clearQuitGuard();
			return;
		}

		boolean hadPlayerCheckpoint = ImaginationRecovery.exists(client);
		boolean hadWorldRevert = ImaginationRecovery.hasPendingWorldRevert(client);
		if (!hadPlayerCheckpoint && !hadWorldRevert && !quitGuardActive) {
			return;
		}

		UUID uuid = client.player.getUUID();
		boolean[] ok = {false};
		try {
			server.executeBlocking(() -> {
				var serverPlayer = server.getPlayerList().getPlayer(uuid);
				if (serverPlayer == null) {
					return;
				}
				if (hadPlayerCheckpoint) {
					ok[0] = ImaginationRecovery.applyTo(serverPlayer, client);
				}
				if (quitGuardActive && quitGuardSnapshot != null) {
					ensureWorldRevertedBeforeSave(server);
					ok[0] = true;
				}
				if (hadWorldRevert) {
					if (ImaginationRecovery.applyPendingWorldRevert(serverPlayer.level(), client)) {
						ok[0] = true;
						ImaginationRecovery.clearPendingWorldRevert(client);
					}
				} else {
					ImaginationRecovery.clearPendingWorldRevert(client);
				}
				server.saveEverything(false, true, true);
			});
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to apply recovery on server join", e);
		}
		if (hadPlayerCheckpoint) {
			ImaginationRecovery.applyTo(client.player, client);
			ImaginationRecovery.clear(client);
		}
		clearQuitGuard();
		if (ok[0] || hadPlayerCheckpoint || hadWorldRevert) {
			client.player.sendOverlayMessage(
				Component.translatable("message.survivalcreativitymod.recovery_restored"));
		}
	}

	public void abortSessionForDisconnect(Minecraft client) {
		if (mode != ImaginationMode.EDITING) {
			return;
		}
		if (remoteSession) {
			autoSaveDisconnected(client);
			ImaginationWorldControls.INSTANCE.endSession(client);
			if (sessionSnapshot != null && client.level instanceof ClientLevel clientLevel) {
				suppressingRemoteDirty = true;
				try {
					sessionSnapshot.restoreRemoteClient(clientLevel, remoteDirtyPositions());
				} finally {
					suppressingRemoteDirty = false;
					remoteOverlay.clear();
					sessionDirty.clear();
				}
			}
			if (client.player != null && bodyPosition != null) {
				client.player.snapTo(bodyPosition.x, bodyPosition.y, bodyPosition.z, bodyYRot, bodyXRot);
				client.player.setDeltaMovement(Vec3.ZERO);
			}
			restoreInventory(client.player);
			if (client.gameMode != null && previousGameType != null) {
				client.gameMode.setLocalMode(previousGameType);
			}
			clearSessionState();
			preview = null;
			ghostEntities = List.of();
			suppressedPreview.clear();
			return;
		}
		prepareForWorldSave(client);
	}

	public void onDisconnect() {
		abortSessionForDisconnect(Minecraft.getInstance());
	}

	/** After disconnect saves finish — drop in-memory quit-guard (pending file remains for join). */
	public void onDisconnectFinished() {
		clearQuitGuard();
	}

	private Set<BlockPos> remoteDirtyPositions() {
		Set<BlockPos> positions = new HashSet<>(sessionDirty);
		positions.addAll(remoteOverlay.keySet());
		return positions;
	}

	private ItemStack[] copyInventorySnapshot() {
		ItemStack[] copy = new ItemStack[inventorySnapshot.length];
		for (int i = 0; i < inventorySnapshot.length; i++) {
			copy[i] = inventorySnapshot[i] == null ? ItemStack.EMPTY : inventorySnapshot[i].copy();
		}
		return copy;
	}

	private void clearSessionState() {
		working = null;
		sessionSnapshot = null;
		remoteSession = false;
		remoteOverlay.clear();
		sessionDirty.clear();
		suppressingRemoteDirty = false;
		sendingIdleHeartbeat = false;
		inventorySnapshot = new ItemStack[0];
		bodyPosition = null;
		mode = ImaginationMode.IDLE;
	}

	public void onSessionBlockChanged(BlockPos pos, BlockState state) {
		if (!isEditing() || suppressingRemoteDirty || sessionSnapshot == null) {
			return;
		}
		if (!sessionSnapshot.contains(pos)) {
			return;
		}
		BlockState original = sessionSnapshot.getBlock(pos);
		BlockPos key = pos.immutable();
		if (original != null && original.equals(state)) {
			sessionDirty.remove(key);
			remoteOverlay.remove(key);
		} else {
			sessionDirty.add(key);
			if (remoteSession) {
				remoteOverlay.put(key, state);
			}
		}
	}

	@Deprecated
	public void onRemoteClientBlockChanged(BlockPos pos, BlockState state) {
		onSessionBlockChanged(pos, state);
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
		HologramSettings.applySliceFromImagination(imagination);
		seedSatisfiedPreview(client.level);
		rebuildGhostEntities(client.level);
		mode = ImaginationMode.PREVIEWING;
		client.gui.setScreen(null);
		client.player.sendOverlayMessage(
			Component.translatable("message.survivalcreativitymod.preview_start", imagination.name()));
	}

	/** Hide hologram markers for work already done in the live world. */
	private void seedSatisfiedPreview(ClientLevel level) {
		if (preview == null || level == null) {
			return;
		}
		for (var entry : preview.changes().entrySet()) {
			BlockPos pos = entry.getKey();
			BlockChange change = entry.getValue();
			BlockState real = level.getBlockState(pos);
			if (change.placement()) {
				if (real.equals(change.imaginedState())) {
					suppressedPreview.add(pos.immutable());
				}
			} else if (real.isAir()) {
				suppressedPreview.add(pos.immutable());
			}
		}
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

	private void setCreativeMode(Minecraft client, boolean creative) {
		GameType target = creative ? GameType.CREATIVE : (previousGameType != null ? previousGameType : GameType.SURVIVAL);
		if (client.gameMode != null) {
			client.gameMode.setLocalMode(target);
		}
		if (!remoteSession) {
			MinecraftServer server = client.getSingleplayerServer();
			if (server != null && client.player != null) {
				UUID uuid = client.player.getUUID();
				Runnable apply = () -> {
					var serverPlayer = server.getPlayerList().getPlayer(uuid);
					if (serverPlayer != null) {
						serverPlayer.setGameMode(target);
					}
				};
				if (server.isSameThread()) {
					apply.run();
				} else {
					server.executeBlocking(apply);
				}
			}
		}
		if (client.player != null) {
			if (creative) {
				applyLocalCreativeAbilities(client.player, true);
			} else {
				var abilities = client.player.getAbilities();
				target.updatePlayerAbilities(abilities);
				abilities.flying = false;
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

	private void seedRemoteDirtyFromImagination(Minecraft client, Imagination imagination) {
		if (client.level == null || sessionSnapshot == null) {
			return;
		}
		for (BlockPos pos : imagination.changes().keySet()) {
			if (!sessionSnapshot.contains(pos)) {
				continue;
			}
			BlockPos key = pos.immutable();
			sessionDirty.add(key);
			remoteOverlay.put(key, client.level.getBlockState(key));
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
		float yRot = bodyYRot;
		float xRot = bodyXRot;
		player.snapTo(x, y, z, yRot, xRot);
		player.setDeltaMovement(Vec3.ZERO);

		MinecraftServer server = client.getSingleplayerServer();
		if (server != null) {
			UUID uuid = player.getUUID();
			Runnable teleport = () -> {
				var serverPlayer = server.getPlayerList().getPlayer(uuid);
				if (serverPlayer != null) {
					serverPlayer.teleportTo(x, y, z);
					serverPlayer.setYRot(yRot);
					serverPlayer.setXRot(xRot);
					serverPlayer.setDeltaMovement(Vec3.ZERO);
				}
			};
			if (server.isSameThread()) {
				teleport.run();
			} else {
				server.executeBlocking(teleport);
			}
		}
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
		ItemStack[] copy = copyInventorySnapshot();
		int selected = selectedSlotSnapshot;

		Inventory inventory = player.getInventory();
		int size = Math.min(copy.length, inventory.getContainerSize());
		for (int i = 0; i < size; i++) {
			inventory.setItem(i, copy[i].copy());
		}
		inventory.setSelectedSlot(selected);

		if (!remoteSession) {
			MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
			if (server != null) {
				UUID uuid = player.getUUID();
				Runnable apply = () -> {
					var serverPlayer = server.getPlayerList().getPlayer(uuid);
					if (serverPlayer == null) {
						return;
					}
					Inventory serverInv = serverPlayer.getInventory();
					int serverSize = Math.min(copy.length, serverInv.getContainerSize());
					for (int i = 0; i < serverSize; i++) {
						serverInv.setItem(i, copy[i].copy());
					}
					serverInv.setSelectedSlot(selected);
				};
				if (server.isSameThread()) {
					apply.run();
				} else {
					server.executeBlocking(apply);
				}
			}
		}

		inventorySnapshot = new ItemStack[0];
	}
}
