package ortero.survivalcreativity.com.fabric;

import net.fabricmc.api.ModInitializer;
import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.fabric.network.ModNetworkingFabric;

public class SurvivalCreativityModFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ModNetworkingFabric.register();
		SurvivalCreativityMod.LOGGER.info("SurvivalCreativityMod (Fabric) loaded");
	}
}
