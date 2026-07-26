package ortero.survivalcreativity.com.client.imagination;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * ClientLevel entities must have a non-zero network ID before {@link ClientLevel#addEntity}.
 * Vanilla only assigns IDs on the server / from spawn packets, so imagination entities use
 * a private negative ID range that won't collide with server entities.
 */
public final class ClientEntitySpawner {
	private static final AtomicInteger NEXT_ID = new AtomicInteger(-1000000);

	private ClientEntitySpawner() {
	}

	public static void add(ClientLevel level, Entity entity) {
		entity.setId(NEXT_ID.getAndDecrement());
		level.addEntity(entity);
	}
}
