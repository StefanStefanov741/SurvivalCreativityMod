package ortero.survivalcreativity.com.client.imagination;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/**
 * Imagination-only world controls (time, weather, cleanup, fire).
 * Singleplayer changes are snapshotted and restored on exit; multiplayer uses client overlays.
 */
public final class ImaginationWorldControls {
	public static final ImaginationWorldControls INSTANCE = new ImaginationWorldControls();

	private static final int OVERWORLD_PERIOD = 24_000;
	private static final int DAY_TICKS = 1_000;
	private static final int NIGHT_TICKS = 13_000;
	private static final int WEATHER_DURATION = 6000;

	private @Nullable ClockState singleplayerClockSnapshot;
	private @Nullable WeatherSnapshot singleplayerWeatherSnapshot;
	private @Nullable Integer singleplayerFireSpreadSnapshot;

	private boolean remoteOverrideActive;
	private long remoteTotalTicks;
	private boolean remoteFrozen;
	private long remoteLastGameTime = -1L;

	private float savedRainLevel;
	private float savedThunderLevel;
	private boolean weatherOverrideActive;
	private boolean fireSpreadDisabled;

	/** Real-world entities hidden for this imagination session only (never discarded). */
	private final Set<UUID> suppressedEntities = new HashSet<>();

	private ImaginationWorldControls() {
	}

	/**
	 * True when an entity was cleared via imagination controls and should be
	 * invisible / non-interactive until the session ends.
	 */
	public boolean isEntitySuppressed(@Nullable Entity entity) {
		return entity != null
			&& ImaginationManager.INSTANCE.isEditing()
			&& suppressedEntities.contains(entity.getUUID());
	}

	public void beginSession(Minecraft client, boolean remoteSession) {
		endSession(client);
		if (client.level == null) {
			return;
		}
		savedRainLevel = client.level.getRainLevel(1.0F);
		savedThunderLevel = client.level.getThunderLevel(1.0F);
		fireSpreadDisabled = false;

		Holder<WorldClock> clock = overworldClock(client);
		if (remoteSession) {
			remoteOverrideActive = true;
			remoteTotalTicks = client.level.getOverworldClockTime();
			remoteFrozen = false;
			remoteLastGameTime = client.level.getGameTime();
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null || clock == null) {
			return;
		}
		try {
			server.executeBlocking(() -> {
				ClockState state = server.clockManager().packState().clocks().get(clock);
				if (state != null) {
					singleplayerClockSnapshot = state;
				}
				WeatherData weather = server.getWeatherData();
				singleplayerWeatherSnapshot = new WeatherSnapshot(
					weather.getClearWeatherTime(),
					weather.getRainTime(),
					weather.getThunderTime(),
					weather.isRaining(),
					weather.isThundering(),
					client.level.getRainLevel(1.0F),
					client.level.getThunderLevel(1.0F)
				);
				singleplayerFireSpreadSnapshot = server.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
			});
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to snapshot imagination world controls", e);
		}
	}

	public void endSession(Minecraft client) {
		if (client == null) {
			reset();
			return;
		}
		Holder<WorldClock> clock = overworldClock(client);
		MinecraftServer server = client.getSingleplayerServer();
		if (server != null) {
			try {
				server.executeBlocking(() -> {
					if (clock != null && singleplayerClockSnapshot != null) {
						restoreClock(server, clock, singleplayerClockSnapshot);
					}
					if (singleplayerWeatherSnapshot != null) {
						restoreWeather(server, singleplayerWeatherSnapshot);
					}
					if (singleplayerFireSpreadSnapshot != null) {
						server.getGameRules().set(
							GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,
							singleplayerFireSpreadSnapshot,
							server
						);
					}
				});
			} catch (Exception e) {
				SurvivalCreativityMod.LOGGER.warn("Failed to restore imagination world controls", e);
			}
		}
		if (client.level != null && (weatherOverrideActive || remoteOverrideActive)) {
			client.level.setRainLevel(savedRainLevel);
			client.level.setThunderLevel(savedThunderLevel);
		}
		invalidateSky(client);
		reset();
	}

	private void reset() {
		singleplayerClockSnapshot = null;
		singleplayerWeatherSnapshot = null;
		singleplayerFireSpreadSnapshot = null;
		remoteOverrideActive = false;
		remoteFrozen = false;
		remoteLastGameTime = -1L;
		weatherOverrideActive = false;
		fireSpreadDisabled = false;
		suppressedEntities.clear();
	}

	// —— Time ——

	public void setDay(Minecraft client) {
		applyTime(client, DAY_TICKS, "message.survivalcreativitymod.time_day");
	}

	public void setNight(Minecraft client) {
		applyTime(client, NIGHT_TICKS, "message.survivalcreativitymod.time_night");
	}

	public void toggleFreeze(Minecraft client) {
		if (ImaginationManager.INSTANCE.isRemoteEditing()) {
			remoteFrozen = !remoteFrozen;
			invalidateSky(client);
			notify(client, remoteFrozen
				? "message.survivalcreativitymod.time_frozen"
				: "message.survivalcreativitymod.time_unfrozen");
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		Holder<WorldClock> clock = overworldClock(client);
		if (server == null || clock == null) {
			return;
		}
		boolean[] frozen = {false};
		try {
			server.executeBlocking(() -> {
				var manager = server.clockManager();
				boolean next = !isClockPaused(manager, clock);
				manager.setPaused(clock, next);
				frozen[0] = next;
			});
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to toggle imagination time freeze", e);
			return;
		}
		invalidateSky(client);
		notify(client, frozen[0]
			? "message.survivalcreativitymod.time_frozen"
			: "message.survivalcreativitymod.time_unfrozen");
	}

	// —— Weather ——

	public void setClearWeather(Minecraft client) {
		applyWeather(client, false, false, "message.survivalcreativitymod.weather_clear");
	}

	public void setRain(Minecraft client) {
		applyWeather(client, true, false, "message.survivalcreativitymod.weather_rain");
	}

	public void setThunder(Minecraft client) {
		applyWeather(client, true, true, "message.survivalcreativitymod.weather_thunder");
	}

	// —— Cleanup ——

	public void removeFriendlyMobs(Minecraft client) {
		int count = removeEntities(client, entity ->
			entity instanceof Mob mob && mob.getType().getCategory().isFriendly());
		notifyCount(client, "message.survivalcreativitymod.removed_friendly", count);
	}

	public void removeHostileMobs(Minecraft client) {
		int count = removeEntities(client, entity -> {
			if (!(entity instanceof Mob mob)) {
				return false;
			}
			MobCategory category = mob.getType().getCategory();
			return category == MobCategory.MONSTER || !category.isFriendly();
		});
		notifyCount(client, "message.survivalcreativitymod.removed_hostile", count);
	}

	public void removeDroppedItems(Minecraft client) {
		int count = removeEntities(client, entity -> entity instanceof ItemEntity);
		notifyCount(client, "message.survivalcreativitymod.removed_items", count);
	}

	public void extinguishFires(Minecraft client) {
		int count = extinguishFiresInSession(client);
		notifyCount(client, "message.survivalcreativitymod.extinguished_fires", count);
	}

	// —— Fire ——

	public void toggleFireSpread(Minecraft client) {
		if (ImaginationManager.INSTANCE.isRemoteEditing()) {
			fireSpreadDisabled = !fireSpreadDisabled;
			notify(client, fireSpreadDisabled
				? "message.survivalcreativitymod.fire_spread_off"
				: "message.survivalcreativitymod.fire_spread_on");
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		boolean[] off = {false};
		try {
			server.executeBlocking(() -> {
				int current = server.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
				int next = current == 0 ? (singleplayerFireSpreadSnapshot != null ? singleplayerFireSpreadSnapshot : 128) : 0;
				server.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, next, server);
				fireSpreadDisabled = next == 0;
				off[0] = fireSpreadDisabled;
			});
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to toggle fire spread", e);
			return;
		}
		notify(client, off[0]
			? "message.survivalcreativitymod.fire_spread_off"
			: "message.survivalcreativitymod.fire_spread_on");
	}

	public boolean isFireSpreadDisabled() {
		return fireSpreadDisabled && ImaginationManager.INSTANCE.isEditing();
	}

	public boolean isRemoteOverrideActive() {
		return remoteOverrideActive && ImaginationManager.INSTANCE.isRemoteEditing();
	}

	public long getRemoteTotalTicks() {
		return remoteTotalTicks;
	}

	/** @return true if default tick handling should be skipped */
	public boolean tickRemoteOverride(long gameTime) {
		if (!isRemoteOverrideActive()) {
			remoteLastGameTime = gameTime;
			return false;
		}
		if (remoteLastGameTime < 0L) {
			remoteLastGameTime = gameTime;
			return true;
		}
		long delta = gameTime - remoteLastGameTime;
		remoteLastGameTime = gameTime;
		if (!remoteFrozen && delta > 0L) {
			remoteTotalTicks += delta;
			invalidateSky(Minecraft.getInstance());
		}
		return true;
	}

	private void applyTime(Minecraft client, int markerTicks, String messageKey) {
		if (ImaginationManager.INSTANCE.isRemoteEditing()) {
			remoteTotalTicks = advanceToMarker(remoteTotalTicks, markerTicks);
			invalidateSky(client);
			notify(client, messageKey);
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		Holder<WorldClock> clock = overworldClock(client);
		if (server == null || clock == null) {
			return;
		}
		ResourceKey<ClockTimeMarker> marker = markerTicks == DAY_TICKS
			? ClockTimeMarkers.DAY
			: ClockTimeMarkers.NIGHT;
		try {
			server.executeBlocking(() -> server.clockManager().moveToTimeMarker(clock, marker));
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to set imagination world time", e);
			return;
		}
		invalidateSky(client);
		notify(client, messageKey);
	}

	private void applyWeather(Minecraft client, boolean raining, boolean thundering, String messageKey) {
		weatherOverrideActive = true;
		if (ImaginationManager.INSTANCE.isRemoteEditing()) {
			if (client.level != null) {
				client.level.setRainLevel(raining ? 1.0F : 0.0F);
				client.level.setThunderLevel(thundering ? 1.0F : 0.0F);
			}
			notify(client, messageKey);
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		try {
			server.executeBlocking(() -> {
				WeatherData weather = server.getWeatherData();
				weather.setRaining(raining);
				weather.setThundering(thundering);
				if (raining) {
					weather.setRainTime(WEATHER_DURATION);
					weather.setClearWeatherTime(0);
					weather.setThunderTime(thundering ? WEATHER_DURATION : WEATHER_DURATION * 2);
				} else {
					weather.setClearWeatherTime(WEATHER_DURATION);
					weather.setRainTime(0);
					weather.setThunderTime(0);
				}
				float rain = raining ? 1.0F : 0.0F;
				float thunder = thundering ? 1.0F : 0.0F;
				for (ServerLevel level : server.getAllLevels()) {
					level.setRainLevel(rain);
					level.setThunderLevel(thunder);
				}
				var players = server.getPlayerList();
				if (raining) {
					players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
				} else {
					players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
				}
				players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
				players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
			});
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to set imagination weather", e);
			return;
		}
		notify(client, messageKey);
	}

	/**
	 * Imagination-only cleanup: hide real entities for this session, discard only
	 * client-spawned imaginary ones (negative network ids). Never touches the server world.
	 */
	private int removeEntities(Minecraft client, java.util.function.Predicate<Entity> filter) {
		AABB bounds = sessionBounds(client);
		if (bounds == null || !(client.level instanceof ClientLevel level)) {
			return 0;
		}
		List<Entity> imaginaryToDiscard = new ArrayList<>();
		int count = 0;
		for (Entity entity : level.getEntities(null, bounds)) {
			if (entity instanceof Player || ImaginedEntities.isTracked(entity)) {
				continue;
			}
			if (!filter.test(entity)) {
				continue;
			}
			// Client-only imagination entities (spawn eggs, etc.)
			if (entity.getId() < 0) {
				imaginaryToDiscard.add(entity);
				count++;
				continue;
			}
			if (suppressedEntities.add(entity.getUUID())) {
				count++;
			}
		}
		for (Entity entity : imaginaryToDiscard) {
			entity.discard();
		}
		return count;
	}

	private int extinguishFiresInSession(Minecraft client) {
		AABB bounds = sessionBounds(client);
		WorldSnapshot snapshot = ImaginationManager.INSTANCE.sessionSnapshot();
		if (bounds == null || snapshot == null) {
			return 0;
		}
		if (ImaginationManager.INSTANCE.isRemoteEditing()) {
			if (!(client.level instanceof ClientLevel level)) {
				return 0;
			}
			return extinguishInLevel(level, snapshot);
		}
		MinecraftServer server = client.getSingleplayerServer();
		UUIDHolder uuid = new UUIDHolder(client.player != null ? client.player.getUUID() : null);
		int[] count = {0};
		if (server == null) {
			return 0;
		}
		try {
			server.executeBlocking(() -> {
				var player = uuid.id != null ? server.getPlayerList().getPlayer(uuid.id) : null;
				if (player == null) {
					return;
				}
				count[0] = extinguishInLevel(player.level(), snapshot);
			});
		} catch (Exception e) {
			SurvivalCreativityMod.LOGGER.warn("Failed to extinguish imagination fires", e);
		}
		return count[0];
	}

	private static int extinguishInLevel(Level level, WorldSnapshot snapshot) {
		int count = 0;
		BlockPos origin = snapshot.origin();
		int radius = snapshot.radius();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
			for (int y = origin.getY() - radius; y <= origin.getY() + radius; y++) {
				for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
						level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
						count++;
					} else if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
						&& state.hasProperty(CampfireBlock.LIT)
						&& state.getValue(CampfireBlock.LIT)) {
						level.setBlock(cursor, state.setValue(CampfireBlock.LIT, false), 3);
						count++;
					}
				}
			}
		}
		return count;
	}

	private static @Nullable AABB sessionBounds(Minecraft client) {
		WorldSnapshot snapshot = ImaginationManager.INSTANCE.sessionSnapshot();
		if (snapshot != null) {
			return snapshot.bounds();
		}
		if (client.player == null) {
			return null;
		}
		BlockPos origin = BlockPos.containing(client.player.position());
		int r = WorldSnapshot.DEFAULT_RADIUS;
		return new AABB(
			origin.getX() - r, origin.getY() - r, origin.getZ() - r,
			origin.getX() + r + 1, origin.getY() + r + 1, origin.getZ() + r + 1
		);
	}

	private static void restoreClock(MinecraftServer server, Holder<WorldClock> clock, ClockState state) {
		var manager = server.clockManager();
		manager.setTotalTicks(clock, state.totalTicks());
		manager.setRate(clock, state.rate());
		manager.setPaused(clock, state.paused());
	}

	private static void restoreWeather(MinecraftServer server, WeatherSnapshot snapshot) {
		WeatherData weather = server.getWeatherData();
		weather.setClearWeatherTime(snapshot.clearTime());
		weather.setRainTime(snapshot.rainTime());
		weather.setThunderTime(snapshot.thunderTime());
		weather.setRaining(snapshot.raining());
		weather.setThundering(snapshot.thundering());
		for (ServerLevel level : server.getAllLevels()) {
			level.setRainLevel(snapshot.rainLevel());
			level.setThunderLevel(snapshot.thunderLevel());
		}
		var players = server.getPlayerList();
		if (snapshot.raining()) {
			players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
		} else {
			players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
		}
		players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, snapshot.rainLevel()));
		players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, snapshot.thunderLevel()));
	}

	private static boolean isClockPaused(net.minecraft.world.clock.ServerClockManager manager, Holder<WorldClock> clock) {
		ClockState state = manager.packState().clocks().get(clock);
		return state != null && state.paused();
	}

	private static long advanceToMarker(long totalTicks, int markerTicks) {
		long phase = totalTicks % OVERWORLD_PERIOD;
		long duration = markerTicks - phase;
		if (duration <= 0L) {
			duration += OVERWORLD_PERIOD;
		}
		return totalTicks + duration;
	}

	private static @Nullable Holder<WorldClock> overworldClock(Minecraft client) {
		if (client.level == null) {
			return null;
		}
		return client.level.registryAccess()
			.lookupOrThrow(Registries.WORLD_CLOCK)
			.get(WorldClocks.OVERWORLD)
			.orElse(null);
	}

	private static void invalidateSky(Minecraft client) {
		if (client.level instanceof ClientLevel level) {
			level.environmentAttributes().invalidateTickCache();
		}
	}

	private static void notify(Minecraft client, String key) {
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.translatable(key));
		}
	}

	private static void notifyCount(Minecraft client, String key, int count) {
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.translatable(key, count));
		}
	}

	private record WeatherSnapshot(
		int clearTime,
		int rainTime,
		int thunderTime,
		boolean raining,
		boolean thundering,
		float rainLevel,
		float thunderLevel
	) {
	}

	private static final class UUIDHolder {
		final java.util.UUID id;

		UUIDHolder(java.util.UUID id) {
			this.id = id;
		}
	}
}
